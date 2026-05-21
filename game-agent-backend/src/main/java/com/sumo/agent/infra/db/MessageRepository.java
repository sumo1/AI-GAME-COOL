package com.sumo.agent.infra.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * messages 表数据访问。
 * 仅暴露契约要求的两个方法（insert / listBySession）；不写"未来可能用"。
 */
@Repository
public class MessageRepository {

    private static final String INSERT_SQL =
            "INSERT INTO messages(id, session_id, role, content, iterations, eval_score, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String LIST_BY_SESSION_SQL =
            "SELECT id, session_id, role, content, iterations, eval_score, created_at " +
                    "FROM messages WHERE session_id = ? ORDER BY created_at ASC";

    private static final RowMapper<MessageEntity> ROW_MAPPER = (rs, rowNum) -> {
        MessageEntity e = new MessageEntity();
        e.setId(rs.getString("id"));
        e.setSessionId(rs.getString("session_id"));
        e.setRole(rs.getString("role"));
        e.setContent(rs.getString("content"));
        int iter = rs.getInt("iterations");
        e.setIterations(rs.wasNull() ? null : iter);
        int score = rs.getInt("eval_score");
        e.setEvalScore(rs.wasNull() ? null : score);
        e.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
        return e;
    };

    private final JdbcTemplate jdbc;

    public MessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 插入；若 entity.id / createdAt 为空，会回填到 entity 上（**有副作用**）。 */
    public synchronized String insert(MessageEntity entity) {
        if (entity.getId() == null || entity.getId().isBlank()) {
            entity.setId(UUID.randomUUID().toString());
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }

        jdbc.update(con -> {
            var ps = con.prepareStatement(INSERT_SQL);
            ps.setString(1, entity.getId());
            ps.setString(2, entity.getSessionId());
            ps.setString(3, entity.getRole());
            ps.setString(4, entity.getContent());
            if (entity.getIterations() == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, entity.getIterations());
            if (entity.getEvalScore() == null) ps.setNull(6, Types.INTEGER);
            else ps.setInt(6, entity.getEvalScore());
            ps.setLong(7, entity.getCreatedAt().toEpochMilli());
            return ps;
        });
        return entity.getId();
    }

    public List<MessageEntity> listBySession(String sessionId) {
        return jdbc.query(LIST_BY_SESSION_SQL, ROW_MAPPER, sessionId);
    }
}

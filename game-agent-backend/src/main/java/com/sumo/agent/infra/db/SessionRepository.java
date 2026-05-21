package com.sumo.agent.infra.db;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * sessions 表数据访问。
 *
 * 写操作 synchronized：HikariCP 单连接已隔离，Java 层再加一道锁是为清晰且兜底。
 * 不暴露 JdbcTemplate；所有 SQL 在本类内写死字符串常量。
 */
@Repository
public class SessionRepository {

    private static final String INSERT_SQL =
            "INSERT INTO sessions(id, title, model_key, created_at, updated_at, message_count, game_count) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String FIND_BY_ID_SQL =
            "SELECT id, title, model_key, created_at, updated_at, message_count, game_count " +
                    "FROM sessions WHERE id = ?";

    private static final String LIST_RECENT_SQL =
            "SELECT id, title, model_key, created_at, updated_at, message_count, game_count " +
                    "FROM sessions ORDER BY updated_at DESC LIMIT ?";

    private static final String TOUCH_SQL =
            "UPDATE sessions SET updated_at = ? WHERE id = ?";

    private static final String INCREMENT_COUNTERS_SQL =
            "UPDATE sessions SET message_count = message_count + ?, game_count = game_count + ? WHERE id = ?";

    private static final String DELETE_BY_ID_SQL =
            "DELETE FROM sessions WHERE id = ?";

    private static final RowMapper<SessionEntity> ROW_MAPPER = (rs, rowNum) -> {
        SessionEntity e = new SessionEntity();
        e.setId(rs.getString("id"));
        e.setTitle(rs.getString("title"));
        e.setModelKey(rs.getString("model_key"));
        e.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
        e.setUpdatedAt(Instant.ofEpochMilli(rs.getLong("updated_at")));
        e.setMessageCount(rs.getInt("message_count"));
        e.setGameCount(rs.getInt("game_count"));
        return e;
    };

    private final JdbcTemplate jdbc;

    public SessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 插入；若 entity.id / createdAt / updatedAt 为空，会回填到 entity 上（**有副作用**）。 */
    public synchronized String insert(SessionEntity entity) {
        if (entity.getId() == null || entity.getId().isBlank()) {
            entity.setId(UUID.randomUUID().toString());
        }
        Instant now = Instant.now();
        if (entity.getCreatedAt() == null) entity.setCreatedAt(now);
        if (entity.getUpdatedAt() == null) entity.setUpdatedAt(now);

        jdbc.update(INSERT_SQL,
                entity.getId(),
                entity.getTitle(),
                entity.getModelKey(),
                entity.getCreatedAt().toEpochMilli(),
                entity.getUpdatedAt().toEpochMilli(),
                entity.getMessageCount(),
                entity.getGameCount()
        );
        return entity.getId();
    }

    public Optional<SessionEntity> findById(String id) {
        try {
            return Optional.of(jdbc.queryForObject(FIND_BY_ID_SQL, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<SessionEntity> listRecent(int limit) {
        return jdbc.query(LIST_RECENT_SQL, ROW_MAPPER, limit);
    }

    public synchronized void touch(String id, long updatedAtMs) {
        jdbc.update(TOUCH_SQL, updatedAtMs, id);
    }

    public synchronized void incrementCounters(String id, int messageDelta, int gameDelta) {
        jdbc.update(INCREMENT_COUNTERS_SQL, messageDelta, gameDelta, id);
    }

    public synchronized int deleteById(String id) {
        return jdbc.update(DELETE_BY_ID_SQL, id);
    }
}

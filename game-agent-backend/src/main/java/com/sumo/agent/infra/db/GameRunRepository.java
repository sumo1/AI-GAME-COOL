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
 * game_runs 表数据访问。
 *
 * 列表查询（listBySession / listRecent / listFavorites）**有意不返回 html 字段**
 * （SELECT 中不含 html，RowMapper 不读 html）——避免一次拉一堆 50KB+ 的大字段。
 * 详情请用 findHtmlById（专门只查 id+html）。
 */
@Repository
public class GameRunRepository {

    /** 含全部字段（含 html）。用于 findById 详情 / insert 后查回。 */
    private static final String FULL_COLUMNS =
            "id, session_id, message_id, title, html, eval_score, iterations, favorited, created_at";

    /** 不含 html 的列表列。用于 listBySession / listRecent / listFavorites。 */
    private static final String LIST_COLUMNS =
            "id, session_id, message_id, title, eval_score, iterations, favorited, created_at";

    private static final String INSERT_SQL =
            "INSERT INTO game_runs(id, session_id, message_id, title, html, eval_score, iterations, favorited, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String FIND_BY_ID_SQL =
            "SELECT " + FULL_COLUMNS + " FROM game_runs WHERE id = ?";

    private static final String LIST_BY_SESSION_SQL =
            "SELECT " + LIST_COLUMNS + " FROM game_runs WHERE session_id = ? ORDER BY created_at DESC";

    private static final String LIST_RECENT_SQL =
            "SELECT " + LIST_COLUMNS + " FROM game_runs ORDER BY created_at DESC LIMIT ?";

    private static final String LIST_FAVORITES_SQL =
            "SELECT " + LIST_COLUMNS + " FROM game_runs WHERE favorited = 1 " +
                    "ORDER BY eval_score DESC, created_at DESC LIMIT ?";

    private static final String FIND_HTML_BY_ID_SQL =
            "SELECT id, html FROM game_runs WHERE id = ?";

    private static final String SET_FAVORITED_SQL =
            "UPDATE game_runs SET favorited = ? WHERE id = ?";

    /** 完整 RowMapper（读所有字段，含 html）。 */
    private static final RowMapper<GameRunEntity> FULL_ROW_MAPPER = (rs, rowNum) -> {
        GameRunEntity e = new GameRunEntity();
        e.setId(rs.getString("id"));
        e.setSessionId(rs.getString("session_id"));
        e.setMessageId(rs.getString("message_id"));
        e.setTitle(rs.getString("title"));
        e.setHtml(rs.getString("html"));
        e.setEvalScore(rs.getInt("eval_score"));
        e.setIterations(rs.getInt("iterations"));
        e.setFavorited(rs.getInt("favorited") == 1);
        e.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
        return e;
    };

    /** 列表 RowMapper（不读 html，html 字段保持 null）。 */
    private static final RowMapper<GameRunEntity> LIST_ROW_MAPPER = (rs, rowNum) -> {
        GameRunEntity e = new GameRunEntity();
        e.setId(rs.getString("id"));
        e.setSessionId(rs.getString("session_id"));
        e.setMessageId(rs.getString("message_id"));
        e.setTitle(rs.getString("title"));
        // html 不读 —— 保持 null
        e.setEvalScore(rs.getInt("eval_score"));
        e.setIterations(rs.getInt("iterations"));
        e.setFavorited(rs.getInt("favorited") == 1);
        e.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
        return e;
    };

    /** 仅含 id+html 的 RowMapper（findHtmlById 用）。 */
    private static final RowMapper<GameRunEntity> HTML_ONLY_ROW_MAPPER = (rs, rowNum) -> {
        GameRunEntity e = new GameRunEntity();
        e.setId(rs.getString("id"));
        e.setHtml(rs.getString("html"));
        return e;
    };

    private final JdbcTemplate jdbc;

    public GameRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 插入；若 entity.id / createdAt 为空，会回填到 entity 上（**有副作用**）。 */
    public synchronized String insert(GameRunEntity entity) {
        if (entity.getId() == null || entity.getId().isBlank()) {
            entity.setId(UUID.randomUUID().toString());
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }

        jdbc.update(INSERT_SQL,
                entity.getId(),
                entity.getSessionId(),
                entity.getMessageId(),
                entity.getTitle(),
                entity.getHtml(),
                entity.getEvalScore(),
                entity.getIterations(),
                entity.isFavorited() ? 1 : 0,
                entity.getCreatedAt().toEpochMilli()
        );
        return entity.getId();
    }

    public Optional<GameRunEntity> findById(String id) {
        try {
            return Optional.of(jdbc.queryForObject(FIND_BY_ID_SQL, FULL_ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<GameRunEntity> listBySession(String sessionId) {
        return jdbc.query(LIST_BY_SESSION_SQL, LIST_ROW_MAPPER, sessionId);
    }

    public List<GameRunEntity> listRecent(int limit) {
        return jdbc.query(LIST_RECENT_SQL, LIST_ROW_MAPPER, limit);
    }

    public List<GameRunEntity> listFavorites(int limit) {
        return jdbc.query(LIST_FAVORITES_SQL, LIST_ROW_MAPPER, limit);
    }

    public Optional<GameRunEntity> findHtmlById(String id) {
        try {
            return Optional.of(jdbc.queryForObject(FIND_HTML_BY_ID_SQL, HTML_ONLY_ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public synchronized int setFavorited(String id, boolean favorited) {
        return jdbc.update(SET_FAVORITED_SQL, favorited ? 1 : 0, id);
    }
}

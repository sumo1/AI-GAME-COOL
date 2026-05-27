package com.sumo.agent.infra.db;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * game_run_evaluations 表数据访问。
 *
 * 列表查询（listBySession / listBySkill / listFailures）**不返回 *_json 大字段**
 * （SELECT 中不含，RowMapper 不读）——避免一次拉一堆几十 KB 的 trace JSON。
 * 详情请用 findById（FULL_ROW_MAPPER 读全字段）。
 *
 * 沿用 GameRunRepository 的字段分离 + synchronized 写方法风格。
 */
@Repository
public class GameRunEvaluationRepository {

    /** 完整列（含 *_json）：用于 findById 详情 / insert 后查回。 */
    private static final String FULL_COLUMNS =
            "id, session_id, game_run_id, skill_name, model_key, success, error_type, " +
                    "total_score, degraded, degraded_reason, iteration_count, final_iteration_summary, " +
                    "scores_json, probe_summary_json, classified_issues_json, iter_traces_json, created_at";

    /** 列表列（不含 *_json）：避免大字段污染列表查询。 */
    private static final String LIST_COLUMNS =
            "id, session_id, game_run_id, skill_name, model_key, success, error_type, " +
                    "total_score, degraded, degraded_reason, iteration_count, final_iteration_summary, " +
                    "created_at";

    private static final String INSERT_SQL =
            "INSERT INTO game_run_evaluations(" + FULL_COLUMNS + ") " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String FIND_BY_ID_SQL =
            "SELECT " + FULL_COLUMNS + " FROM game_run_evaluations WHERE id = ?";

    private static final String LIST_BY_SESSION_SQL =
            "SELECT " + LIST_COLUMNS + " FROM game_run_evaluations WHERE session_id = ? ORDER BY created_at DESC";

    private static final String LIST_BY_SKILL_SQL =
            "SELECT " + LIST_COLUMNS + " FROM game_run_evaluations WHERE skill_name = ? " +
                    "ORDER BY created_at DESC LIMIT ?";

    private static final String LIST_FAILURES_SQL =
            "SELECT " + LIST_COLUMNS + " FROM game_run_evaluations WHERE success = 0 " +
                    "ORDER BY created_at DESC LIMIT ?";

    /** 完整 RowMapper（读所有字段，含 *_json）。 */
    private static final RowMapper<GameRunEvaluationEntity> FULL_ROW_MAPPER = (rs, rowNum) -> {
        GameRunEvaluationEntity e = new GameRunEvaluationEntity();
        e.setId(rs.getString("id"));
        e.setSessionId(rs.getString("session_id"));
        e.setGameRunId(rs.getString("game_run_id"));
        e.setSkillName(rs.getString("skill_name"));
        e.setModelKey(rs.getString("model_key"));
        e.setSuccess(rs.getInt("success"));
        e.setErrorType(rs.getString("error_type"));
        e.setTotalScore(rs.getInt("total_score"));
        e.setDegraded(rs.getInt("degraded"));
        e.setDegradedReason(rs.getString("degraded_reason"));
        e.setIterationCount(rs.getInt("iteration_count"));
        e.setFinalIterationSummary(rs.getString("final_iteration_summary"));
        e.setScoresJson(rs.getString("scores_json"));
        e.setProbeSummaryJson(rs.getString("probe_summary_json"));
        e.setClassifiedIssuesJson(rs.getString("classified_issues_json"));
        e.setIterTracesJson(rs.getString("iter_traces_json"));
        e.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
        return e;
    };

    /** 列表 RowMapper（不读 *_json，4 个 _json 字段保持 null）。 */
    private static final RowMapper<GameRunEvaluationEntity> LIST_ROW_MAPPER = (rs, rowNum) -> {
        GameRunEvaluationEntity e = new GameRunEvaluationEntity();
        e.setId(rs.getString("id"));
        e.setSessionId(rs.getString("session_id"));
        e.setGameRunId(rs.getString("game_run_id"));
        e.setSkillName(rs.getString("skill_name"));
        e.setModelKey(rs.getString("model_key"));
        e.setSuccess(rs.getInt("success"));
        e.setErrorType(rs.getString("error_type"));
        e.setTotalScore(rs.getInt("total_score"));
        e.setDegraded(rs.getInt("degraded"));
        e.setDegradedReason(rs.getString("degraded_reason"));
        e.setIterationCount(rs.getInt("iteration_count"));
        e.setFinalIterationSummary(rs.getString("final_iteration_summary"));
        // *_json 字段不读 —— 保持 null
        e.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
        return e;
    };

    private final JdbcTemplate jdbc;

    public GameRunEvaluationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 插入；若 entity.id / createdAt 为空，会回填到 entity 上（**有副作用**）。 */
    public synchronized String insert(GameRunEvaluationEntity entity) {
        if (entity.getId() == null || entity.getId().isBlank()) {
            entity.setId(UUID.randomUUID().toString());
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }

        jdbc.update(INSERT_SQL,
                entity.getId(),
                entity.getSessionId(),
                entity.getGameRunId(),
                entity.getSkillName(),
                entity.getModelKey(),
                entity.getSuccess(),
                entity.getErrorType(),
                entity.getTotalScore(),
                entity.getDegraded(),
                entity.getDegradedReason(),
                entity.getIterationCount(),
                entity.getFinalIterationSummary(),
                entity.getScoresJson(),
                entity.getProbeSummaryJson(),
                entity.getClassifiedIssuesJson(),
                entity.getIterTracesJson(),
                entity.getCreatedAt().toEpochMilli()
        );
        return entity.getId();
    }

    public Optional<GameRunEvaluationEntity> findById(String id) {
        try {
            return Optional.of(jdbc.queryForObject(FIND_BY_ID_SQL, FULL_ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<GameRunEvaluationEntity> listBySession(String sessionId) {
        return jdbc.query(LIST_BY_SESSION_SQL, LIST_ROW_MAPPER, sessionId);
    }

    public List<GameRunEvaluationEntity> listBySkill(String skillName, int limit) {
        return jdbc.query(LIST_BY_SKILL_SQL, LIST_ROW_MAPPER, skillName, limit);
    }

    public List<GameRunEvaluationEntity> listFailures(int limit) {
        return jdbc.query(LIST_FAILURES_SQL, LIST_ROW_MAPPER, limit);
    }

    // -----------------------------------------------------------------------
    // Step 5：候选样本查询增量
    // -----------------------------------------------------------------------

    private static final String LIST_BY_SKILL_SCORE_SQL =
            "SELECT " + LIST_COLUMNS + " FROM game_run_evaluations " +
                    "WHERE skill_name = ? AND total_score >= ? AND total_score <= ? " +
                    "ORDER BY total_score ASC, created_at DESC LIMIT ?";

    /** 按 skill + 分数区间筛候选（低分优先）。*_json 字段不读。 */
    public List<GameRunEvaluationEntity> listBySkillAndScore(String skillName, int minScore, int maxScore, int limit) {
        return jdbc.query(LIST_BY_SKILL_SCORE_SQL, LIST_ROW_MAPPER, skillName, minScore, maxScore, limit);
    }

    private static final String LIST_BY_ISSUE_CATEGORY_SQL =
            "SELECT " + LIST_COLUMNS + " FROM game_run_evaluations " +
                    "WHERE classified_issues_json LIKE ? " +
                    "ORDER BY created_at DESC LIMIT ?";

    /**
     * 用 SQL LIKE 在 classified_issues_json 上做关键词匹配。
     *
     * 性能不敏感（本地 SQLite < 万行），不做索引化。severity 给空时只过 category。
     */
    public List<GameRunEvaluationEntity> listByIssueCategory(String category, String severity, int limit) {
        String catPattern = "%\"category\":\"" + category + "\"%";
        if (severity != null && !severity.isBlank()) {
            String sql = "SELECT " + LIST_COLUMNS + " FROM game_run_evaluations " +
                    "WHERE classified_issues_json LIKE ? AND classified_issues_json LIKE ? " +
                    "ORDER BY created_at DESC LIMIT ?";
            String sevPattern = "%\"severity\":\"" + severity + "\"%";
            return jdbc.query(sql, LIST_ROW_MAPPER, catPattern, sevPattern, limit);
        }
        return jdbc.query(LIST_BY_ISSUE_CATEGORY_SQL, LIST_ROW_MAPPER, catPattern, limit);
    }

    /**
     * 按多维条件计数（全字段可空）。供 stats 端点用。
     *
     * onlyDegraded=true 过滤 degraded=1；onlySuccess 直接对 success 列做 0/1 映射。
     */
    public int countByConditions(String skillName, Integer minScore, Integer maxScore,
                                  Boolean onlyDegraded, Boolean onlySuccess) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM game_run_evaluations WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (skillName != null && !skillName.isBlank()) {
            sql.append(" AND skill_name = ?");
            args.add(skillName);
        }
        if (minScore != null) {
            sql.append(" AND total_score >= ?");
            args.add(minScore);
        }
        if (maxScore != null) {
            sql.append(" AND total_score <= ?");
            args.add(maxScore);
        }
        if (Boolean.TRUE.equals(onlyDegraded)) {
            sql.append(" AND degraded = 1");
        }
        if (onlySuccess != null) {
            sql.append(" AND success = ?");
            args.add(onlySuccess ? 1 : 0);
        }
        Integer n = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return n != null ? n : 0;
    }
}

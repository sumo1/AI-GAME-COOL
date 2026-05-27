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
 * skill_distillation_candidates 表数据访问。
 *
 * 字段较少且无大字段，列表与详情共用 RowMapper。
 */
@Repository
public class SkillDistillationCandidateRepository {

    private static final String COLUMNS =
            "id, evaluation_id, skill_name, status, note, created_at, updated_at";

    private static final String INSERT_SQL =
            "INSERT INTO skill_distillation_candidates(" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String FIND_BY_ID_SQL =
            "SELECT " + COLUMNS + " FROM skill_distillation_candidates WHERE id = ?";

    /** SQL 拼接基底；status 过滤可选。 */
    private static final String LIST_BY_SKILL_BASE_SQL =
            "SELECT " + COLUMNS + " FROM skill_distillation_candidates WHERE skill_name = ?";

    private static final String UPDATE_STATUS_SQL =
            "UPDATE skill_distillation_candidates SET status = ?, note = ?, updated_at = ? WHERE id = ?";

    private static final String DELETE_BY_ID_SQL =
            "DELETE FROM skill_distillation_candidates WHERE id = ?";

    private static final RowMapper<SkillDistillationCandidateEntity> ROW_MAPPER = (rs, rowNum) -> {
        SkillDistillationCandidateEntity e = new SkillDistillationCandidateEntity();
        e.setId(rs.getString("id"));
        e.setEvaluationId(rs.getString("evaluation_id"));
        e.setSkillName(rs.getString("skill_name"));
        e.setStatus(rs.getString("status"));
        e.setNote(rs.getString("note"));
        e.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
        e.setUpdatedAt(Instant.ofEpochMilli(rs.getLong("updated_at")));
        return e;
    };

    private final JdbcTemplate jdbc;

    public SkillDistillationCandidateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 插入；若 id / 时间戳 / status 为空，自动兜底（**有副作用**：回填到 entity）。 */
    public synchronized String insert(SkillDistillationCandidateEntity entity) {
        if (entity.getId() == null || entity.getId().isBlank()) {
            entity.setId(UUID.randomUUID().toString());
        }
        Instant now = Instant.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        if (entity.getUpdatedAt() == null) {
            entity.setUpdatedAt(now);
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("raw");
        }

        jdbc.update(INSERT_SQL,
                entity.getId(),
                entity.getEvaluationId(),
                entity.getSkillName(),
                entity.getStatus(),
                entity.getNote(),
                entity.getCreatedAt().toEpochMilli(),
                entity.getUpdatedAt().toEpochMilli()
        );
        return entity.getId();
    }

    public Optional<SkillDistillationCandidateEntity> findById(String id) {
        try {
            return Optional.of(jdbc.queryForObject(FIND_BY_ID_SQL, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    /**
     * 按 skill 名查蒸馏候选。
     *
     * @param skillName 必填
     * @param status    null / blank 时不过滤；否则按 status 精确匹配
     * @param limit     最大返回行数
     */
    public List<SkillDistillationCandidateEntity> listBySkill(String skillName, String status, int limit) {
        if (status == null || status.isBlank()) {
            String sql = LIST_BY_SKILL_BASE_SQL + " ORDER BY updated_at DESC LIMIT ?";
            return jdbc.query(sql, ROW_MAPPER, skillName, limit);
        }
        String sql = LIST_BY_SKILL_BASE_SQL + " AND status = ? ORDER BY updated_at DESC LIMIT ?";
        return jdbc.query(sql, ROW_MAPPER, skillName, status, limit);
    }

    /** 推进状态；返回受影响行数（0 = 不存在）。updated_at 自动刷新为当前时间。 */
    public synchronized int updateStatus(String id, String status, String note) {
        return jdbc.update(UPDATE_STATUS_SQL, status, note, Instant.now().toEpochMilli(), id);
    }

    public synchronized int deleteById(String id) {
        return jdbc.update(DELETE_BY_ID_SQL, id);
    }
}

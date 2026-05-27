package com.sumo.agent.infra.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumo.agent.infra.db.GameRunEvaluationEntity;
import com.sumo.agent.infra.db.GameRunEvaluationRepository;
import com.sumo.agent.infra.db.SkillDistillationCandidateEntity;
import com.sumo.agent.infra.db.SkillDistillationCandidateRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 蒸馏候选查询编排层（只读 + 状态机推进）。
 *
 * 列表端点用 LIST_ROW_MAPPER（不含 *_json），详情端点把 *_json 解析为对象再返回。
 *
 * 状态机推进通过 SkillDistillationCandidateRepository.upsertFromEvaluation 幂等实现：
 *   - promoteToCandidate：raw → candidate（首次自动 upsert 成 candidate）
 *   - accept / reject：candidate → accepted/rejected（已 accepted/rejected 的再次调用幂等刷新 updated_at）
 */
@Service
public class EvidenceQueryService {

    private final GameRunEvaluationRepository evaluations;
    private final SkillDistillationCandidateRepository candidates;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvidenceQueryService(GameRunEvaluationRepository evaluations,
                                 SkillDistillationCandidateRepository candidates) {
        this.evaluations = evaluations;
        this.candidates = candidates;
    }

    /**
     * 按 skill + 分数范围筛候选；skill 为空时退化为列出最近的失败样本（候选语义偏向问题样本）。
     */
    public List<Map<String, Object>> findCandidates(String skill, Integer minScore, Integer maxScore, int limit) {
        int min = minScore != null ? minScore : 0;
        int max = maxScore != null ? maxScore : 100;
        List<GameRunEvaluationEntity> rows;
        if (skill != null && !skill.isBlank()) {
            rows = evaluations.listBySkillAndScore(skill, min, max, limit);
        } else {
            rows = evaluations.listFailures(limit);
        }
        return rows.stream().map(this::toSummary).toList();
    }

    public Optional<Map<String, Object>> findDetail(String evaluationId) {
        return evaluations.findById(evaluationId).map(this::toDetail);
    }

    /** 总览：评估总数 / 失败 / 降级 / candidate 各状态计数。 */
    public Map<String, Object> stats() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalEvaluations", evaluations.countByConditions(null, null, null, null, null));
        r.put("totalFailures", evaluations.countByConditions(null, null, null, null, false));
        r.put("totalDegraded", evaluations.countByConditions(null, null, null, true, null));
        r.put("totalCandidates", candidates.listByStatus("candidate", 1000).size());
        r.put("totalAccepted", candidates.listByStatus("accepted", 1000).size());
        r.put("totalRejected", candidates.listByStatus("rejected", 1000).size());
        return r;
    }

    /**
     * raw → candidate（自动 upsert：若 evaluation 还没对应 candidate 行直接建为 candidate；若已存在直接迁状态）。
     *
     * @throws IllegalArgumentException 当 evaluationId 不存在
     */
    public synchronized String promoteToCandidate(String evaluationId, String note) {
        GameRunEvaluationEntity ev = evaluations.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("evaluation not found: " + evaluationId));
        return candidates.upsertFromEvaluation(evaluationId, ev.getSkillName(), "candidate", note);
    }

    /**
     * candidate → accepted。已 accepted/rejected 的再次调用幂等（updated_at 刷新）。
     *
     * @throws IllegalArgumentException 当 candidateId 不存在
     */
    public synchronized String accept(String candidateId, String note) {
        SkillDistillationCandidateEntity c = candidates.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("candidate not found: " + candidateId));
        candidates.updateStatus(c.getId(), "accepted", note);
        return c.getId();
    }

    /**
     * candidate → rejected。已 accepted/rejected 的再次调用幂等（updated_at 刷新）。
     *
     * @throws IllegalArgumentException 当 candidateId 不存在
     */
    public synchronized String reject(String candidateId, String note) {
        SkillDistillationCandidateEntity c = candidates.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("candidate not found: " + candidateId));
        candidates.updateStatus(c.getId(), "rejected", note);
        return c.getId();
    }

    /** 列表用的轻量 summary（不含 *_json）。 */
    private Map<String, Object> toSummary(GameRunEvaluationEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("sessionId", e.getSessionId());
        m.put("gameRunId", e.getGameRunId());
        m.put("skillName", e.getSkillName());
        m.put("modelKey", e.getModelKey());
        m.put("success", e.getSuccess() == 1);
        m.put("errorType", e.getErrorType());
        m.put("totalScore", e.getTotalScore());
        m.put("degraded", e.getDegraded() == 1);
        m.put("iterationCount", e.getIterationCount());
        m.put("finalIterationSummary", e.getFinalIterationSummary());
        m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return m;
    }

    /** 详情：把 *_json 解析为对象返回前端。 */
    private Map<String, Object> toDetail(GameRunEvaluationEntity e) {
        Map<String, Object> m = new LinkedHashMap<>(toSummary(e));
        m.put("scores", parseJson(e.getScoresJson(), Map.class));
        m.put("probeSummary", parseJson(e.getProbeSummaryJson(), Map.class));
        m.put("classifiedIssues", parseJson(e.getClassifiedIssuesJson(), List.class));
        m.put("iterTraces", parseJson(e.getIterTracesJson(), List.class));
        return m;
    }

    private Object parseJson(String json, Class<?> type) {
        if (json == null || json.isBlank()) {
            return type == List.class ? List.of() : new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return type == List.class ? List.of() : new HashMap<>();
        }
    }
}

package com.sumo.agent.infra.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumo.agent.agent.evaluation.EvaluationObservation;
import com.sumo.agent.agent.evaluation.ObservationIssue;
import com.sumo.agent.agent.loop.RunTrace;
import com.sumo.agent.agent.loop.TraceEntry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evidence 序列化器 — 把 EvaluationObservation / RunTrace 转成 game_run_evaluations 表的 JSON 列字符串。
 * <p>
 * 任务 260524 Step 4。设计要点：
 * <ul>
 *   <li>纯静态、无状态、不持有 Spring Bean。</li>
 *   <li>{@code null} 输入 → 返回 {@code "{}"} 或 {@code "[]"}，不抛 NPE。</li>
 *   <li>Jackson 序列化失败 → 记录日志 + 返回空对象/空数组（不影响主流程，evidence 写入降级为空）。</li>
 *   <li>RunTrace → JSON 时**剔除** {@link TraceEntry#getIssuesSnapshot()} 大字段，避免单行膨胀。</li>
 * </ul>
 */
@Slf4j
public final class EvidenceMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EvidenceMapper() {}

    /** 五维评分 → JSON object。null obs → "{}"。 */
    public static String toScoresJson(EvaluationObservation obs) {
        if (obs == null || obs.getScoresByDimension() == null) {
            return "{}";
        }
        return safeWrite(obs.getScoresByDimension(), "{}");
    }

    /** ProbeSummary → JSON object。null obs → "{}"。 */
    public static String toProbeSummaryJson(EvaluationObservation obs) {
        if (obs == null || obs.getProbeSummary() == null) {
            return "{}";
        }
        return safeWrite(obs.getProbeSummary(), "{}");
    }

    /** ObservationIssue 列表 → JSON array。null obs → "[]"。 */
    public static String toClassifiedIssuesJson(EvaluationObservation obs) {
        if (obs == null || obs.getIssues() == null) {
            return "[]";
        }
        List<Map<String, String>> simplified = new ArrayList<>();
        for (ObservationIssue issue : obs.getIssues()) {
            if (issue == null) {
                continue;
            }
            Map<String, String> m = new HashMap<>();
            m.put("category", issue.getCategory());
            m.put("severity", issue.getSeverity());
            m.put("message", issue.getMessage());
            if (issue.getEvidence() != null) {
                m.put("evidence", issue.getEvidence());
            }
            simplified.add(m);
        }
        return safeWrite(simplified, "[]");
    }

    /**
     * RunTrace → JSON array。**剔除 issuesSnapshot 大字段**。null trace → "[]"。
     */
    public static String toIterTracesJson(RunTrace trace) {
        if (trace == null || trace.getEntries() == null) {
            return "[]";
        }
        List<Map<String, Object>> simplified = new ArrayList<>();
        for (TraceEntry te : trace.getEntries()) {
            if (te == null) {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("iteration", te.getIteration());
            m.put("scoreBefore", te.getScoreBefore());
            m.put("scoreAfter", te.getScoreAfter());
            m.put("issueCount", te.getIssueCount());
            m.put("responseLength", te.getResponseLength());
            m.put("gameVersion", te.getGameVersion());
            m.put("summary", te.getSummary());
            m.put("evaluationDegraded", te.isEvaluationDegraded());
            // 不含 issuesSnapshot —— 大字段、低信号
            simplified.add(m);
        }
        return safeWrite(simplified, "[]");
    }

    private static String safeWrite(Object obj, String fallback) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("evidence JSON 序列化失败，返回 {}: {}", fallback, e.getMessage());
            return fallback;
        }
    }
}

package com.sumo.agent.agent.evaluation;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评估观察 — ProbeReport 的结构化压缩，喂回 WorkingMemory 让 LLM 在下一轮看到证据。
 * <p>
 * 设计目标：高信号、低 token、无完整 ProbeReport 注入。
 */
@Data
public class EvaluationObservation {

    private int totalScore;
    private Map<String, Integer> scoresByDimension = new HashMap<>();
    private List<ObservationIssue> issues = new ArrayList<>();
    private ProbeSummary probeSummary;
    private boolean degraded;
    private String degradedReason;

    /**
     * Probe 数据的高信号摘要 — 保留计数和状态转换，舍弃事件原文等大体积细节。
     */
    @Data
    public static class ProbeSummary {
        private boolean pageLoaded;
        private int jsErrorCount;
        private int eventCount;
        private int domMutationsCount;
        private int outOfBoundsCount;
        private List<String> stateTransitions = new ArrayList<>();
        /** finalState 可能为 null，故用包装类型 */
        private Integer finalScore;
    }

    /**
     * 把 ProbeReport 转成结构化观察。
     *
     * @param report 来自 GameEvaluator 的报告；为 null 时返回空 observation（不抛 NPE）
     */
    public static EvaluationObservation fromProbeReport(ProbeReport report) {
        EvaluationObservation obs = new EvaluationObservation();
        ProbeSummary summary = new ProbeSummary();
        obs.setProbeSummary(summary);

        if (report == null) {
            return obs;
        }

        obs.setTotalScore(report.getTotalScore());

        // 五维评分进 scoresByDimension（key 全英文，便于稳定渲染）
        Map<String, Integer> scores = obs.getScoresByDimension();
        scores.put("runnability", report.getRunnabilityScore());
        scores.put("layout", report.getLayoutScore());
        scores.put("interactivity", report.getInteractivityScore());
        scores.put("completeness", report.getCompletenessScore());
        scores.put("education", report.getEducationScore());

        // issues 解析
        List<ObservationIssue> parsedIssues = new ArrayList<>();
        if (report.getIssues() != null) {
            for (String text : report.getIssues()) {
                ObservationIssue issue = ObservationIssue.fromIssueText(text);
                if (issue != null) {
                    parsedIssues.add(issue);
                }
            }
        }
        obs.setIssues(parsedIssues);

        // ProbeSummary
        summary.setPageLoaded(report.isPageLoaded());
        summary.setJsErrorCount(report.getErrors() != null ? report.getErrors().size() : 0);
        summary.setEventCount(report.getEvents() != null ? report.getEvents().size() : 0);
        summary.setDomMutationsCount(report.getDomMutationsCount());
        summary.setOutOfBoundsCount(
                report.getOutOfBoundsElements() != null ? report.getOutOfBoundsElements().size() : 0);

        if (report.getStateTransitions() != null) {
            summary.setStateTransitions(new ArrayList<>(report.getStateTransitions()));
        } else {
            summary.setStateTransitions(new ArrayList<>());
        }

        if (report.getFinalState() != null) {
            summary.setFinalScore(report.getFinalState().getScore());
        }

        return obs;
    }

    /**
     * 构造降级观察 — Playwright 超时或评估异常时的兜底。
     */
    public static EvaluationObservation degraded(int estimatedScore, String reason, List<String> issueTexts) {
        EvaluationObservation obs = new EvaluationObservation();
        obs.setDegraded(true);
        obs.setDegradedReason(reason);
        obs.setTotalScore(estimatedScore);

        // 五维全 0：降级时无可信运行时数据
        Map<String, Integer> scores = obs.getScoresByDimension();
        scores.put("runnability", 0);
        scores.put("layout", 0);
        scores.put("interactivity", 0);
        scores.put("completeness", 0);
        scores.put("education", 0);

        List<ObservationIssue> parsedIssues = new ArrayList<>();
        if (issueTexts != null) {
            for (String text : issueTexts) {
                ObservationIssue issue = ObservationIssue.fromIssueText(text);
                if (issue != null) {
                    parsedIssues.add(issue);
                }
            }
        }
        obs.setIssues(parsedIssues);

        ProbeSummary summary = new ProbeSummary();
        summary.setPageLoaded(false);
        summary.setStateTransitions(new ArrayList<>());
        obs.setProbeSummary(summary);

        return obs;
    }
}

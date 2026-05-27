package com.sumo.agent.agent.evaluation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EvaluationObservation 单元测试 — 验证 ProbeReport → 结构化观察的映射、负面用例和降级路径。
 */
class EvaluationObservationTest {

    // 1. fromProbeReport 总分映射正确
    @Test
    void fromProbeReport_totalScoreMappedCorrectly() {
        ProbeReport report = new ProbeReport();
        report.setTotalScore(82);

        EvaluationObservation obs = EvaluationObservation.fromProbeReport(report);

        assertEquals(82, obs.getTotalScore(), "totalScore 必须直传");
    }

    // 2. 五维评分进入 scoresByDimension，使用统一 key
    @Test
    void fromProbeReport_fiveDimensionsEnterScoresMap() {
        ProbeReport report = new ProbeReport();
        report.setRunnabilityScore(18);
        report.setLayoutScore(15);
        report.setInteractivityScore(17);
        report.setCompletenessScore(16);
        report.setEducationScore(14);

        EvaluationObservation obs = EvaluationObservation.fromProbeReport(report);

        assertEquals(18, obs.getScoresByDimension().get("runnability"));
        assertEquals(15, obs.getScoresByDimension().get("layout"));
        assertEquals(17, obs.getScoresByDimension().get("interactivity"));
        assertEquals(16, obs.getScoresByDimension().get("completeness"));
        assertEquals(14, obs.getScoresByDimension().get("education"));
    }

    // 3. errors.size() → jsErrorCount，events.size() → eventCount，domMutationsCount 直传
    @Test
    void fromProbeReport_countsMappedToProbeSummary() {
        ProbeReport report = new ProbeReport();

        List<ProbeReport.ProbeError> errors = new ArrayList<>();
        errors.add(new ProbeReport.ProbeError());
        errors.add(new ProbeReport.ProbeError());
        report.setErrors(errors);

        List<ProbeReport.ProbeEvent> events = new ArrayList<>();
        events.add(new ProbeReport.ProbeEvent());
        events.add(new ProbeReport.ProbeEvent());
        events.add(new ProbeReport.ProbeEvent());
        report.setEvents(events);

        report.setDomMutationsCount(7);

        EvaluationObservation obs = EvaluationObservation.fromProbeReport(report);
        EvaluationObservation.ProbeSummary ps = obs.getProbeSummary();

        assertNotNull(ps);
        assertEquals(2, ps.getJsErrorCount(), "jsErrorCount 应等于 errors.size()");
        assertEquals(3, ps.getEventCount(), "eventCount 应等于 events.size()");
        assertEquals(7, ps.getDomMutationsCount(), "domMutationsCount 直传");
    }

    // 4. outOfBoundsElements.size() → outOfBoundsCount
    @Test
    void fromProbeReport_outOfBoundsCountMapped() {
        ProbeReport report = new ProbeReport();
        List<ProbeReport.OutOfBoundsElement> oob = new ArrayList<>();
        oob.add(new ProbeReport.OutOfBoundsElement());
        oob.add(new ProbeReport.OutOfBoundsElement());
        report.setOutOfBoundsElements(oob);

        EvaluationObservation obs = EvaluationObservation.fromProbeReport(report);

        assertEquals(2, obs.getProbeSummary().getOutOfBoundsCount());
    }

    // 5. fromProbeReport(null) 不抛 NPE，返回空 observation
    @Test
    void fromProbeReport_nullInput_returnsEmptyObservation() {
        EvaluationObservation obs = EvaluationObservation.fromProbeReport(null);

        assertNotNull(obs, "null 输入应返回非 null 空 observation");
        assertEquals(0, obs.getTotalScore());
        assertNotNull(obs.getIssues(), "issues 不应为 null");
        assertTrue(obs.getIssues().isEmpty(), "issues 应为空 List");
        assertNotNull(obs.getProbeSummary(), "probeSummary 不应为 null");
        assertFalse(obs.getProbeSummary().isPageLoaded());
        assertEquals(0, obs.getProbeSummary().getJsErrorCount());
    }

    // 6. report.getIssues() == null → issues 空 List 而非 null
    @Test
    void fromProbeReport_nullIssues_yieldsEmptyList() {
        ProbeReport report = new ProbeReport();
        report.setIssues(null);

        EvaluationObservation obs = EvaluationObservation.fromProbeReport(report);

        assertNotNull(obs.getIssues(), "issues 必须非 null");
        assertTrue(obs.getIssues().isEmpty(), "应为空 List");
    }

    // 7. report.getFinalState() == null → ProbeSummary.finalScore 为 null
    @Test
    void fromProbeReport_nullFinalState_doesNotThrow() {
        ProbeReport report = new ProbeReport();
        report.setFinalState(null);

        EvaluationObservation obs = EvaluationObservation.fromProbeReport(report);

        assertNotNull(obs.getProbeSummary());
        assertNull(obs.getProbeSummary().getFinalScore(), "finalState 为 null 时 finalScore 应为 null");
    }

    // 8. report.getStateTransitions() == null → ProbeSummary.stateTransitions 空 List
    @Test
    void fromProbeReport_nullStateTransitions_yieldsEmptyList() {
        ProbeReport report = new ProbeReport();
        report.setStateTransitions(null);

        EvaluationObservation obs = EvaluationObservation.fromProbeReport(report);

        assertNotNull(obs.getProbeSummary().getStateTransitions(), "stateTransitions 必须非 null");
        assertTrue(obs.getProbeSummary().getStateTransitions().isEmpty(), "应为空 List");
    }

    // 9. degraded(...) 路径
    @Test
    void degraded_buildsDegradedObservationWithReason() {
        EvaluationObservation obs = EvaluationObservation.degraded(
                50, "Playwright 超时",
                Arrays.asList("[评估] xxx", "[可运行性] yyy"));

        assertTrue(obs.isDegraded(), "degraded 标志应为 true");
        assertEquals("Playwright 超时", obs.getDegradedReason());
        assertEquals(50, obs.getTotalScore());
        assertEquals(2, obs.getIssues().size(), "应解析出 2 个 issue");
        assertEquals("evaluation", obs.getIssues().get(0).getCategory(), "第一条来自 [评估]");
        assertEquals("runnability", obs.getIssues().get(1).getCategory(), "第二条来自 [可运行性]");

        // 五维全 0：降级时无可信运行时数据
        assertEquals(0, obs.getScoresByDimension().get("runnability"));
        assertEquals(0, obs.getScoresByDimension().get("layout"));
        assertEquals(0, obs.getScoresByDimension().get("interactivity"));
        assertEquals(0, obs.getScoresByDimension().get("completeness"));
        assertEquals(0, obs.getScoresByDimension().get("education"));

        // ProbeSummary 兜底
        assertNotNull(obs.getProbeSummary());
        assertFalse(obs.getProbeSummary().isPageLoaded());
        assertNotNull(obs.getProbeSummary().getStateTransitions());
        assertTrue(obs.getProbeSummary().getStateTransitions().isEmpty());
    }
}

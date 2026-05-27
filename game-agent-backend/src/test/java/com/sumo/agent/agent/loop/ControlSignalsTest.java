package com.sumo.agent.agent.loop;

import com.sumo.agent.agent.evaluation.EvaluationObservation;
import com.sumo.agent.agent.evaluation.ObservationIssue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ControlSignals 单元测试 — 覆盖 plan §数据/字段验收 + §负面用例。
 */
class ControlSignalsTest {

    // 1. compute(null, null) 全 false 不抛 NPE
    @Test
    void compute_bothNull_allFalseNoNpe() {
        ControlSignals signals = ControlSignals.compute(null, null);

        assertNotNull(signals);
        assertFalse(signals.isScoreImproved());
        assertFalse(signals.isSameIssuesRepeated());
        assertFalse(signals.isCriticalIssueExists());
        assertFalse(signals.isEvaluationDegraded());
        assertFalse(signals.isShouldFullRewrite());
    }

    // 2. 第一轮（trace 为空）→ scoreImproved=false
    @Test
    void compute_firstIterationEmptyTrace_scoreImprovedFalse() {
        WorkingMemory memory = new WorkingMemory();
        memory.setEvalScore(70);
        RunTrace trace = new RunTrace();

        ControlSignals signals = ControlSignals.compute(memory, trace);

        assertFalse(signals.isScoreImproved(), "无上一轮可比时不算 improved");
    }

    // 3. 当前 80, 上一轮 60 → scoreImproved=true
    @Test
    void compute_scoreUp_yieldsImproved() {
        WorkingMemory memory = new WorkingMemory();
        memory.setEvalScore(80);
        RunTrace trace = new RunTrace();
        trace.append(traceWithScore(60));

        ControlSignals signals = ControlSignals.compute(memory, trace);

        assertTrue(signals.isScoreImproved());
    }

    // 4. 当前 60, 上一轮 60 → scoreImproved=false
    @Test
    void compute_scoreSame_noImproved() {
        WorkingMemory memory = new WorkingMemory();
        memory.setEvalScore(60);
        RunTrace trace = new RunTrace();
        trace.append(traceWithScore(60));

        ControlSignals signals = ControlSignals.compute(memory, trace);

        assertFalse(signals.isScoreImproved());
    }

    // 5. 当前 50, 上一轮 60 → scoreImproved=false
    @Test
    void compute_scoreDown_noImproved() {
        WorkingMemory memory = new WorkingMemory();
        memory.setEvalScore(50);
        RunTrace trace = new RunTrace();
        trace.append(traceWithScore(60));

        ControlSignals signals = ControlSignals.compute(memory, trace);

        assertFalse(signals.isScoreImproved());
    }

    // 6. 当前 [A,B] 上一轮 [A,B] → sameIssuesRepeated=true
    @Test
    void compute_sameIssueSet_repeated() {
        WorkingMemory memory = new WorkingMemory();
        memory.getOpenIssues().addAll(Arrays.asList("A", "B"));

        RunTrace trace = new RunTrace();
        TraceEntry prev = traceWithScore(60);
        prev.setIssuesSnapshot(new ArrayList<>(Arrays.asList("A", "B")));
        trace.append(prev);

        ControlSignals signals = ControlSignals.compute(memory, trace);

        assertTrue(signals.isSameIssuesRepeated());
    }

    // 7. 当前 [A,B] 上一轮 [A,C] → sameIssuesRepeated=false
    @Test
    void compute_differentIssueSet_notRepeated() {
        WorkingMemory memory = new WorkingMemory();
        memory.getOpenIssues().addAll(Arrays.asList("A", "B"));

        RunTrace trace = new RunTrace();
        TraceEntry prev = traceWithScore(60);
        prev.setIssuesSnapshot(new ArrayList<>(Arrays.asList("A", "C")));
        trace.append(prev);

        ControlSignals signals = ControlSignals.compute(memory, trace);

        assertFalse(signals.isSameIssuesRepeated());
    }

    // 8. 当前空 + 上一轮空 → sameIssuesRepeated=false（plan 负面用例 #2）
    @Test
    void compute_bothIssueSetsEmpty_notRepeated() {
        WorkingMemory memory = new WorkingMemory();

        RunTrace trace = new RunTrace();
        TraceEntry prev = traceWithScore(60);
        prev.setIssuesSnapshot(new ArrayList<>());
        trace.append(prev);

        ControlSignals signals = ControlSignals.compute(memory, trace);

        assertFalse(signals.isSameIssuesRepeated(), "空 issues 不应被判定为重复");
    }

    // 9. observation.degraded=true → evaluationDegraded=true
    @Test
    void compute_degradedObservation_evaluationDegradedTrue() {
        WorkingMemory memory = new WorkingMemory();
        memory.setLastEvaluationObservation(
                EvaluationObservation.degraded(40, "Playwright 超时", List.of("[评估] 超时"))
        );

        ControlSignals signals = ControlSignals.compute(memory, new RunTrace());

        assertTrue(signals.isEvaluationDegraded());
    }

    // 10. observation 含 critical issue → criticalIssueExists=true
    @Test
    void compute_observationWithCriticalIssue_flagged() {
        WorkingMemory memory = new WorkingMemory();
        EvaluationObservation obs = new EvaluationObservation();
        obs.setIssues(new ArrayList<>(List.of(
                new ObservationIssue("interactivity", "minor", "minor issue", null),
                new ObservationIssue("runnability", "critical", "JS 错误", null)
        )));
        memory.setLastEvaluationObservation(obs);

        ControlSignals signals = ControlSignals.compute(memory, new RunTrace());

        assertTrue(signals.isCriticalIssueExists());
    }

    // 11. fixCount=3 + sameIssuesRepeated=true + scoreImproved=false → shouldFullRewrite=true
    @Test
    void compute_fixCountThreeAndStuck_shouldFullRewrite() {
        WorkingMemory memory = new WorkingMemory();
        memory.setFixCount(3);
        memory.setEvalScore(60);
        memory.getOpenIssues().addAll(Arrays.asList("A", "B"));

        RunTrace trace = new RunTrace();
        TraceEntry prev = traceWithScore(60);
        prev.setIssuesSnapshot(new ArrayList<>(Arrays.asList("A", "B")));
        trace.append(prev);

        ControlSignals signals = ControlSignals.compute(memory, trace);

        assertFalse(signals.isScoreImproved());
        assertTrue(signals.isSameIssuesRepeated());
        assertTrue(signals.isShouldFullRewrite());
    }

    // 12. fixCount=2 同样卡顿 → shouldFullRewrite=false
    @Test
    void compute_fixCountTwoStuck_shouldNotRewrite() {
        WorkingMemory memory = new WorkingMemory();
        memory.setFixCount(2);
        memory.setEvalScore(60);
        memory.getOpenIssues().addAll(Arrays.asList("A", "B"));

        RunTrace trace = new RunTrace();
        TraceEntry prev = traceWithScore(60);
        prev.setIssuesSnapshot(new ArrayList<>(Arrays.asList("A", "B")));
        trace.append(prev);

        ControlSignals signals = ControlSignals.compute(memory, trace);

        assertTrue(signals.isSameIssuesRepeated());
        assertFalse(signals.isShouldFullRewrite(), "fixCount<3 时不应触发全量重写");
    }

    // 辅助：构造仅设置 scoreAfter 的 TraceEntry
    private TraceEntry traceWithScore(int scoreAfter) {
        TraceEntry entry = new TraceEntry();
        entry.setIteration(1);
        entry.setScoreBefore(0);
        entry.setScoreAfter(scoreAfter);
        entry.setIssueCount(0);
        entry.setResponseLength(0);
        entry.setGameVersion(0);
        entry.setSummary("prev");
        entry.setIssuesSnapshot(new ArrayList<>());
        return entry;
    }
}

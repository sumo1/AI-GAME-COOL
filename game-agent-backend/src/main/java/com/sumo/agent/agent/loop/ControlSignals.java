package com.sumo.agent.agent.loop;

import com.sumo.agent.agent.evaluation.EvaluationObservation;
import com.sumo.agent.agent.evaluation.ObservationIssue;
import lombok.Data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 控制信号 — 由当前 {@link WorkingMemory} 与上一轮 {@link RunTrace} 计算的布尔标志，
 * 用于让 LLM（以及未来的策略层）感知"是否在进步""是否在原地打转"等元信息。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>所有字段默认 false，缺数据时不强行推断。</li>
 *   <li>计算函数 {@link #compute(WorkingMemory, RunTrace)} 接受 null 输入不抛异常。</li>
 * </ul>
 */
@Data
public class ControlSignals {

    /** 当前评分 &gt; 上一轮 scoreAfter；无上一轮时 false */
    private boolean scoreImproved;

    /** 当前 openIssues 与上一轮 issuesSnapshot 集合相等且非空；无上一轮 / 任一为空时 false */
    private boolean sameIssuesRepeated;

    /** 当前 lastEvaluationObservation 中存在 severity="critical" 的 issue */
    private boolean criticalIssueExists;

    /** 当前 lastEvaluationObservation.isDegraded() == true */
    private boolean evaluationDegraded;

    /** 启发式：fixCount &gt;= 3 且未进步且重复问题，提示 LLM 考虑全量重写 */
    private boolean shouldFullRewrite;

    /**
     * 基于当前 memory 与累计 trace 计算控制信号。
     *
     * @param memory 当前工作记忆；为 null 时返回全 false 信号
     * @param trace  累计轨迹；为 null 视为空 trace
     * @return 新构造的 ControlSignals 实例
     */
    public static ControlSignals compute(WorkingMemory memory, RunTrace trace) {
        ControlSignals signals = new ControlSignals();
        if (memory == null) {
            return signals;
        }

        TraceEntry prev = trace != null ? trace.last() : null;

        // scoreImproved：必须有上一轮可比
        if (prev != null) {
            signals.scoreImproved = memory.getEvalScore() > prev.getScoreAfter();
        }

        // sameIssuesRepeated：当前与上一轮 issuesSnapshot 集合相等且非空
        if (prev != null) {
            List<String> currentIssues = memory.getOpenIssues();
            List<String> prevIssues = prev.getIssuesSnapshot();
            if (currentIssues != null && !currentIssues.isEmpty()
                    && prevIssues != null && !prevIssues.isEmpty()) {
                Set<String> currentSet = new HashSet<>(currentIssues);
                Set<String> prevSet = new HashSet<>(prevIssues);
                signals.sameIssuesRepeated = currentSet.equals(prevSet);
            }
        }

        // criticalIssueExists / evaluationDegraded：从当前 observation 取
        EvaluationObservation obs = memory.getLastEvaluationObservation();
        if (obs != null) {
            signals.evaluationDegraded = obs.isDegraded();
            if (obs.getIssues() != null) {
                for (ObservationIssue iss : obs.getIssues()) {
                    if (iss != null && "critical".equals(iss.getSeverity())) {
                        signals.criticalIssueExists = true;
                        break;
                    }
                }
            }
        }

        // shouldFullRewrite：保守启发式
        signals.shouldFullRewrite = memory.getFixCount() >= 3
                && !signals.scoreImproved
                && signals.sameIssuesRepeated;

        return signals;
    }
}

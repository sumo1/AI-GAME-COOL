package com.sumo.agent.agent.loop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单轮迭代轨迹条目 — 记录 AgentLoop 一轮 LLM 调用前后的事实快照。
 * <p>
 * 仅事实记录，不做推断；推断由 {@link ControlSignals} 基于若干 TraceEntry 计算。
 * 不持久化到 DB，仅在 {@link RunTrace} 内随会话生灭。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TraceEntry {

    /** 当前轮次（1-based） */
    private int iteration;

    /** 本轮 LLM 调用前的评分 */
    private int scoreBefore;

    /** 本轮 LLM 调用后的评分 */
    private int scoreAfter;

    /** 本轮结束时 openIssues 的数量 */
    private int issueCount;

    /** LLM 文本响应的字符数；null 响应记 0 */
    private int responseLength;

    /** 本轮结束时 gameVersion */
    private int gameVersion;

    /** 一句话总结，例如 "score 60→78 (+18)" */
    private String summary;

    /** 本轮评估是否走了降级路径 */
    private boolean evaluationDegraded;

    /** 本轮 openIssues 文本快照，供下一轮 ControlSignals 比较"重复问题" */
    private List<String> issuesSnapshot = new ArrayList<>();
}

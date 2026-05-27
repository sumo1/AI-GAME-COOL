package com.sumo.agent.agent.loop;

import com.sumo.agent.agent.evaluation.EvaluationObservation;

/**
 * AgentLoop 执行结果。
 * <p>
 * 任务 260524 Step 4：在原 6 字段基础上扩展 evidence payload（lastEvaluationObservation /
 * runTrace / activeSkillName / errorType），由 SessionService.recordEvidence 写入
 * game_run_evaluations 表。旧 success/failure 工厂保留，向后兼容。
 */
public record AgentLoopResult(
        boolean success,
        String gameHtml,
        String llmMessage,
        int iterations,
        int evalScore,
        String error,
        EvaluationObservation lastEvaluationObservation,
        RunTrace runTrace,
        String activeSkillName,
        String errorType
) {
    /** 旧工厂：保持向后兼容（evidence 字段填 null）。 */
    public static AgentLoopResult success(String gameHtml, String llmMessage, int iterations, int evalScore) {
        return new AgentLoopResult(true, gameHtml, llmMessage, iterations, evalScore, null,
                null, null, null, null);
    }

    /** 旧工厂：保持向后兼容（evidence 字段填 null）。 */
    public static AgentLoopResult failure(String error, int iterations) {
        return new AgentLoopResult(false, null, null, iterations, 0, error,
                null, null, null, null);
    }

    /** 新工厂：成功路径含 evidence。 */
    public static AgentLoopResult successWithEvidence(
            String gameHtml,
            String llmMessage,
            int iterations,
            int evalScore,
            EvaluationObservation observation,
            RunTrace runTrace,
            String activeSkillName) {
        return new AgentLoopResult(true, gameHtml, llmMessage, iterations, evalScore, null,
                observation, runTrace, activeSkillName, null);
    }

    /** 新工厂：失败路径含 evidence + errorType。 */
    public static AgentLoopResult failureWithEvidence(
            String error,
            int iterations,
            String errorType,
            EvaluationObservation observation,
            RunTrace runTrace,
            String activeSkillName) {
        return new AgentLoopResult(false, null, null, iterations, 0, error,
                observation, runTrace, activeSkillName, errorType);
    }
}

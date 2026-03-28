package com.sumo.agent.v2.loop;

/**
 * AgentLoop 执行结果
 */
public record AgentLoopResult(
        boolean success,
        String gameHtml,
        String llmMessage,
        int iterations,
        int evalScore,
        String error
) {
    public static AgentLoopResult success(String gameHtml, String llmMessage, int iterations, int evalScore) {
        return new AgentLoopResult(true, gameHtml, llmMessage, iterations, evalScore, null);
    }

    public static AgentLoopResult failure(String error, int iterations) {
        return new AgentLoopResult(false, null, null, iterations, 0, error);
    }
}

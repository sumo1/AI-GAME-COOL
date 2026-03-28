package com.sumo.agent.v2.tool;

/**
 * 工具执行结果
 */
public record ToolResult(
        boolean success,
        String data,
        String error
) {
    public static ToolResult success(String data) {
        return new ToolResult(true, data, null);
    }

    public static ToolResult failure(String error) {
        return new ToolResult(false, null, error);
    }
}

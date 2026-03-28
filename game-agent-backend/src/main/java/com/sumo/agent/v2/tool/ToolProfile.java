package com.sumo.agent.v2.tool;

/**
 * 工具描述信息（name, description, parameters schema）
 */
public record ToolProfile(
        String name,
        String description,
        String parametersSchema
) {
}

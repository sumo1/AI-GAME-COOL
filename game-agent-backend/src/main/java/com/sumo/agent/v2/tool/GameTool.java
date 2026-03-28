package com.sumo.agent.v2.tool;

/**
 * 游戏工具接口 — Agent 的 "手"
 * <p>
 * 每个工具负责一项原子能力（生成游戏、评估游戏、加载 Skill 等）。
 * 工具通过 Spring @Component 自动注册到 ToolRegistry。
 */
public interface GameTool {

    /**
     * 工具描述信息（供 LLM Function Calling 使用）
     */
    ToolProfile getProfile();

    /**
     * 执行工具
     *
     * @param input JSON 格式的输入参数
     * @return 执行结果
     */
    ToolResult execute(String input);
}

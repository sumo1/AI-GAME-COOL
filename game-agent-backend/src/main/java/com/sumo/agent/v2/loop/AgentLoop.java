package com.sumo.agent.v2.loop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumo.agent.config.ChatModelRouter;
import com.sumo.agent.v2.tool.GameTool;
import com.sumo.agent.v2.tool.ToolRegistry;
import com.sumo.agent.v2.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent Loop 核心类 — 多轮 Think-Act-Observe 迭代循环
 * <p>
 * 使用 prompt-based tool calling：在系统提示词中定义工具 schema，
 * LLM 以 JSON 格式返回工具调用指令，AgentLoop 解析并执行。
 * 不依赖 Spring AI 的 FunctionCallback 机制，更可控、兼容性更好。
 * <p>
 * 流程：
 * 1. 构建系统提示词（含工具定义 + Working Memory）
 * 2. 调用 LLM → 解析响应中的工具调用 → 执行工具 → 将结果追加到消息历史
 * 3. 如果 LLM 未调用工具（纯文本回复），视为任务完成
 * 4. 检查质量门禁，未达标则继续迭代
 * 5. 达标或达到最大迭代次数 → 返回结果
 */
@Slf4j
@Service
public class AgentLoop {

    private static final int MAX_ITERATIONS = 5;
    private static final int QUALITY_GATE_SCORE = 80;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 匹配 LLM 输出中的工具调用 JSON 块 */
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "```tool_call\\s*\\n(\\{.*?\\})\\s*\\n```",
            Pattern.DOTALL
    );

    @Autowired
    private ChatModelRouter chatModelRouter;

    @Autowired
    private ToolRegistry toolRegistry;

    /**
     * 执行 Agent Loop
     *
     * @param userInput 用户的自然语言输入
     * @param modelKey  模型路由 key（null 表示默认模型）
     * @return 执行结果
     */
    public AgentLoopResult run(String userInput, String modelKey) {
        ChatModel chatModel = chatModelRouter.get(modelKey);
        if (chatModel == null) {
            return AgentLoopResult.failure("未配置可用的 ChatModel", 0);
        }

        WorkingMemory memory = new WorkingMemory();
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(userInput));

        log.info("🔄 AgentLoop 启动, 用户输入: {}", userInput);

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            memory.setIteration(i + 1);
            log.info("🔄 迭代 {}/{}", i + 1, MAX_ITERATIONS);

            try {
                // 构建 prompt
                String systemPrompt = buildSystemPrompt(memory);
                List<Message> promptMessages = new ArrayList<>();
                promptMessages.add(new SystemMessage(systemPrompt));
                promptMessages.addAll(messages);

                // 调用 LLM
                Prompt prompt = new Prompt(promptMessages);
                ChatResponse response = chatModel.call(prompt);
                String llmOutput = response.getResult().getOutput().getText();

                log.info("🤖 LLM 响应 ({} 字符)", llmOutput != null ? llmOutput.length() : 0);

                // 解析工具调用
                ToolCallRequest toolCall = parseToolCall(llmOutput);

                if (toolCall == null) {
                    // 无工具调用 = LLM 认为任务完成，返回纯文本
                    log.info("✅ LLM 未调用工具，任务完成，迭代 {} 次", i + 1);
                    if (memory.getGameHtml() != null) {
                        return AgentLoopResult.success(memory.getGameHtml(), llmOutput, i + 1, memory.getEvalScore());
                    }
                    // 没有游戏 HTML 但 LLM 停止了，返回文本响应
                    return AgentLoopResult.success(null, llmOutput, i + 1, 0);
                }

                // 执行工具
                log.info("🔧 执行工具: {}({})", toolCall.tool, truncate(toolCall.input, 100));
                messages.add(new AssistantMessage(llmOutput));

                GameTool tool = toolRegistry.getTool(toolCall.tool);
                if (tool == null) {
                    String errMsg = "未知工具: " + toolCall.tool;
                    log.warn("⚠️ {}", errMsg);
                    messages.add(new UserMessage("[工具执行结果]\n工具: " + toolCall.tool + "\n状态: 失败\n错误: " + errMsg));
                    continue;
                }

                ToolResult result = tool.execute(toolCall.input);

                // 更新 WorkingMemory
                if ("generate_game".equals(toolCall.tool) && result.success()) {
                    memory.setGameHtml(result.data());
                    memory.incrementGameVersion();
                    log.info("📝 游戏 HTML 已更新, 版本: {}", memory.getGameVersion());
                }

                // 将工具结果追加到消息历史
                String obsMessage = formatObservation(toolCall.tool, result);
                messages.add(new UserMessage(obsMessage));

                // 检查质量门禁
                if (memory.getGameHtml() != null && memory.getEvalScore() >= QUALITY_GATE_SCORE) {
                    log.info("✅ 质量达标 ({}分), 迭代 {} 次", memory.getEvalScore(), i + 1);
                    return AgentLoopResult.success(memory.getGameHtml(), llmOutput, i + 1, memory.getEvalScore());
                }

            } catch (Exception e) {
                log.error("❌ 迭代 {} 出错: {}", i + 1, e.getMessage(), e);
                if (memory.getGameHtml() != null) {
                    return AgentLoopResult.success(
                            memory.getGameHtml(),
                            "生成过程遇到问题，返回当前版本: " + e.getMessage(),
                            i + 1, memory.getEvalScore()
                    );
                }
                return AgentLoopResult.failure("AgentLoop 执行失败: " + e.getMessage(), i + 1);
            }
        }

        // 达到最大迭代次数
        log.warn("⚠️ 达到最大迭代次数 {}", MAX_ITERATIONS);
        if (memory.getGameHtml() != null) {
            return AgentLoopResult.success(memory.getGameHtml(), "达到最大迭代次数，返回当前最佳版本",
                    MAX_ITERATIONS, memory.getEvalScore());
        }
        return AgentLoopResult.failure("达到最大迭代次数仍未生成游戏", MAX_ITERATIONS);
    }

    /**
     * 从 LLM 输出中解析工具调用。
     * 格式约定：
     * ```tool_call
     * {"tool": "generate_game", "input": "..."}
     * ```
     */
    private ToolCallRequest parseToolCall(String llmOutput) {
        if (llmOutput == null) return null;

        Matcher matcher = TOOL_CALL_PATTERN.matcher(llmOutput);
        if (!matcher.find()) return null;

        try {
            JsonNode node = MAPPER.readTree(matcher.group(1));
            String tool = node.has("tool") ? node.get("tool").asText() : null;
            String input = node.has("input") ? node.get("input").toString() : "{}";
            if (tool == null || tool.isBlank()) return null;
            return new ToolCallRequest(tool, input);
        } catch (Exception e) {
            log.warn("⚠️ 解析工具调用 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 格式化工具执行结果（Observation），追加到消息历史供 LLM 下一轮参考
     */
    private String formatObservation(String toolName, ToolResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[工具执行结果]\n");
        sb.append("工具: ").append(toolName).append("\n");
        sb.append("状态: ").append(result.success() ? "成功" : "失败").append("\n");
        if (result.success()) {
            sb.append("输出:\n").append(truncate(result.data(), 2000));
        } else {
            sb.append("错误: ").append(result.error());
        }
        return sb.toString();
    }

    /**
     * 构建系统提示词（角色定义 + 工具协议 + Working Memory）
     */
    private String buildSystemPrompt(WorkingMemory memory) {
        StringBuilder sb = new StringBuilder();
        sb.append(SEMANTIC_PROMPT).append("\n\n");
        sb.append(buildToolProtocol()).append("\n\n");
        sb.append(memory.toContextXml());
        return sb.toString();
    }

    /**
     * 构建工具调用协议说明（告诉 LLM 如何调用工具）
     */
    private String buildToolProtocol() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 工具调用协议\n\n");
        sb.append("你可以调用以下工具来完成任务。调用工具时，使用以下格式：\n\n");
        sb.append("```tool_call\n");
        sb.append("{\"tool\": \"工具名称\", \"input\": \"输入参数（JSON 字符串或纯文本）\"}\n");
        sb.append("```\n\n");
        sb.append("**重要**：每次回复最多调用一个工具。工具执行结果会在下一轮消息中返回给你。\n");
        sb.append("当你认为任务完成时，直接用自然语言回复用户，不要调用工具。\n\n");
        sb.append("### 可用工具\n\n");

        for (GameTool tool : toolRegistry.getAllTools()) {
            var profile = tool.getProfile();
            sb.append("**").append(profile.name()).append("**\n");
            sb.append("- 描述: ").append(profile.description()).append("\n");
            sb.append("- 参数: ").append(profile.parametersSchema()).append("\n\n");
        }

        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /** 工具调用请求 */
    private record ToolCallRequest(String tool, String input) {}

    /**
     * 语义记忆（角色定义 + 领域知识 + 质量标准）
     */
    private static final String SEMANTIC_PROMPT = """
            你是一个儿童教育游戏设计专家（Game Agent）。你的工作是根据用户的需求描述，
            设计并生成完整的 HTML5 教育小游戏。你追求的不是"能跑"，而是"好玩、有教育意义、没有 bug"。

            ## 你的工作流程

            1. **分析需求**：理解用户想要什么类型的游戏、适合什么年龄段、有什么教育目标
            2. **查找技能模板**：调用 list_skills 查看是否有匹配的内置模板
            3. **加载模板**（可选）：如果有匹配的模板，调用 load_skill 获取参考
            4. **生成游戏**：调用 generate_game 生成完整的 HTML5 游戏
            5. **总结反馈**：向用户描述生成的游戏特点（不调用工具，直接文字回复）

            ## 游戏质量标准

            - 游戏必须是单个完整的 HTML 文件（内联 CSS/JS，不依赖外部资源）
            - 必须有明确的开始和结束
            - 必须有计分或进度反馈
            - 操作必须简单直觉（点击/拖拽）
            - 视觉元素不能超出可见区域
            - 失败时给鼓励而非惩罚
            - 响应式布局，适配不同屏幕

            ## 输出要求

            在调用工具完成游戏生成后，请用简短的中文向用户说明：
            - 生成了什么游戏
            - 适合什么年龄段
            - 核心玩法是什么
            - 有哪些教育目标
            """;
}

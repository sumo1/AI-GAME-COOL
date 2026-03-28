package com.sumo.agent.v2.loop;

import com.sumo.agent.config.ChatModelRouter;
import com.sumo.agent.v2.tools.GameTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Agent Loop 核心类 — 使用 Spring AI 原生 Function Calling
 * <p>
 * ChatClient.tools(gameTools) 将 @Tool 方法暴露给 LLM。
 * Spring AI 内部自动处理 FC 循环：LLM 返回 tool_calls → 执行工具 → 返回结果 → LLM 继续。
 * <p>
 * AgentLoop 的外部循环用于质量门禁（Phase 3: evaluate → fix → 再 evaluate）。
 * Phase 1 阶段，单次 ChatClient 调用即可完成：查 Skill → 生成游戏 → 总结反馈。
 */
@Slf4j
@Service
public class AgentLoop {

    private static final int MAX_ITERATIONS = 5;
    private static final int QUALITY_GATE_SCORE = 80;

    @Autowired
    private ChatModelRouter chatModelRouter;

    @Autowired
    private GameTools gameTools;

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
        log.info("🔄 AgentLoop 启动, 用户输入: {}", userInput);

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            memory.setIteration(i + 1);
            log.info("🔄 迭代 {}/{}", i + 1, MAX_ITERATIONS);

            try {
                String systemPrompt = buildSystemPrompt(memory);

                // Spring AI Function Calling：
                // ChatClient 自动处理多轮 FC 循环
                // LLM 决定调用 listSkills/loadSkill/generateGame → 执行 → 返回结果 → LLM 继续
                // 最终返回 LLM 的文本总结
                ChatClient chatClient = ChatClient.create(chatModel);
                String response = chatClient
                        .prompt()
                        .system(systemPrompt)
                        .user(buildUserPrompt(userInput, memory))
                        .tools(gameTools)
                        .call()
                        .content();

                log.info("🤖 LLM 响应完成 ({} 字符)", response != null ? response.length() : 0);

                // Phase 1: 单次调用已包含完整 FC 循环，直接返回
                // Phase 3 会在此检查 eval_score 是否达标，决定是否继续迭代
                if (memory.getEvalScore() == 0 || memory.getEvalScore() >= QUALITY_GATE_SCORE) {
                    log.info("✅ AgentLoop 完成, 迭代 {} 次", i + 1);
                    return AgentLoopResult.success(
                            memory.getGameHtml(),
                            response,
                            i + 1,
                            memory.getEvalScore()
                    );
                }

                log.info("⚠️ 评分 {}/100 未达标，继续迭代", memory.getEvalScore());

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

        log.warn("⚠️ 达到最大迭代次数 {}", MAX_ITERATIONS);
        if (memory.getGameHtml() != null) {
            return AgentLoopResult.success(memory.getGameHtml(), "达到最大迭代次数，返回当前最佳版本",
                    MAX_ITERATIONS, memory.getEvalScore());
        }
        return AgentLoopResult.failure("达到最大迭代次数仍未生成游戏", MAX_ITERATIONS);
    }

    private String buildSystemPrompt(WorkingMemory memory) {
        return SEMANTIC_PROMPT + "\n\n" + memory.toContextXml();
    }

    private String buildUserPrompt(String userInput, WorkingMemory memory) {
        if (memory.getIteration() == 1) {
            return userInput;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下问题修复当前游戏：\n");
        for (String issue : memory.getOpenIssues()) {
            sb.append("- ").append(issue).append("\n");
        }
        sb.append("\n原始需求：").append(userInput);
        return sb.toString();
    }

    private static final String SEMANTIC_PROMPT = """
            你是一个儿童教育游戏设计专家（Game Agent）。你的工作是根据用户的需求描述，
            设计并生成完整的 HTML5 教育小游戏。你追求的不是"能跑"，而是"好玩、有教育意义、没有 bug"。

            ## 你的工作流程

            1. **分析需求**：理解用户想要什么类型的游戏、适合什么年龄段、有什么教育目标
            2. **查找技能模板**：调用 listSkills 查看是否有匹配的内置模板
            3. **加载模板**（可选）：如果有匹配的模板，调用 loadSkill 获取参考
            4. **生成游戏**：调用 generateGame 生成完整的 HTML5 游戏
            5. **总结反馈**：向用户描述生成的游戏特点

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

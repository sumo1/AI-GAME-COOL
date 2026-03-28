package com.sumo.agent.v2.loop;

import com.sumo.agent.config.ChatModelRouter;
import com.sumo.agent.v2.tool.GameTool;
import com.sumo.agent.v2.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Agent Loop 核心类 — 多轮 Think-Act-Observe 迭代循环
 * <p>
 * 参考 Agent Harness 的 AgentLoop 设计，用 Spring AI Function Calling 实现。
 * 每次请求创建新的工作记忆实例（无状态设计）。
 * <p>
 * 流程：
 * 1. 构建系统提示词（含 Working Memory 上下文）
 * 2. 调用 LLM → Spring AI 自动处理 tool_calls → 执行工具 → 返回结果给 LLM
 * 3. 检查质量门禁（评分是否达标）
 * 4. 未达标 → 更新 Working Memory → 继续迭代
 * 5. 达标或达到最大迭代次数 → 返回结果
 */
@Slf4j
@Service
public class AgentLoop {

    private static final int MAX_ITERATIONS = 5;
    private static final int QUALITY_GATE_SCORE = 80;

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
        log.info("🔄 AgentLoop 启动, 用户输入: {}", userInput);

        // 构建带 Working Memory 副作用捕获的工具回调
        List<FunctionCallback> callbacks = buildToolCallbacks(memory);

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            memory.setIteration(i + 1);
            log.info("🔄 迭代 {}/{}", i + 1, MAX_ITERATIONS);

            try {
                String systemPrompt = buildSystemPrompt(memory);

                // 使用 ChatClient 调用 LLM，Spring AI 自动处理 Function Calling 循环
                // ChatClient.builder().defaultFunctions() 将工具注册到所有请求
                ChatClient chatClient = ChatClient.builder(chatModel)
                        .defaultFunctions(callbacks.toArray(new FunctionCallback[0]))
                        .build();
                String llmResponse = chatClient.prompt()
                        .system(systemPrompt)
                        .user(buildUserPrompt(userInput, memory))
                        .call()
                        .content();

                log.info("🤖 LLM 响应完成, 游戏版本: {}, 评分: {}",
                        memory.getGameVersion(), memory.getEvalScore());

                // 检查是否生成了游戏
                if (memory.getGameHtml() != null) {
                    // Phase 1: 没有 evaluate_game，生成即完成
                    // Phase 3 会在此检查 eval_score 是否达标
                    if (memory.getEvalScore() == 0 || memory.getEvalScore() >= QUALITY_GATE_SCORE) {
                        log.info("✅ AgentLoop 完成, 迭代 {} 次", i + 1);
                        return AgentLoopResult.success(
                                memory.getGameHtml(),
                                llmResponse,
                                i + 1,
                                memory.getEvalScore()
                        );
                    }
                    // 评分未达标，继续迭代
                    log.info("⚠️ 评分 {}/100 未达标，继续迭代", memory.getEvalScore());
                } else {
                    log.warn("⚠️ 迭代 {} 未产出游戏 HTML", i + 1);
                }

            } catch (Exception e) {
                log.error("❌ 迭代 {} 出错: {}", i + 1, e.getMessage(), e);
                // 如果已有游戏 HTML，返回当前最佳版本
                if (memory.getGameHtml() != null) {
                    return AgentLoopResult.success(
                            memory.getGameHtml(),
                            "生成过程遇到问题，返回当前版本: " + e.getMessage(),
                            i + 1,
                            memory.getEvalScore()
                    );
                }
                return AgentLoopResult.failure("AgentLoop 执行失败: " + e.getMessage(), i + 1);
            }
        }

        // 达到最大迭代次数
        log.warn("⚠️ 达到最大迭代次数 {}", MAX_ITERATIONS);
        if (memory.getGameHtml() != null) {
            return AgentLoopResult.success(
                    memory.getGameHtml(),
                    "达到最大迭代次数，返回当前最佳版本",
                    MAX_ITERATIONS,
                    memory.getEvalScore()
            );
        }
        return AgentLoopResult.failure("达到最大迭代次数仍未生成游戏", MAX_ITERATIONS);
    }

    /**
     * 构建工具回调列表，包装 GameTool 并捕获副作用到 WorkingMemory。
     * <p>
     * 使用 FunctionCallbackWrapper&lt;String, String&gt;：
     * - inputType = String.class → Spring AI 将 LLM 的 JSON arguments 作为原始字符串传入
     * - inputTypeSchema = 工具的参数 JSON Schema → 告知 LLM 函数参数结构
     */
    @SuppressWarnings("unchecked")
    private List<FunctionCallback> buildToolCallbacks(WorkingMemory memory) {
        List<FunctionCallback> callbacks = new ArrayList<>();

        for (GameTool tool : toolRegistry.getAllTools()) {
            String name = tool.getProfile().name();
            String description = tool.getProfile().description();
            String schema = tool.getProfile().parametersSchema();

            // 包装工具执行，捕获结果到 WorkingMemory
            Function<String, String> wrappedFn = (String input) -> {
                log.info("🔧 执行工具: {}({})", name, truncate(input, 100));
                var result = tool.execute(input);

                // 副作用：更新 WorkingMemory
                if ("generate_game".equals(name) && result.success()) {
                    memory.setGameHtml(result.data());
                    memory.incrementGameVersion();
                    log.info("📝 游戏 HTML 已更新, 版本: {}", memory.getGameVersion());
                }

                return result.success() ? result.data() : "工具执行失败: " + result.error();
            };

            FunctionCallbackWrapper<String, String> callback = FunctionCallbackWrapper
                    .<String, String>builder(wrappedFn)
                    .withName(name)
                    .withDescription(description)
                    .withInputType(String.class)
                    .withInputTypeSchema(schema)
                    .build();
            callbacks.add(callback);
        }

        return callbacks;
    }

    /**
     * 构建系统提示词（含角色定义 + Working Memory 上下文）
     */
    private String buildSystemPrompt(WorkingMemory memory) {
        return SEMANTIC_PROMPT + "\n\n" + memory.toContextXml();
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String userInput, WorkingMemory memory) {
        if (memory.getIteration() == 1) {
            return userInput;
        }
        // 后续迭代：带上修复上下文
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下问题修复当前游戏：\n");
        for (String issue : memory.getOpenIssues()) {
            sb.append("- ").append(issue).append("\n");
        }
        sb.append("\n原始需求：").append(userInput);
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

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

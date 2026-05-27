package com.sumo.agent.agent.loop;

import com.sumo.agent.infra.model.ChatModelRegistry;
import com.sumo.agent.agent.evaluation.EvaluationObservation;
import com.sumo.agent.agent.skill.SkillDefinition;
import com.sumo.agent.agent.skill.SkillLoader;
import com.sumo.agent.agent.tools.ToolContext;
import com.sumo.agent.agent.tools.skill.SkillListTool;
import com.sumo.agent.agent.tools.skill.SkillLoadTool;

import com.sumo.agent.agent.tools.generation.ErrorClassifier;
import com.sumo.agent.agent.tools.generation.GameSaveTool;
import com.sumo.agent.agent.tools.evaluation.GameEvaluationTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /** LLM 调用最大重试次数 */
    private static final int MAX_LLM_RETRIES = 2;
    /** 初始重试间隔（毫秒） */
    private static final long RETRY_BASE_DELAY_MS = 2000;

    /** Skill 快速匹配关键词映射：关键词 → skill 名称（连字符格式，对齐 AgentSkills.io 规范） */
    private static final Map<String, String> SKILL_KEYWORD_MAP = Map.of(
            "数学冒险", "math-adventure",
            "数学游戏", "math-adventure",
            "加减法", "math-adventure",
            "记忆翻牌", "memory-master",
            "记忆游戏", "memory-master",
            "翻牌配对", "memory-master",
            "英语", "english-explorer",
            "单词拼写", "english-explorer",
            "交通安全", "traffic-safety",
            "形状颜色", "shape-colors"
    );

    @Autowired
    private ChatModelRegistry chatModelRegistry;

    @Autowired
    private ToolContext toolContext;

    @Autowired
    private SkillListTool skillListTool;

    @Autowired
    private SkillLoadTool skillLoadTool;

    @Autowired
    private GameSaveTool gameSaveTool;

    @Autowired
    private GameEvaluationTool gameEvaluationTool;

    @Autowired
    private SkillLoader skillLoader;

    /** 上下文渲染器，纯逻辑组件，private final 实例即可，不走 Spring 注入 */
    private final ContextRenderer contextRenderer = new ContextRenderer();

    /**
     * 执行 Agent Loop
     *
     * @param userInput 用户的自然语言输入
     * @param modelKey  模型路由 key（null 表示默认模型）
     * @return 执行结果
     */
    public AgentLoopResult run(String userInput, String modelKey) {
        ChatModel chatModel = chatModelRegistry.get(modelKey);
        if (chatModel == null) {
            // 模型不可用 → 走 evidence 路径但 trace/observation 都为 null
            return AgentLoopResult.failureWithEvidence(
                    "未配置可用的 ChatModel", 0, "配置缺失", null, null, null);
        }

        WorkingMemory memory = new WorkingMemory();
        toolContext.init(memory);
        log.info("AgentLoop 启动, 用户输入: {}", userInput);

        try {
            // 快速路径：尝试预加载匹配的 Skill
            tryPreloadSkill(userInput, memory);

            // 注入 Skill Index 到 WorkingMemory（任务 260524 Step 1）
            // 让 LLM 直接从 system prompt 的 <skill_index> 块看到全部 Skill 摘要，
            // 减少 listSkills 工具调用，提高路由命中率。
            memory.setSkillIndex(skillLoader.listSkills());

            for (int i = 0; i < MAX_ITERATIONS; i++) {
                memory.setIteration(i + 1);
                int scoreBefore = memory.getEvalScore();
                log.info("🔄 迭代 {}/{}", i + 1, MAX_ITERATIONS);

                try {
                    String systemPrompt = buildSystemPrompt(memory);
                    String response = callLlmWithRetry(chatModel, systemPrompt, buildUserPrompt(userInput, memory));

                    log.info("🤖 LLM 响应完成 ({} 字符)", response != null ? response.length() : 0);

                    // 记录本轮轨迹 + 重算控制信号（Step 3）
                    // 必须在质量门禁判断之前完成，使达标退出时也保留当轮 trace。
                    recordTraceAndSignals(memory, i + 1, scoreBefore, response);

                    // 质量门禁检查
                    if (memory.getEvalScore() == 0 || memory.getEvalScore() >= QUALITY_GATE_SCORE) {
                        log.info("✅ AgentLoop 完成, 迭代 {} 次", i + 1);
                        return AgentLoopResult.successWithEvidence(
                                memory.getGameHtml(),
                                response,
                                i + 1,
                                memory.getEvalScore(),
                                memory.getLastEvaluationObservation(),
                                memory.getRunTrace(),
                                resolveActiveSkillName(memory)
                        );
                    }

                    log.info("⚠️ 评分 {}/100 未达标，继续迭代", memory.getEvalScore());

                } catch (Exception e) {
                    log.error("❌ 迭代 {} 出错: {}", i + 1, e.getMessage(), e);
                    String errorType = ErrorClassifier.classify(e);
                    if (memory.getGameHtml() != null) {
                        // 已有 html → 仍当成功返回，但带上 evidence
                        return AgentLoopResult.successWithEvidence(
                                memory.getGameHtml(),
                                "生成过程遇到问题，返回当前版本: " + e.getMessage(),
                                i + 1,
                                memory.getEvalScore(),
                                memory.getLastEvaluationObservation(),
                                memory.getRunTrace(),
                                resolveActiveSkillName(memory)
                        );
                    }
                    return AgentLoopResult.failureWithEvidence(
                            "AgentLoop 执行失败: " + e.getMessage(),
                            i + 1,
                            errorType,
                            memory.getLastEvaluationObservation(),
                            memory.getRunTrace(),
                            resolveActiveSkillName(memory)
                    );
                }
            }

            log.warn("⚠️ 达到最大迭代次数 {}", MAX_ITERATIONS);
            if (memory.getGameHtml() != null) {
                return AgentLoopResult.successWithEvidence(
                        memory.getGameHtml(),
                        "达到最大迭代次数，返回当前最佳版本",
                        MAX_ITERATIONS,
                        memory.getEvalScore(),
                        memory.getLastEvaluationObservation(),
                        memory.getRunTrace(),
                        resolveActiveSkillName(memory)
                );
            }
            return AgentLoopResult.failureWithEvidence(
                    "达到最大迭代次数仍未生成游戏",
                    MAX_ITERATIONS,
                    "迭代用尽未达标",
                    memory.getLastEvaluationObservation(),
                    memory.getRunTrace(),
                    resolveActiveSkillName(memory)
            );

        } finally {
            // 清理 ThreadLocal，防止线程池复用时状态泄漏
            toolContext.clear();
        }
    }

    /**
     * 取当前 active skill 名称：优先 toolContext 内的（LLM 通过 loadSkill 显式激活），
     * 退而求其次取 memory.preloadedSkill（关键词快速路径预加载）。
     */
    private String resolveActiveSkillName(WorkingMemory memory) {
        SkillDefinition active = toolContext.getActiveSkill();
        if (active != null && active.getName() != null) {
            return active.getName();
        }
        return memory.getPreloadedSkill();
    }

    /**
     * 带重试的 LLM 调用：失败时最多重试 MAX_LLM_RETRIES 次，
     * 仅对可恢复异常（超时、5xx）重试，间隔指数退避。
     */
    private String callLlmWithRetry(ChatModel chatModel, String systemPrompt, String userPrompt) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_LLM_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = RETRY_BASE_DELAY_MS * (1L << (attempt - 1)); // 指数退避: 2s, 4s
                    log.warn("🔁 LLM 调用重试 {}/{}，等待 {}ms", attempt, MAX_LLM_RETRIES, delay);
                    Thread.sleep(delay);
                }

                ChatClient chatClient = ChatClient.create(chatModel);
                return chatClient
                        .prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .tools(skillListTool, skillLoadTool, gameSaveTool, gameEvaluationTool)
                        .call()
                        .content();

            } catch (Exception e) {
                lastException = e;
                if (!isRetryable(e)) {
                    log.error("❌ LLM 调用不可恢复异常，不再重试: {}", e.getMessage());
                    throw new RuntimeException("LLM 调用失败（不可恢复）: " + e.getMessage(), e);
                }
                log.warn("⚠️ LLM 调用失败 (attempt {}): {}", attempt + 1, e.getMessage());
            }
        }

        throw new RuntimeException("LLM 调用失败，已重试 " + MAX_LLM_RETRIES + " 次: " +
                (lastException != null ? lastException.getMessage() : "未知错误"), lastException);
    }

    /**
     * 判断异常是否可重试（超时、5xx 类服务端错误）
     */
    private boolean isRetryable(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return false;
        }
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        // 超时类
        if (e.getCause() instanceof SocketTimeoutException) return true;
        if (msg.contains("timeout") || msg.contains("timed out")) return true;
        // 5xx 服务端错误
        if (msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504")) return true;
        if (msg.contains("server error") || msg.contains("service unavailable")) return true;
        if (msg.contains("rate limit") || msg.contains("too many requests") || msg.contains("429")) return true;
        return false;
    }

    /**
     * 快速路径：根据用户输入关键词直接预加载匹配的 Skill，减少 LLM 工具调用轮次
     */
    private void tryPreloadSkill(String userInput, WorkingMemory memory) {
        if (userInput == null || userInput.isBlank()) return;

        for (Map.Entry<String, String> entry : SKILL_KEYWORD_MAP.entrySet()) {
            if (userInput.contains(entry.getKey())) {
                String skillName = entry.getValue();
                Optional<SkillDefinition> skillOpt = skillLoader.getSkill(skillName);
                if (skillOpt.isPresent()) {
                    SkillDefinition skill = skillOpt.get();
                    memory.setPreloadedSkill(skill.getName());
                    log.info("⚡ 快速路径命中: '{}' → Skill '{}'", entry.getKey(), skillName);
                    return;
                }
            }
        }
    }

    /**
     * 记录本轮 TraceEntry 并重算 ControlSignals（Step 3）。
     * <p>
     * 仅在内存中追加，不持久化；不改变迭代/门禁逻辑。
     */
    private void recordTraceAndSignals(WorkingMemory memory, int iteration, int scoreBefore, String response) {
        int scoreAfter = memory.getEvalScore();
        EvaluationObservation obs = memory.getLastEvaluationObservation();
        boolean degraded = obs != null && obs.isDegraded();
        List<String> snapshot = new ArrayList<>(memory.getOpenIssues());
        int delta = scoreAfter - scoreBefore;

        String summary;
        if (delta > 0) {
            summary = String.format("score %d→%d (+%d)", scoreBefore, scoreAfter, delta);
        } else if (delta < 0) {
            summary = String.format("score %d→%d (%d)", scoreBefore, scoreAfter, delta);
        } else {
            summary = String.format("score unchanged at %d, %d issues", scoreAfter, snapshot.size());
        }

        TraceEntry entry = new TraceEntry();
        entry.setIteration(iteration);
        entry.setScoreBefore(scoreBefore);
        entry.setScoreAfter(scoreAfter);
        entry.setIssueCount(snapshot.size());
        entry.setResponseLength(response != null ? response.length() : 0);
        entry.setGameVersion(memory.getGameVersion());
        entry.setSummary(summary);
        entry.setEvaluationDegraded(degraded);
        entry.setIssuesSnapshot(snapshot);

        memory.getRunTrace().append(entry);
        memory.setControlSignals(ControlSignals.compute(memory, memory.getRunTrace()));
    }

    private String buildSystemPrompt(WorkingMemory memory) {
        return AgentPrompts.SYSTEM_PROMPT + "\n\n" + contextRenderer.render(memory);
    }

    private String buildUserPrompt(String userInput, WorkingMemory memory) {
        if (memory.getIteration() == 1) {
            return userInput;
        }

        // 修复迭代：把 openIssues + 当前 HTML 摘要传给编排器 LLM
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下问题修复当前游戏（fix_count=")
                .append(toolContext.getFixCount()).append("）：\n");
        for (String issue : memory.getOpenIssues()) {
            sb.append("- ").append(issue).append("\n");
        }

        // 注入当前 HTML，让编排器有修改基础
        if (memory.getGameHtml() != null) {
            sb.append("\n当前游戏 HTML：\n").append(memory.getGameHtml());
        }

        sb.append("\n\n原始需求：").append(userInput);
        return sb.toString();
    }
}

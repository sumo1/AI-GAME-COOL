package com.sumo.agent.agent.tools.generation;

import com.sumo.agent.agent.loop.WorkingMemory;
import com.sumo.agent.agent.tools.ToolContext;
import com.sumo.agent.infra.model.ChatModelRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 游戏修复工具 — 根据评估报告修复游戏 HTML
 */
@Slf4j
@Component
public class GameFixTool {

    @Autowired
    private ChatModelRegistry chatModelRegistry;

    @Autowired
    private ToolContext toolContext;

    @Tool(description = "修复游戏中发现的问题。根据评估报告中的问题列表，对 HTML 游戏代码进行增量修补。如果是第4次及以上修复，则全量重写。")
    public String fixGame(
            @ToolParam(description = "需要修复的问题描述，来自评估报告") String issueDescription) {

        WorkingMemory memory = toolContext.getWorkingMemory();
        int fixCount = toolContext.incrementAndGetFixCount();
        log.info("[fixGame] 修复第 {} 次, 问题: {}", fixCount, issueDescription);

        if (memory == null || memory.getGameHtml() == null) {
            return "错误：没有可修复的游戏 HTML。请先调用 generateGame 生成游戏。";
        }

        ChatModel model = chatModelRegistry.get(null);
        if (model == null) {
            return "错误：未配置可用的 ChatModel";
        }

        String currentHtml = memory.getGameHtml();
        boolean fullRewrite = fixCount >= 4;

        try {
            String systemPrompt = fullRewrite ? FIX_FULL_REWRITE_PROMPT : FIX_INCREMENTAL_PROMPT;

            // Skill 的修复策略已在 SKILL.md "常见问题" 段落中，LLM 在 loadSkill 时已读到
            // 不需要代码层再注入

            String userPrompt;
            if (fullRewrite) {
                userPrompt = "这是第 " + fixCount + " 次修复尝试，之前的增量修补未能解决所有问题，请全量重写。\n\n"
                        + "需要修复的问题：\n" + issueDescription + "\n\n"
                        + "当前有问题的 HTML：\n" + currentHtml;
            } else {
                userPrompt = "请修复以下问题（只修改有问题的部分，保持其他代码不变）：\n\n"
                        + "问题列表：\n" + issueDescription + "\n\n"
                        + "当前 HTML 代码：\n" + currentHtml;
            }

            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            ));

            ChatResponse chatResponse = model.call(prompt);
            if (chatResponse == null || chatResponse.getResult() == null
                    || chatResponse.getResult().getOutput() == null) {
                log.error("[fixGame] LLM 返回空内容");
                return "错误：LLM 返回空内容，保持当前版本不变";
            }

            String fixedHtml = chatResponse.getResult().getOutput().getText();
            if (fixedHtml == null || fixedHtml.isBlank()) {
                log.error("[fixGame] LLM 返回空 HTML");
                return "错误：LLM 返回空 HTML，保持当前版本不变";
            }

            fixedHtml = HtmlCleaner.clean(fixedHtml);

            // 更新 WorkingMemory
            memory.setGameHtml(fixedHtml);
            memory.incrementGameVersion();
            log.info("[fixGame] 修复完成 ({}), 版本: {}, HTML 长度: {}",
                    fullRewrite ? "全量重写" : "增量修补", memory.getGameVersion(), fixedHtml.length());

            return fixedHtml;

        } catch (Exception e) {
            log.error("[fixGame] 修复失败", e);
            String errorType = ErrorClassifier.classify(e);
            return "游戏修复失败 [" + errorType + "]: " + e.getMessage();
        }
    }

    private static final String FIX_INCREMENTAL_PROMPT = """
            你是一个 HTML5 游戏修复专家。请根据问题列表对游戏代码进行增量修补。

            修复原则：
            1. 只修改有问题的部分，不要重写整个游戏
            2. 保持原有的游戏逻辑和视觉风格
            3. 确保修复后的代码仍然是完整可运行的 HTML 文件
            4. 所有样式和脚本内联，不依赖外部资源

            输出格式：只输出修复后的完整 HTML（从 <!DOCTYPE html> 到 </html>），不要包含解释。
            """;

    private static final String FIX_FULL_REWRITE_PROMPT = """
            你是一个 HTML5 游戏开发专家。之前的游戏代码经过多次修补仍有问题，请从零重写。

            重写原则：
            1. 保持原始的游戏设计意图和教育目标
            2. 使用更简洁、健壮的代码结构
            3. 确保所有交互元素都有正确的事件处理
            4. 确保布局响应式，元素不超出可见区域
            5. 必须有明确的游戏开始和结束状态
            6. 必须有计分系统
            7. 所有样式和脚本内联，不依赖外部资源

            输出格式：只输出完整 HTML（从 <!DOCTYPE html> 到 </html>），不要包含解释。
            """;
}

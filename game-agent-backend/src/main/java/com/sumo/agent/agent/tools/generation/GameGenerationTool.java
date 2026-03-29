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
 * 游戏生成工具 — 调用 LLM 生成完整 HTML5 游戏
 */
@Slf4j
@Component
public class GameGenerationTool {

    @Autowired
    private ChatModelRegistry chatModelRegistry;

    @Autowired
    private ToolContext toolContext;

    @Tool(description = "根据游戏设计方案生成一个完整的、可直接运行的 HTML5 教育游戏。输入应包含游戏的详细设计描述，包括玩法、年龄段、教育目标、视觉风格等。")
    public String generateGame(
            @ToolParam(description = "游戏设计方案的详细描述，包含玩法、年龄段、教育目标等") String gameDesign) {
        log.info("[generateGame] 开始生成游戏 ({} 字符描述)", gameDesign.length());

        ChatModel model = chatModelRegistry.get(null);
        if (model == null) {
            return "错误：未配置可用的 ChatModel";
        }

        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(GENERATE_SYSTEM_PROMPT),
                    new UserMessage("请根据以下设计方案生成游戏：\n\n" + gameDesign)
            ));

            ChatResponse chatResponse = model.call(prompt);
            if (chatResponse == null || chatResponse.getResult() == null
                    || chatResponse.getResult().getOutput() == null) {
                log.error("[generateGame] LLM 返回空内容");
                return "错误：LLM 返回空内容，请重试";
            }

            String html = chatResponse.getResult().getOutput().getText();
            if (html == null || html.isBlank()) {
                log.error("[generateGame] LLM 返回空 HTML");
                return "错误：LLM 返回空 HTML 内容，请重试";
            }

            html = HtmlCleaner.clean(html);

            // 更新 WorkingMemory
            WorkingMemory memory = toolContext.getWorkingMemory();
            if (memory != null) {
                memory.setGameHtml(html);
                memory.incrementGameVersion();
                log.info("游戏 HTML 已更新, 版本: {}", memory.getGameVersion());
            }

            log.info("[generateGame] 游戏生成完成, HTML 长度: {}", html.length());
            return html;

        } catch (Exception e) {
            log.error("[generateGame] 生成失败", e);
            String errorType = ErrorClassifier.classify(e);
            return "游戏生成失败 [" + errorType + "]: " + e.getMessage();
        }
    }

    private static final String GENERATE_SYSTEM_PROMPT = """
            你是一个专业的儿童教育游戏开发专家。请根据设计方案生成一个完整的 HTML5 教育小游戏。

            基本要求（必须同时满足）：
            1) 生成单个、可直接运行的完整 HTML 文件（<!DOCTYPE html>…</html>）。
            2) 所有样式与脚本均内联（<style>/<script>），不依赖任何外部资源或 CDN。
            3) 界面清晰、适合儿童，操作简单，同时支持键盘与可点击按钮。
            4) 响应式布局：优先使用百分比/视口单位，游戏主区域在桌面端填充≥90%宽高。
            5) 游戏状态可见：分数/进度需实时展示。
            6) 必须有明确的开始状态和结束状态。
            7) 失败时给鼓励而非惩罚。

            输出格式：只输出最终完整 HTML（从 <!DOCTYPE html> 到 </html>），不要包含 Markdown 代码块或解释文字。
            """;
}

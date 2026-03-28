package com.sumo.agent.v2.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumo.agent.config.ChatModelRouter;
import com.sumo.agent.v2.tool.GameTool;
import com.sumo.agent.v2.tool.ToolProfile;
import com.sumo.agent.v2.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * generate_game 工具 — 调用 LLM 生成完整的 HTML5 游戏
 * <p>
 * 从 UniversalGameAgent 的核心逻辑抽取而来。
 */
@Slf4j
@Component
public class GenerateGameTool implements GameTool {

    private static final ToolProfile PROFILE = new ToolProfile(
            "generate_game",
            "根据游戏设计方案描述，调用大模型生成一个完整的、可直接运行的 HTML5 教育小游戏。" +
                    "输入是 JSON 格式: {\"design\": \"游戏设计描述\", \"skill_template\": \"可选的参考模板HTML\"}。" +
                    "输出是完整的 HTML 代码。",
            """
            {
              "type": "object",
              "properties": {
                "design": { "type": "string", "description": "游戏设计方案的自然语言描述" },
                "skill_template": { "type": "string", "description": "可选的 Skill 模板 HTML 作为参考" }
              },
              "required": ["design"]
            }
            """
    );

    @Autowired
    private ChatModelRouter chatModelRouter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ToolProfile getProfile() {
        return PROFILE;
    }

    @Override
    public ToolResult execute(String input) {
        try {
            JsonNode node = objectMapper.readTree(input);
            String design = node.has("design") ? node.get("design").asText() : input;
            String skillTemplate = node.has("skill_template") ? node.get("skill_template").asText() : null;

            ChatModel chatModel = chatModelRouter.get(null); // 使用默认模型
            if (chatModel == null) {
                return ToolResult.failure("未配置可用的 ChatModel");
            }

            String systemPrompt = buildSystemPrompt(skillTemplate);
            String userPrompt = buildUserPrompt(design);

            log.info("🎮 generate_game: 调用 LLM 生成游戏...");
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            ));

            String gameHtml = chatModel.call(prompt).getResult().getOutput().getText();
            gameHtml = cleanAndValidateHtml(gameHtml);

            log.info("🎮 generate_game: 游戏生成完成, HTML 长度: {}", gameHtml.length());
            return ToolResult.success(gameHtml);

        } catch (Exception e) {
            log.error("generate_game 执行失败", e);
            return ToolResult.failure("游戏生成失败: " + e.getMessage());
        }
    }

    private String buildSystemPrompt(String skillTemplate) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
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
                """);

        if (skillTemplate != null && !skillTemplate.isBlank()) {
            sb.append("\n\n参考模板（请在此基础上根据具体需求调整）：\n");
            // 截断过长的模板，只保留结构参考
            if (skillTemplate.length() > 3000) {
                sb.append(skillTemplate, 0, 3000);
                sb.append("\n... [模板已截断，请参考上述结构和交互模式]");
            } else {
                sb.append(skillTemplate);
            }
        }

        return sb.toString();
    }

    private String buildUserPrompt(String design) {
        return "请根据以下设计方案生成游戏：\n\n" + design;
    }

    private String cleanAndValidateHtml(String html) {
        html = html.replaceAll("```html\\s*", "");
        html = html.replaceAll("```\\s*$", "");
        html = html.trim();

        if (!html.startsWith("<!DOCTYPE html>") && !html.startsWith("<html")) {
            html = "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n" + html;
        }
        if (!html.endsWith("</html>")) {
            html = html + "\n</html>";
        }
        if (!html.contains("charset")) {
            html = html.replace("<head>", "<head>\n    <meta charset=\"UTF-8\">");
        }

        return html;
    }
}

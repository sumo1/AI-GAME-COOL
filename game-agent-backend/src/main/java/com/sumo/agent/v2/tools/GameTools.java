package com.sumo.agent.v2.tools;

import com.sumo.agent.v2.skill.SkillDefinition;
import com.sumo.agent.v2.skill.SkillLoader;
import com.sumo.agent.config.ChatModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Game Agent 的工具集 — 通过 Spring AI @Tool 注解注册为 Function Calling 工具
 * <p>
 * Spring AI 自动将 @Tool 方法暴露给 LLM，LLM 决定何时调用哪个工具。
 */
@Slf4j
@Component
public class GameTools {

    @Autowired
    private SkillLoader skillLoader;

    @Autowired
    private ChatModelRouter chatModelRouter;

    /**
     * 列出可用的游戏技能模板
     */
    @Tool(description = "列出可用的游戏技能模板。返回所有内置游戏 Skill 的名称、描述、适合年龄段和标签。可按关键词过滤。")
    public String listSkills(@ToolParam(description = "可选的过滤关键词，如 '数学' '英语' '4-6岁'，留空返回全部") String filter) {
        log.info("🔧 [listSkills] filter={}", filter);

        List<SkillDefinition> skills = skillLoader.loadAll();

        if (filter != null && !filter.isBlank()) {
            String f = filter.toLowerCase();
            skills = skills.stream()
                    .filter(s -> s.getName().toLowerCase().contains(f)
                            || s.getDisplayName().toLowerCase().contains(f)
                            || s.getDescription().toLowerCase().contains(f)
                            || s.getTags().stream().anyMatch(t -> t.toLowerCase().contains(f))
                            || s.getAgeGroup().contains(f))
                    .collect(Collectors.toList());
        }

        if (skills.isEmpty()) {
            return "没有找到匹配的 Skill。可用的游戏类型包括：数学、记忆、英语、交通安全、形状颜色、逻辑推理。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(skills.size()).append(" 个可用 Skill：\n\n");
        for (SkillDefinition skill : skills) {
            sb.append("- **").append(skill.getDisplayName()).append("** (").append(skill.getName()).append(")\n");
            sb.append("  描述: ").append(skill.getDescription()).append("\n");
            sb.append("  年龄段: ").append(skill.getAgeGroup()).append("\n");
            sb.append("  标签: ").append(String.join(", ", skill.getTags())).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 加载指定的游戏技能模板
     */
    @Tool(description = "加载指定名称的游戏技能模板，获取完整的 HTML 模板代码、生成提示词和评估标准。用于参考已有模板来生成新游戏。")
    public String loadSkill(@ToolParam(description = "Skill 名称，如 math_adventure, memory_master, english_explorer 等") String skillName) {
        log.info("🔧 [loadSkill] name={}", skillName);

        SkillDefinition skill = skillLoader.load(skillName);
        if (skill == null) {
            return "未找到 Skill: " + skillName + "。请调用 listSkills 查看可用的 Skill 列表。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Skill: ").append(skill.getDisplayName()).append("\n\n");
        sb.append("**描述**: ").append(skill.getDescription()).append("\n");
        sb.append("**年龄段**: ").append(skill.getAgeGroup()).append("\n");
        sb.append("**标签**: ").append(String.join(", ", skill.getTags())).append("\n\n");

        if (skill.getPromptHint() != null) {
            sb.append("## 生成提示\n").append(skill.getPromptHint()).append("\n\n");
        }

        if (skill.getEvaluationCriteria() != null && !skill.getEvaluationCriteria().isEmpty()) {
            sb.append("## 评估标准\n");
            for (String c : skill.getEvaluationCriteria()) {
                sb.append("- ").append(c).append("\n");
            }
            sb.append("\n");
        }

        if (skill.getTemplate() != null) {
            sb.append("## HTML 模板（参考）\n```html\n").append(skill.getTemplate()).append("\n```\n");
        }

        return sb.toString();
    }

    /**
     * 生成完整的 HTML5 教育游戏
     */
    @Tool(description = "根据游戏设计方案生成一个完整的、可直接运行的 HTML5 教育游戏。输入应包含游戏的详细设计描述，包括玩法、年龄段、教育目标、视觉风格等。")
    public String generateGame(@ToolParam(description = "游戏设计方案的详细描述，包含玩法、年龄段、教育目标等") String gameDesign) {
        log.info("🔧 [generateGame] 开始生成游戏 ({} 字符描述)", gameDesign.length());

        ChatModel model = chatModelRouter.get(null); // 使用默认模型
        if (model == null) {
            return "错误：未配置可用的 ChatModel";
        }

        String systemPrompt = buildGenerateSystemPrompt();
        String userPrompt = "请生成一个游戏，设计方案如下：\n" + gameDesign;

        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            ));
            String html = model.call(prompt).getResult().getOutput().getText();
            html = cleanHtml(html);
            log.info("✅ [generateGame] 游戏生成完成 ({} 字符)", html.length());
            return html;
        } catch (Exception e) {
            log.error("❌ [generateGame] 生成失败", e);
            return "游戏生成失败: " + e.getMessage();
        }
    }

    private String buildGenerateSystemPrompt() {
        return """
                你是一个专业的儿童教育游戏开发专家。请根据设计方案生成一个完整的 HTML5 教育小游戏。

                要求：
                1) 单个完整 HTML 文件（内联 CSS/JS），不依赖外部资源
                2) 界面清晰适合儿童，同时支持键盘和点击操作
                3) 响应式布局，适配不同屏幕
                4) 有明确的开始/结束、计分/进度反馈
                5) 失败时给鼓励而非惩罚

                只输出最终 HTML（从 <!DOCTYPE html> 到 </html>），不要解释。
                """;
    }

    private String cleanHtml(String html) {
        if (html == null) return "";
        html = html.replaceAll("```html\\s*", "");
        html = html.replaceAll("```\\s*$", "");
        html = html.trim();
        if (!html.startsWith("<!DOCTYPE html>") && !html.startsWith("<html")) {
            html = "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n" + html;
        }
        if (!html.endsWith("</html>")) {
            html = html + "\n</html>";
        }
        return html;
    }
}

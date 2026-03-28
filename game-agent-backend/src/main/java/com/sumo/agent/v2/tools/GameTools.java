package com.sumo.agent.v2.tools;

import com.sumo.agent.config.ChatModelRouter;
import com.sumo.agent.v2.loop.WorkingMemory;
import com.sumo.agent.v2.skill.SkillDefinition;
import com.sumo.agent.v2.skill.SkillLoader;
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
import java.util.Optional;

/**
 * 游戏工具集 — 使用 Spring AI @Tool 注解，支持原生 Function Calling
 * <p>
 * 包含三个工具：listSkills / loadSkill / generateGame
 * Spring AI 自动将 @Tool 方法暴露给 LLM，LLM 决定何时调用哪个工具。
 * <p>
 * 每次 AgentLoop 运行时，通过 setWorkingMemory() 注入当前迭代的 WorkingMemory，
 * generateGame 执行成功后自动更新 WorkingMemory 中的游戏状态。
 */
@Slf4j
@Component
public class GameTools {

    @Autowired
    private SkillLoader skillLoader;

    @Autowired
    private ChatModelRouter chatModelRouter;

    /** 当前迭代的 WorkingMemory，由 AgentLoop 在每次 run() 时设置 */
    private WorkingMemory workingMemory;

    public void setWorkingMemory(WorkingMemory memory) {
        this.workingMemory = memory;
    }

    public WorkingMemory getWorkingMemory() {
        return this.workingMemory;
    }

    @Tool(description = "列出可用的游戏技能模板。返回所有内置游戏 Skill 的名称、描述、适合年龄段和标签。可按关键词过滤。")
    public String listSkills(
            @ToolParam(description = "可选的过滤关键词，如 '数学' '英语' '4-6岁'，留空返回全部", required = false) String filter) {
        log.info("[listSkills] filter={}", filter);

        try {
            List<SkillDefinition> skills = skillLoader.listSkills(filter);

            if (skills.isEmpty()) {
                return "没有找到匹配的 Skill。可用的游戏类型包括：数学、记忆、英语、交通安全、形状颜色、逻辑推理。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(skills.size()).append(" 个可用 Skill：\n\n");
            for (SkillDefinition skill : skills) {
                sb.append("- **").append(skill.getDisplayName()).append("** (").append(skill.getName()).append(")\n");
                sb.append("  描述: ").append(skill.getDescription()).append("\n");
                sb.append("  年龄段: ").append(skill.getAgeGroup()).append("\n");
                if (skill.getTags() != null) {
                    sb.append("  标签: ").append(String.join(", ", skill.getTags())).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();

        } catch (Exception e) {
            log.error("list_skills 执行失败", e);
            return "列出 Skill 失败: " + e.getMessage();
        }
    }

    @Tool(description = "加载指定名称的游戏技能模板，获取完整的 HTML 模板代码、生成提示词和评估标准。用于参考已有模板来生成新游戏。")
    public String loadSkill(
            @ToolParam(description = "Skill 名称，如 math_adventure, memory_master 等") String skillName) {
        log.info("[loadSkill] name={}", skillName);

        try {
            if (skillName == null || skillName.isBlank()) {
                return "请提供 skillName 参数";
            }

            Optional<SkillDefinition> opt = skillLoader.getSkill(skillName);
            if (opt.isEmpty()) {
                return "未找到 Skill: " + skillName + "。请调用 listSkills 查看可用的 Skill 列表。";
            }

            SkillDefinition skill = opt.get();
            StringBuilder sb = new StringBuilder();
            sb.append("# Skill: ").append(skill.getDisplayName()).append("\n\n");
            sb.append("**描述**: ").append(skill.getDescription()).append("\n");
            sb.append("**年龄段**: ").append(skill.getAgeGroup()).append("\n");
            sb.append("**游戏类型**: ").append(skill.getGameType()).append("\n\n");

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
                sb.append("## HTML 模板\n```html\n").append(skill.getTemplate()).append("\n```\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("load_skill 执行失败", e);
            return "加载 Skill 失败: " + e.getMessage();
        }
    }

    @Tool(description = "根据游戏设计方案生成一个完整的、可直接运行的 HTML5 教育游戏。输入应包含游戏的详细设计描述，包括玩法、年龄段、教育目标、视觉风格等。")
    public String generateGame(
            @ToolParam(description = "游戏设计方案的详细描述，包含玩法、年龄段、教育目标等") String gameDesign) {
        log.info("[generateGame] 开始生成游戏 ({} 字符描述)", gameDesign.length());

        ChatModel model = chatModelRouter.get(null);
        if (model == null) {
            return "错误：未配置可用的 ChatModel";
        }

        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(GENERATE_SYSTEM_PROMPT),
                    new UserMessage("请根据以下设计方案生成游戏：\n\n" + gameDesign)
            ));

            String html = model.call(prompt).getResult().getOutput().getText();
            html = cleanHtml(html);

            // 更新 WorkingMemory
            if (workingMemory != null) {
                workingMemory.setGameHtml(html);
                workingMemory.incrementGameVersion();
                log.info("游戏 HTML 已更新, 版本: {}", workingMemory.getGameVersion());
            }

            log.info("[generateGame] 游戏生成完成, HTML 长度: {}", html.length());
            return html;

        } catch (Exception e) {
            log.error("[generateGame] 生成失败", e);
            return "游戏生成失败: " + e.getMessage();
        }
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
        if (!html.contains("charset")) {
            html = html.replace("<head>", "<head>\n    <meta charset=\"UTF-8\">");
        }
        return html;
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

package com.sumo.agent.v2.tools;

import com.sumo.agent.config.ChatModelRouter;
import com.sumo.agent.v2.evaluate.GameEvaluator;
import com.sumo.agent.v2.evaluate.ProbeReport;
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

import java.util.ArrayList;
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

    @Autowired
    private GameEvaluator gameEvaluator;

    /** 累计修复次数，第 4 次起全量重写 */
    private int fixCount = 0;

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

    @Tool(description = "评估生成的 HTML5 游戏的可玩性和质量。使用 headless 浏览器渲染游戏，模拟操作，检测 JS 错误、元素越界、交互响应性等。返回结构化的评估报告和各维度评分。")
    public String evaluateGame(
            @ToolParam(description = "要评估的完整 HTML 游戏代码") String htmlCode) {
        log.info("[evaluateGame] 开始评估游戏 ({} 字符)", htmlCode.length());

        try {
            ProbeReport report = gameEvaluator.evaluate(htmlCode);

            // 更新 WorkingMemory
            if (workingMemory != null) {
                workingMemory.setEvalScore(report.getTotalScore());
                List<String> openIssues = workingMemory.getOpenIssues();
                openIssues.clear();
                if (report.getIssues() != null) {
                    openIssues.addAll(report.getIssues());
                }
                workingMemory.setIssueCount(openIssues.size());
                log.info("WorkingMemory 已更新: evalScore={}, issues={}", report.getTotalScore(), openIssues.size());
            }

            // 构建评估报告文本
            return buildEvalReportText(report);

        } catch (Exception e) {
            log.error("[evaluateGame] 评估失败", e);
            return "游戏评估失败: " + e.getMessage();
        }
    }

    private String buildEvalReportText(ProbeReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 游戏评估报告\n\n");
        sb.append("**总分: ").append(report.getTotalScore()).append("/100**\n\n");
        sb.append("| 维度 | 得分 |\n|------|------|\n");
        sb.append("| 可运行性 | ").append(report.getRunnabilityScore()).append("/20 |\n");
        sb.append("| 布局正确性 | ").append(report.getLayoutScore()).append("/20 |\n");
        sb.append("| 交互响应性 | ").append(report.getInteractivityScore()).append("/20 |\n");
        sb.append("| 游戏完整性 | ").append(report.getCompletenessScore()).append("/20 |\n");
        sb.append("| 教育匹配度 | ").append(report.getEducationScore()).append("/20 |\n\n");

        if (report.getIssues() != null && !report.getIssues().isEmpty()) {
            sb.append("### 发现的问题\n");
            for (String issue : report.getIssues()) {
                sb.append("- ").append(issue).append("\n");
            }
            sb.append("\n");
        }

        // 附加 Probe 摘要信息
        sb.append("### Probe 数据摘要\n");
        sb.append("- 页面加载: ").append(report.isPageLoaded() ? "成功" : "失败").append("\n");
        sb.append("- JS 错误数: ").append(report.getErrors() != null ? report.getErrors().size() : 0).append("\n");
        sb.append("- 交互事件数: ").append(report.getEvents() != null ? report.getEvents().size() : 0).append("\n");
        sb.append("- DOM 变化次数: ").append(report.getDomMutationsCount()).append("\n");
        sb.append("- 状态转换: ").append(report.getStateTransitions() != null ? report.getStateTransitions() : "无").append("\n");
        sb.append("- 越界元素数: ").append(report.getOutOfBoundsElements() != null ? report.getOutOfBoundsElements().size() : 0).append("\n");

        if (report.getFinalState() != null) {
            sb.append("- 最终分数: ").append(report.getFinalState().getScore()).append("\n");
        }

        return sb.toString();
    }

    @Tool(description = "修复游戏中发现的问题。根据评估报告中的问题列表，对 HTML 游戏代码进行增量修补。如果是第4次及以上修复，则全量重写。")
    public String fixGame(
            @ToolParam(description = "需要修复的问题描述，来自评估报告") String issueDescription) {
        log.info("[fixGame] 修复第 {} 次, 问题: {}", fixCount + 1, issueDescription);

        if (workingMemory == null || workingMemory.getGameHtml() == null) {
            return "错误：没有可修复的游戏 HTML。请先调用 generateGame 生成游戏。";
        }

        ChatModel model = chatModelRouter.get(null);
        if (model == null) {
            return "错误：未配置可用的 ChatModel";
        }

        fixCount++;
        String currentHtml = workingMemory.getGameHtml();
        boolean fullRewrite = fixCount >= 4;

        try {
            String systemPrompt = fullRewrite ? FIX_FULL_REWRITE_PROMPT : FIX_INCREMENTAL_PROMPT;
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

            String fixedHtml = model.call(prompt).getResult().getOutput().getText();
            fixedHtml = cleanHtml(fixedHtml);

            // 更新 WorkingMemory
            workingMemory.setGameHtml(fixedHtml);
            workingMemory.incrementGameVersion();
            log.info("[fixGame] 修复完成 ({}), 版本: {}, HTML 长度: {}",
                    fullRewrite ? "全量重写" : "增量修补", workingMemory.getGameVersion(), fixedHtml.length());

            return fixedHtml;

        } catch (Exception e) {
            log.error("[fixGame] 修复失败", e);
            return "游戏修复失败: " + e.getMessage();
        }
    }

    /** 重置修复计数（每次新的 AgentLoop 运行时调用） */
    public void resetFixCount() {
        this.fixCount = 0;
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

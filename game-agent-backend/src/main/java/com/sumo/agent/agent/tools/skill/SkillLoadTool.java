package com.sumo.agent.agent.tools.skill;

import com.sumo.agent.agent.skill.Skill;
import com.sumo.agent.agent.skill.SkillDefinition;
import com.sumo.agent.agent.skill.SkillLoader;
import com.sumo.agent.agent.tools.ToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Skill 加载工具 — 加载指定名称的游戏技能模板，同时激活 Skill 策略。
 * <p>
 * 加载成功后将 Skill 实例设置到 ToolContext.activeSkill，
 * 后续的 generateGame / evaluateGame / fixGame 会自动使用该 Skill 的策略。
 */
@Slf4j
@Component
public class SkillLoadTool {

    @Autowired
    private SkillLoader skillLoader;

    @Autowired
    private ToolContext toolContext;

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

            // 激活 Skill 策略实例
            Optional<Skill> skillOpt = skillLoader.getSkillInstance(skillName);
            skillOpt.ifPresent(skill -> {
                toolContext.setActiveSkill(skill);
                log.info("[loadSkill] 已激活 Skill 策略: {} ({}项检查, {}项修复提示)",
                        skillName, skill.getEvaluationChecks().size(), skill.getFixHints().size());
            });

            SkillDefinition def = opt.get();
            StringBuilder sb = new StringBuilder();

            // 返回 SKILL.md 操作手册（Agent 读这个理解怎么做）
            sb.append(def.getInstructions());

            // 附加 HTML 模板（如果有）
            if (def.getTemplate() != null) {
                sb.append("\n\n## HTML 模板\n```html\n").append(def.getTemplate()).append("\n```\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("load_skill 执行失败", e);
            return "加载 Skill 失败: " + e.getMessage();
        }
    }
}

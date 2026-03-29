package com.sumo.agent.agent.tools.skill;

import com.sumo.agent.agent.skill.SkillDefinition;
import com.sumo.agent.agent.skill.SkillLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Skill 加载工具 — 加载指定名称的游戏技能模板
 */
@Slf4j
@Component
public class SkillLoadTool {

    @Autowired
    private SkillLoader skillLoader;

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
}

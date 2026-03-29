package com.sumo.agent.agent.tools.skill;

import com.sumo.agent.agent.skill.SkillDefinition;
import com.sumo.agent.agent.skill.SkillLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Skill 列表工具 — 列出可用的游戏技能模板
 */
@Slf4j
@Component
public class SkillListTool {

    @Autowired
    private SkillLoader skillLoader;

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
}

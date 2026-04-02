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
 * Skill 列表工具 — 列出可用的游戏 Skill（仅暴露 name + description，渐进式披露）
 */
@Slf4j
@Component
public class SkillListTool {

    @Autowired
    private SkillLoader skillLoader;

    @Tool(description = "列出可用的游戏技能。返回所有内置游戏 Skill 的名称和描述。可按关键词过滤。使用 loadSkill 获取详细操作手册。")
    public String listSkills(
            @ToolParam(description = "可选的过滤关键词，如 '数学' '英语' '记忆'，留空返回全部", required = false) String filter) {
        log.info("[listSkills] filter={}", filter);

        try {
            List<SkillDefinition> skills = skillLoader.listSkills(filter);

            if (skills.isEmpty()) {
                return "没有找到匹配的 Skill。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(skills.size()).append(" 个可用 Skill：\n\n");

            for (SkillDefinition skill : skills) {
                sb.append(skill.toSummary()).append("\n");
            }

            sb.append("\n使用 loadSkill(name) 加载详细操作手册。");
            return sb.toString();

        } catch (Exception e) {
            log.error("list_skills 执行失败", e);
            return "列出 Skill 失败: " + e.getMessage();
        }
    }
}

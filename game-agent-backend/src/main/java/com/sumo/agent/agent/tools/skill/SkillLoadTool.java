package com.sumo.agent.agent.tools.skill;

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
 * Skill 加载工具 — 加载指定名称的 Skill，返回操作手册供 LLM 阅读。
 * <p>
 * 对齐 AgentSkills.io 渐进式披露：
 * Discovery（listSkills）→ Activation（loadSkill）→ Execution（LLM 按手册操作）
 */
@Slf4j
@Component
public class SkillLoadTool {

    @Autowired
    private SkillLoader skillLoader;

    @Autowired
    private ToolContext toolContext;

    @Tool(description = "加载指定名称的游戏技能，获取完整的操作手册和 HTML 参考模板。LLM 阅读操作手册后即可理解如何生成、评估和修复该类游戏。")
    public String loadSkill(
            @ToolParam(description = "Skill 名称，如 math-adventure, memory-master 等") String skillName) {
        log.info("[loadSkill] name={}", skillName);

        try {
            if (skillName == null || skillName.isBlank()) {
                return "请提供 skillName 参数";
            }

            Optional<SkillDefinition> opt = skillLoader.getSkill(skillName);
            if (opt.isEmpty()) {
                return "未找到 Skill: " + skillName + "。请调用 listSkills 查看可用的 Skill 列表。";
            }

            SkillDefinition def = opt.get();

            // 设置为当前激活的 Skill
            toolContext.setActiveSkill(def);
            log.info("[loadSkill] 已激活 Skill: {}", skillName);

            StringBuilder sb = new StringBuilder();

            // 返回 SKILL.md 操作手册（LLM 读这个理解怎么做）
            sb.append(def.getInstructions());

            // 附加 HTML 模板（如果有）
            if (def.getTemplate() != null) {
                sb.append("\n\n## HTML 参考模板\n```html\n").append(def.getTemplate()).append("\n```\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("load_skill 执行失败", e);
            return "加载 Skill 失败: " + e.getMessage();
        }
    }
}

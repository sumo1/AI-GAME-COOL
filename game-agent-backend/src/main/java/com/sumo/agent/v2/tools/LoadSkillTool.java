package com.sumo.agent.v2.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumo.agent.v2.skill.SkillDefinition;
import com.sumo.agent.v2.skill.SkillLoader;
import com.sumo.agent.v2.tool.GameTool;
import com.sumo.agent.v2.tool.ToolProfile;
import com.sumo.agent.v2.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * load_skill 工具 — 加载指定 Skill 的完整内容（模板 + 提示词 + 评估标准）
 */
@Slf4j
@Component
public class LoadSkillTool implements GameTool {

    private static final ToolProfile PROFILE = new ToolProfile(
            "load_skill",
            "加载指定名称的游戏技能模板（Skill）的完整内容，包括 HTML 模板、生成提示和评估标准。" +
                    "输入是 JSON 格式: {\"skill_name\": \"技能模板名称\"}。" +
                    "输出包含完整的模板代码和提示信息。",
            """
            {
              "type": "object",
              "properties": {
                "skill_name": { "type": "string", "description": "要加载的 Skill 名称，如 math_adventure" }
              },
              "required": ["skill_name"]
            }
            """
    );

    @Autowired
    private SkillLoader skillLoader;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ToolProfile getProfile() {
        return PROFILE;
    }

    @Override
    public ToolResult execute(String input) {
        try {
            String skillName = extractSkillName(input);
            if (skillName == null || skillName.isBlank()) {
                return ToolResult.failure("请提供 skill_name 参数");
            }

            Optional<SkillDefinition> opt = skillLoader.getSkill(skillName);
            if (opt.isEmpty()) {
                return ToolResult.failure("未找到 Skill: " + skillName);
            }

            SkillDefinition skill = opt.get();

            // 构建完整的 Skill 信息返回给 LLM
            StringBuilder sb = new StringBuilder();
            sb.append("# Skill: ").append(skill.getDisplayName()).append("\n\n");
            sb.append("**描述**: ").append(skill.getDescription()).append("\n");
            sb.append("**年龄段**: ").append(skill.getAgeGroup()).append("\n");
            sb.append("**游戏类型**: ").append(skill.getGameType()).append("\n\n");

            if (skill.getPromptHint() != null) {
                sb.append("## 生成提示\n").append(skill.getPromptHint()).append("\n\n");
            }

            if (skill.getEvaluationCriteria() != null) {
                sb.append("## 评估标准\n");
                for (String criteria : skill.getEvaluationCriteria()) {
                    sb.append("- ").append(criteria).append("\n");
                }
                sb.append("\n");
            }

            if (skill.getTemplate() != null) {
                sb.append("## HTML 模板\n```html\n").append(skill.getTemplate()).append("\n```\n");
            }

            log.info("load_skill: 加载 Skill '{}'", skillName);
            return ToolResult.success(sb.toString());

        } catch (Exception e) {
            log.error("load_skill 执行失败", e);
            return ToolResult.failure("加载 Skill 失败: " + e.getMessage());
        }
    }

    private String extractSkillName(String input) {
        if (input == null || input.isBlank()) return null;

        try {
            JsonNode node = objectMapper.readTree(input);
            if (node.has("skill_name")) {
                return node.get("skill_name").asText();
            }
            if (node.has("name")) {
                return node.get("name").asText();
            }
        } catch (Exception e) {
            // input 可能是纯文本名称
        }

        return input.trim();
    }
}

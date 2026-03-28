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

import java.util.List;

/**
 * list_skills 工具 — 列出可用的游戏技能模板
 */
@Slf4j
@Component
public class ListSkillsTool implements GameTool {

    private static final ToolProfile PROFILE = new ToolProfile(
            "list_skills",
            "列出所有可用的游戏技能模板（Skill）。可按关键词过滤。" +
                    "输入是 JSON 格式: {\"filter\": \"可选的过滤关键词\"}。" +
                    "输出是匹配的 Skill 摘要列表。",
            """
            {
              "type": "object",
              "properties": {
                "filter": { "type": "string", "description": "可选的过滤关键词，匹配名称/描述/标签" }
              }
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
            String filter = null;
            if (input != null && !input.isBlank()) {
                try {
                    JsonNode node = objectMapper.readTree(input);
                    if (node.has("filter")) {
                        filter = node.get("filter").asText();
                    }
                } catch (Exception e) {
                    // input 可能就是纯文本 filter
                    filter = input.trim();
                }
            }

            List<SkillDefinition> skills = skillLoader.listSkills(filter);

            if (skills.isEmpty()) {
                return ToolResult.success("{\"skills\":[], \"message\":\"没有找到匹配的 Skill\"}");
            }

            StringBuilder sb = new StringBuilder("{\"skills\":[");
            for (int i = 0; i < skills.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(skills.get(i).toSummary());
            }
            sb.append("], \"total\":").append(skills.size()).append("}");

            log.info("list_skills: 返回 {} 个 Skill", skills.size());
            return ToolResult.success(sb.toString());

        } catch (Exception e) {
            log.error("list_skills 执行失败", e);
            return ToolResult.failure("列出 Skill 失败: " + e.getMessage());
        }
    }
}

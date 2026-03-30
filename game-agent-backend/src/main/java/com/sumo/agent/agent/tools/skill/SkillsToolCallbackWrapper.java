package com.sumo.agent.agent.tools.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumo.agent.agent.skill.SkillLoader;
import com.sumo.agent.agent.tools.ToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * SkillsTool 回调包装器 — 拦截 SkillsTool 的加载调用，同时设置 ToolContext。
 * <p>
 * 当 LLM 通过 SkillsTool 加载某个 Skill 时，此包装器：
 * 1. 委托给内部的 SkillsTool 回调获取 skill 内容
 * 2. 从调用参数中提取 skill 名称
 * 3. 通过 SkillLoader 查找对应的 SkillDefinition（含 gameType 等元数据）
 * 4. 设置到 ToolContext.activeSkillDefinition
 */
@Slf4j
public class SkillsToolCallbackWrapper implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolContext toolContext;
    private final SkillLoader skillLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SkillsToolCallbackWrapper(ToolCallback delegate, ToolContext toolContext, SkillLoader skillLoader) {
        this.delegate = delegate;
        this.toolContext = toolContext;
        this.skillLoader = skillLoader;
    }

    @Override
    public String call(String toolInput) {
        String result = delegate.call(toolInput);

        // 拦截：从 toolInput 中提取 skill 名称，设置 ToolContext
        try {
            JsonNode node = objectMapper.readTree(toolInput);
            String skillName = node.has("command") ? node.get("command").asText() : null;

            if (skillName != null && !skillName.isBlank()) {
                skillLoader.getSkill(skillName).ifPresent(def -> {
                    toolContext.setActiveSkillDefinition(def);
                    log.info("[SkillsToolWrapper] 已激活 Skill: {} (gameType={})",
                            skillName, def.getGameType());
                });
            }
        } catch (Exception e) {
            log.debug("解析 SkillsTool 输入失败（不影响功能）: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }
}

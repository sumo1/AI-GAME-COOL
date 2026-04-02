package com.sumo.agent.agent.skill;

import java.util.Map;

/**
 * Skill 定义 — 对齐 AgentSkills.io 规范。
 * <p>
 * 规范要求 frontmatter 只有两个必填字段：name + description。
 * 其余领域信息（ageGroup、gameType、tags 等）放在 metadata map 中，
 * 或由 LLM 从 SKILL.md body 自行理解。
 *
 * @see <a href="https://agentskills.io/specification">AgentSkills.io Specification</a>
 */
public class SkillDefinition {

    // === AgentSkills.io 规范 Required 字段 ===

    /** 技能标识，小写字母+数字+连字符（如 math-adventure） */
    private String name;

    /** 技能描述：做什么、什么时候用 */
    private String description;

    // === AgentSkills.io 规范 Optional 字段 ===

    /** 任意 key-value 元数据（author、version、ageGroup、gameType、tags 等都放这里） */
    private Map<String, Object> metadata;

    // === 运行时字段（SkillLoader 解析后填充） ===

    /** SKILL.md 完整 body（Markdown 操作手册），原样传给 LLM */
    private String instructions;

    /** assets/template.html 参考模板（可选） */
    private String template;

    // --- getters / setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }

    /**
     * 返回发现阶段的摘要（~100 tokens），供 listSkills 使用。
     * 仅暴露 name + description，实现渐进式披露。
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("- **").append(name).append("**: ").append(description);

        // 从 metadata 中提取对用户有用的补充信息
        if (metadata != null) {
            Object ageGroup = metadata.get("ageGroup");
            if (ageGroup != null) {
                sb.append(" (").append(ageGroup).append("岁)");
            }
        }

        return sb.toString();
    }
}

package com.sumo.agent.agent.skill;

import java.util.List;

/**
 * Skill 定义 — 对应 YAML 文件中的技能描述
 */
public class SkillDefinition {

    private String name;
    private String displayName;
    private String description;
    private String ageGroup;
    private List<String> difficulty;
    private List<String> tags;
    private String gameType;
    private String template;
    private String promptHint;
    private List<String> evaluationCriteria;
    private List<FixHint> fixHints;

    /** SKILL.md 完整 body（Markdown 操作手册），SkillLoader 解析后设置 */
    private String instructions;

    // --- getters / setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAgeGroup() { return ageGroup; }
    public void setAgeGroup(String ageGroup) { this.ageGroup = ageGroup; }

    public List<String> getDifficulty() { return difficulty; }
    public void setDifficulty(List<String> difficulty) { this.difficulty = difficulty; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }

    public String getPromptHint() { return promptHint; }
    public void setPromptHint(String promptHint) { this.promptHint = promptHint; }

    public List<String> getEvaluationCriteria() { return evaluationCriteria; }
    public void setEvaluationCriteria(List<String> evaluationCriteria) { this.evaluationCriteria = evaluationCriteria; }

    public List<FixHint> getFixHints() { return fixHints; }
    public void setFixHints(List<FixHint> fixHints) { this.fixHints = fixHints; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    /**
     * 返回摘要信息（供 list_skills 使用）
     */
    public String toSummary() {
        return String.format(
                "{\"name\":\"%s\",\"display_name\":\"%s\",\"description\":\"%s\",\"age_group\":\"%s\",\"game_type\":\"%s\",\"tags\":%s}",
                name, displayName, description, ageGroup, gameType, tags
        );
    }
}

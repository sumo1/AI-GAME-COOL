package com.sumo.agent.agent.skill;

/**
 * 默认 Skill 实现 — 包装 SkillDefinition。
 * <p>
 * SKILL.md body 原样传给 LLM，不做文本解析。
 * 代码级评估检查已移至 GameEvaluator（根据 gameType 自动派生）。
 */
public class DefaultSkill implements Skill {

    private final SkillDefinition definition;

    public DefaultSkill(SkillDefinition definition) {
        this.definition = definition;
    }

    @Override
    public SkillDefinition getDefinition() {
        return definition;
    }

    /**
     * 生成引导 = SKILL.md 的完整操作手册。LLM 读这个理解怎么做。
     */
    @Override
    public String getGenerationGuidance() {
        return definition.getInstructions() != null ? definition.getInstructions() : "";
    }
}

package com.sumo.agent.agent.skill;

/**
 * Skill — 知识包，封装"某类游戏"的操作手册和元数据。
 * <p>
 * V2 简化：Skill 只提供定义和生成引导（SKILL.md body）。
 * 代码级评估检查由 GameEvaluator 根据 gameType 自动派生。
 */
public interface Skill {

    /** 底层 SKILL.md 数据（frontmatter 元数据 + body 操作手册） */
    SkillDefinition getDefinition();

    /**
     * 生成引导：返回 SKILL.md 完整 body（操作手册）。
     * LLM 读这个理解怎么生成、评估、修复。
     */
    String getGenerationGuidance();
}

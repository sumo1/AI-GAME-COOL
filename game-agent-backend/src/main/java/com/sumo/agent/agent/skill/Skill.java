package com.sumo.agent.agent.skill;

import java.util.List;

/**
 * Skill — 可执行的策略单元，不是 prompt 素材。
 * <p>
 * 封装"如何生成/评估/修复某类游戏"的完整策略：
 * <ul>
 *   <li>{@link #getGenerationGuidance()} — 指导生成过程（不只是 prompt hint）</li>
 *   <li>{@link #getEvaluationChecks()} — 可执行的代码级检查（不只是文本标准）</li>
 *   <li>{@link #getFixHints()} — 结构化修复策略（症状 → 方案映射）</li>
 * </ul>
 */
public interface Skill {

    /** 底层 YAML 数据 */
    SkillDefinition getDefinition();

    /**
     * 生成引导：返回增强后的生成指令。
     * 不是简单返回 promptHint 文本，而是可以包含 Skill 特有的约束和要求。
     */
    String getGenerationGuidance();

    /**
     * 评估检查：返回可执行的检查列表。
     * 每个 check 是代码级的——能检查 HTML 结构、Probe 数据，产出具体 issue。
     */
    List<EvaluationCheck> getEvaluationChecks();

    /**
     * 修复提示：返回此类游戏的常见问题和修复方案。
     * 不是通用的"增量修补"，而是针对性的"这个症状 → 这么修"。
     */
    List<FixHint> getFixHints();
}

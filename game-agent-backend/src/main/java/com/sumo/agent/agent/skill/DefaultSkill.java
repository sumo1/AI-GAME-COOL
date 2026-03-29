package com.sumo.agent.agent.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 默认 Skill 实现 — 包装 SkillDefinition。
 * <p>
 * 遵循 OpenClaw 模式：SKILL.md body 原样传给 LLM，不做文本解析。
 * 代码级检查仅基于 gameType 派生（这是真正需要代码的部分）。
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

    /**
     * 代码级评估检查：通用检查 + gameType 特定检查。
     * 这些是 LLM 做不到的事——需要检查 HTML 结构和 Probe 数据。
     */
    @Override
    public List<EvaluationCheck> getEvaluationChecks() {
        List<EvaluationCheck> checks = new ArrayList<>();

        // 通用检查：所有游戏都该有
        checks.add(EvaluationCheck.hasScoreDisplay());
        checks.add(EvaluationCheck.jsErrorsBelow(2));
        checks.add(EvaluationCheck.outOfBoundsBelow(3));

        // gameType 特定检查
        if (definition.getGameType() != null) {
            addGameTypeChecks(definition.getGameType(), checks);
        }

        return checks;
    }

    /**
     * 修复提示：返回空。
     * SKILL.md 中的 "## 常见问题" 段落由 LLM 直接阅读，不需要代码解析。
     */
    @Override
    public List<FixHint> getFixHints() {
        return Collections.emptyList();
    }

    private void addGameTypeChecks(String gameType, List<EvaluationCheck> checks) {
        switch (gameType.toLowerCase()) {
            case "quiz" -> {
                checks.add(EvaluationCheck.hasFeedback());
                checks.add(EvaluationCheck.htmlMustContain("addEventListener",
                        "[quiz] 必须有事件监听器处理用户作答"));
            }
            case "matching" -> {
                checks.add(EvaluationCheck.htmlMustContain("match",
                        "[matching] 未找到配对逻辑"));
                checks.add(EvaluationCheck.hasInteraction());
            }
            case "simulation" -> {
                checks.add(EvaluationCheck.htmlMustContain("state",
                        "[simulation] 未找到状态管理逻辑"));
                checks.add(EvaluationCheck.hasInteraction());
            }
            case "recognition" -> {
                checks.add(EvaluationCheck.htmlMustContain("addEventListener",
                        "[recognition] 必须有交互事件处理"));
                checks.add(EvaluationCheck.hasFeedback());
            }
            case "logic" -> {
                checks.add(EvaluationCheck.hasFeedback());
                checks.add(EvaluationCheck.htmlMustContain("check",
                        "[logic] 未找到答案验证逻辑"));
            }
            default -> checks.add(EvaluationCheck.hasInteraction());
        }
    }
}

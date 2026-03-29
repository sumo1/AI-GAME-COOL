package com.sumo.agent.agent.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 默认 Skill 实现 — 包装 SkillDefinition，从 YAML 数据派生基础行为。
 * <p>
 * 所有从 YAML 加载的 Skill 默认走这个实现。
 * 特殊游戏类型可以子类化或注册自定义 Skill 实现来覆盖行为。
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
     * 生成引导：优先返回 SKILL.md 操作手册（instructions），
     * 回退到旧格式的 promptHint + evaluationCriteria。
     */
    @Override
    public String getGenerationGuidance() {
        return definition.getInstructions() != null ? definition.getInstructions() : "";
    }

    /**
     * 评估检查：从 YAML 数据派生基础代码检查。
     * 通用检查（计分、反馈、交互）+ 根据 gameType 派生的特定检查。
     */
    @Override
    public List<EvaluationCheck> getEvaluationChecks() {
        List<EvaluationCheck> checks = new ArrayList<>();

        // 通用检查：所有游戏都应该有
        checks.add(EvaluationCheck.hasScoreDisplay());
        checks.add(EvaluationCheck.jsErrorsBelow(2));
        checks.add(EvaluationCheck.outOfBoundsBelow(3));

        // 根据 YAML evaluationCriteria 文本派生代码检查
        if (definition.getEvaluationCriteria() != null) {
            for (String criterion : definition.getEvaluationCriteria()) {
                deriveCheckFromCriterion(criterion, checks);
            }
        }

        // 根据 gameType 添加类型特定检查
        if (definition.getGameType() != null) {
            addGameTypeChecks(definition.getGameType(), checks);
        }

        return checks;
    }

    /**
     * 修复提示：返回 YAML 中定义的 fixHints。
     */
    @Override
    public List<FixHint> getFixHints() {
        if (definition.getFixHints() == null) {
            return Collections.emptyList();
        }
        return definition.getFixHints();
    }

    /**
     * 从评估标准文本派生代码检查。
     * 通过关键词匹配，把文本标准转换成可执行的验证逻辑。
     */
    private void deriveCheckFromCriterion(String criterion, List<EvaluationCheck> checks) {
        String lower = criterion.toLowerCase();
        String skillName = definition.getName();

        if (lower.contains("反馈") || lower.contains("提示")) {
            checks.add(EvaluationCheck.hasFeedback());
        }

        if (lower.contains("计时") || lower.contains("timer") || lower.contains("倒计时")) {
            checks.add(EvaluationCheck.htmlMustContain("timer",
                    "[Skill:" + skillName + "] " + criterion));
        }

        if (lower.contains("开始") && lower.contains("按钮")) {
            checks.add(EvaluationCheck.htmlMustContain("start",
                    "[Skill:" + skillName + "] 未找到开始按钮"));
        }

        if (lower.contains("重新开始") || lower.contains("restart") || lower.contains("重玩")) {
            checks.add(EvaluationCheck.htmlMustContain("restart",
                    "[Skill:" + skillName + "] 未找到重新开始功能"));
        }
    }

    /**
     * 根据 gameType 添加类型特定检查。
     */
    private void addGameTypeChecks(String gameType, List<EvaluationCheck> checks) {
        switch (gameType.toLowerCase()) {
            case "quiz" -> {
                // 问答类：必须有题目生成和答案验证
                checks.add(EvaluationCheck.hasFeedback());
                checks.add(EvaluationCheck.htmlMustContain("addEventListener",
                        "[quiz] 必须有事件监听器处理用户作答"));
            }
            case "matching" -> {
                // 配对类：必须有翻牌/配对逻辑
                checks.add(EvaluationCheck.htmlMustContain("match",
                        "[matching] 未找到配对逻辑（match 相关代码）"));
                checks.add(EvaluationCheck.hasInteraction());
            }
            case "simulation" -> {
                // 模拟类：必须有状态管理
                checks.add(EvaluationCheck.htmlMustContain("state",
                        "[simulation] 未找到状态管理逻辑"));
                checks.add(EvaluationCheck.hasInteraction());
            }
            case "recognition" -> {
                // 认知类：必须有选择机制
                checks.add(EvaluationCheck.htmlMustContain("addEventListener",
                        "[recognition] 必须有交互事件处理"));
                checks.add(EvaluationCheck.hasFeedback());
            }
            case "logic" -> {
                // 逻辑类：必须有答案验证
                checks.add(EvaluationCheck.hasFeedback());
                checks.add(EvaluationCheck.htmlMustContain("check",
                        "[logic] 未找到答案验证逻辑（check 相关代码）"));
            }
            default -> {
                // 未知类型：至少要有交互
                checks.add(EvaluationCheck.hasInteraction());
            }
        }
    }
}

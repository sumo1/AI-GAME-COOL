package com.sumo.agent.agent.skill;

import com.sumo.agent.agent.evaluation.ProbeReport;

import java.util.Optional;

/**
 * 可执行的评估检查 — Skill 特有的代码级验证。
 * <p>
 * 与"文本标准"的本质区别：这是可执行的代码，能检查 HTML 结构和 Probe 运行时数据，
 * 产出具体的 issue 描述。不是给 LLM 看的文字，是跑在 JVM 上的检查逻辑。
 */
@FunctionalInterface
public interface EvaluationCheck {

    /**
     * 执行检查
     *
     * @param html   完整 HTML 游戏代码
     * @param report Probe 运行时数据（可能为 null，降级模式下）
     * @return 问题描述（检查通过返回 empty）
     */
    Optional<String> check(String html, ProbeReport report);

    // ==================== 静态工厂方法：常用检查模式 ====================

    /**
     * HTML 必须包含指定内容
     */
    static EvaluationCheck htmlMustContain(String pattern, String issueMsg) {
        return (html, report) -> html.contains(pattern)
                ? Optional.empty()
                : Optional.of(issueMsg);
    }

    /**
     * HTML 不得包含指定内容
     */
    static EvaluationCheck htmlMustNotContain(String pattern, String issueMsg) {
        return (html, report) -> html.contains(pattern)
                ? Optional.of(issueMsg)
                : Optional.empty();
    }

    /**
     * 必须有计分元素（检查 HTML 中是否有 score 相关文本/ID）
     */
    static EvaluationCheck hasScoreDisplay() {
        return (html, report) -> {
            boolean found = html.contains("score") || html.contains("分数")
                    || html.contains("得分") || html.contains("计分");
            return found ? Optional.empty()
                    : Optional.of("[Skill检查] 未找到计分显示元素");
        };
    }

    /**
     * 必须有反馈机制（答对/答错提示）
     */
    static EvaluationCheck hasFeedback() {
        return (html, report) -> {
            boolean found = html.contains("feedback") || html.contains("反馈")
                    || html.contains("正确") || html.contains("答对")
                    || html.contains("correct") || html.contains("wrong");
            return found ? Optional.empty()
                    : Optional.of("[Skill检查] 未找到答题反馈机制");
        };
    }

    /**
     * JS 错误不得超过阈值
     */
    static EvaluationCheck jsErrorsBelow(int maxAllowed) {
        return (html, report) -> {
            if (report == null) return Optional.empty();
            int errors = report.getErrors() != null ? report.getErrors().size() : 0;
            return errors <= maxAllowed
                    ? Optional.empty()
                    : Optional.of("[Skill检查] JS 错误 " + errors + " 个，超过阈值 " + maxAllowed);
        };
    }

    /**
     * 必须检测到用户交互事件
     */
    static EvaluationCheck hasInteraction() {
        return (html, report) -> {
            if (report == null) return Optional.empty();
            boolean hasEvents = report.getEvents() != null && !report.getEvents().isEmpty();
            return hasEvents
                    ? Optional.empty()
                    : Optional.of("[Skill检查] 未检测到用户交互事件");
        };
    }

    /**
     * 越界元素不得超过阈值
     */
    static EvaluationCheck outOfBoundsBelow(int maxAllowed) {
        return (html, report) -> {
            if (report == null) return Optional.empty();
            int oob = report.getOutOfBoundsElements() != null ? report.getOutOfBoundsElements().size() : 0;
            return oob <= maxAllowed
                    ? Optional.empty()
                    : Optional.of("[Skill检查] " + oob + " 个元素越界，超过阈值 " + maxAllowed);
        };
    }

    /**
     * HTML 必须包含指定 JS 函数定义
     */
    static EvaluationCheck hasJsFunction(String functionName, String issueMsg) {
        return (html, report) -> {
            // 匹配 function name( 或 const/let/var name = 或 name: function
            boolean found = html.contains("function " + functionName)
                    || html.contains(functionName + " =")
                    || html.contains(functionName + ":");
            return found ? Optional.empty() : Optional.of(issueMsg);
        };
    }
}

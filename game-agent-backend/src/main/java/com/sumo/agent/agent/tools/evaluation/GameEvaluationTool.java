package com.sumo.agent.agent.tools.evaluation;

import com.sumo.agent.agent.evaluation.GameEvaluator;
import com.sumo.agent.agent.evaluation.ProbeReport;
import com.sumo.agent.agent.loop.WorkingMemory;
import com.sumo.agent.agent.skill.EvaluationCheck;
import com.sumo.agent.agent.skill.Skill;
import com.sumo.agent.agent.tools.ToolContext;
import com.sumo.agent.agent.tools.generation.ErrorClassifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 游戏评估工具 — 使用 Playwright headless 浏览器评估游戏质量
 */
@Slf4j
@Component
public class GameEvaluationTool {

    /** evaluateGame Playwright 超时（毫秒） */
    private static final long EVALUATE_TIMEOUT_MS = 30_000;

    @Autowired
    private GameEvaluator gameEvaluator;

    @Autowired
    private ToolContext toolContext;

    @Tool(description = "评估生成的 HTML5 游戏的可玩性和质量。使用 headless 浏览器渲染游戏，模拟操作，检测 JS 错误、元素越界、交互响应性等。返回结构化的评估报告和各维度评分。")
    public String evaluateGame(
            @ToolParam(description = "要评估的完整 HTML 游戏代码") String htmlCode) {
        log.info("[evaluateGame] 开始评估游戏 ({} 字符)", htmlCode.length());

        // 超时保护：Playwright 评估最多 30 秒
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // 获取 Skill 特定的评估检查（如果有 activeSkill）
            Skill activeSkill = toolContext.getActiveSkill();
            List<EvaluationCheck> skillChecks = (activeSkill != null)
                    ? activeSkill.getEvaluationChecks()
                    : List.of();

            Future<ProbeReport> future = executor.submit(() ->
                    skillChecks.isEmpty()
                            ? gameEvaluator.evaluate(htmlCode)
                            : gameEvaluator.evaluate(htmlCode, skillChecks));
            ProbeReport report;
            try {
                report = future.get(EVALUATE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                log.warn("[evaluateGame] Playwright 评估超时 ({}ms)，返回降级结果", EVALUATE_TIMEOUT_MS);
                return buildDegradedEvalReport(htmlCode);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                log.error("[evaluateGame] 评估执行异常", cause);
                String errorType = ErrorClassifier.classify(cause instanceof Exception ? (Exception) cause : e);
                return "游戏评估失败 [" + errorType + "]: " + (cause != null ? cause.getMessage() : e.getMessage());
            }

            // 更新 WorkingMemory
            WorkingMemory memory = toolContext.getWorkingMemory();
            if (memory != null) {
                memory.setEvalScore(report.getTotalScore());
                List<String> openIssues = memory.getOpenIssues();
                openIssues.clear();
                if (report.getIssues() != null) {
                    openIssues.addAll(report.getIssues());
                }
                memory.setIssueCount(openIssues.size());
                log.info("WorkingMemory 已更新: evalScore={}, issues={}", report.getTotalScore(), openIssues.size());
            }

            return buildEvalReportText(report);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "游戏评估被中断";
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 构建降级评估报告（Playwright 超时时使用）
     */
    private String buildDegradedEvalReport(String htmlCode) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 游戏评估报告（降级模式 — Playwright 超时）\n\n");
        sb.append("**注意**: 浏览器评估超时，以下为静态分析结果\n\n");

        int score = 50;
        List<String> issues = new ArrayList<>();
        issues.add("[评估] Playwright 超时，无法获取运行时数据，建议检查游戏是否有死循环或资源加载问题");

        if (!htmlCode.contains("<script")) {
            score -= 20;
            issues.add("[可运行性] 未发现 JavaScript 代码");
        }
        if (!htmlCode.contains("addEventListener") && !htmlCode.contains("onclick")) {
            score -= 10;
            issues.add("[交互] 未发现事件监听器");
        }

        sb.append("**估计总分: ").append(score).append("/100** (降级评估)\n\n");
        sb.append("### 发现的问题\n");
        for (String issue : issues) {
            sb.append("- ").append(issue).append("\n");
        }

        // 更新 WorkingMemory
        WorkingMemory memory = toolContext.getWorkingMemory();
        if (memory != null) {
            memory.setEvalScore(score);
            memory.getOpenIssues().clear();
            memory.getOpenIssues().addAll(issues);
            memory.setIssueCount(issues.size());
        }

        return sb.toString();
    }

    private String buildEvalReportText(ProbeReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 游戏评估报告\n\n");
        sb.append("**总分: ").append(report.getTotalScore()).append("/100**\n\n");
        sb.append("| 维度 | 得分 |\n|------|------|\n");
        sb.append("| 可运行性 | ").append(report.getRunnabilityScore()).append("/20 |\n");
        sb.append("| 布局正确性 | ").append(report.getLayoutScore()).append("/20 |\n");
        sb.append("| 交互响应性 | ").append(report.getInteractivityScore()).append("/20 |\n");
        sb.append("| 游戏完整性 | ").append(report.getCompletenessScore()).append("/20 |\n");
        sb.append("| 教育匹配度 | ").append(report.getEducationScore()).append("/20 |\n\n");

        if (report.getIssues() != null && !report.getIssues().isEmpty()) {
            sb.append("### 发现的问题\n");
            for (String issue : report.getIssues()) {
                sb.append("- ").append(issue).append("\n");
            }
            sb.append("\n");
        }

        sb.append("### Probe 数据摘要\n");
        sb.append("- 页面加载: ").append(report.isPageLoaded() ? "成功" : "失败").append("\n");
        sb.append("- JS 错误数: ").append(report.getErrors() != null ? report.getErrors().size() : 0).append("\n");
        sb.append("- 交互事件数: ").append(report.getEvents() != null ? report.getEvents().size() : 0).append("\n");
        sb.append("- DOM 变化次数: ").append(report.getDomMutationsCount()).append("\n");
        sb.append("- 状态转换: ").append(report.getStateTransitions() != null ? report.getStateTransitions() : "无").append("\n");
        sb.append("- 越界元素数: ").append(report.getOutOfBoundsElements() != null ? report.getOutOfBoundsElements().size() : 0).append("\n");

        if (report.getFinalState() != null) {
            sb.append("- 最终分数: ").append(report.getFinalState().getScore()).append("\n");
        }

        return sb.toString();
    }
}

package com.sumo.agent.agent.evaluation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GameEvaluator 命令行入口 — 给交叉验证脚本 (`scripts/cross-verify.sh`) 用。
 *
 * 用法：
 * <pre>
 *   java -cp game-agent-backend/target/classes:... \
 *        com.sumo.agent.agent.evaluation.GameEvaluatorMain &lt;html-path&gt;
 * </pre>
 *
 * 输出（stdout）：
 * <pre>
 *   totalScore=N
 *   runnability=N
 *   layout=N
 *   interactivity=N
 *   completeness=N
 *   education=N
 * </pre>
 *
 * 任务 260522-evaluator-oracle-shared-core Step 4 反向要求 Step 2 留这个入口。
 */
public class GameEvaluatorMain {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: GameEvaluatorMain <html-path>");
            System.exit(2);
        }

        Path htmlPath = Path.of(args[0]);
        if (!Files.exists(htmlPath)) {
            System.err.println("HTML file not found: " + htmlPath);
            System.exit(2);
        }

        String html = Files.readString(htmlPath, StandardCharsets.UTF_8);

        // 直接 new GameEvaluator() 绕过 Spring（cross-verify 不需要完整 ApplicationContext）
        GameEvaluator ev = new GameEvaluator();
        try {
            ev.init();
        } catch (Exception e) {
            System.err.println("GameEvaluator.init() failed: " + e.getMessage());
            System.exit(3);
        }

        ProbeReport report = null;
        try {
            report = ev.evaluate(html);
        } catch (Exception e) {
            System.err.println("GameEvaluator.evaluate() failed: " + e.getMessage());
            System.exit(4);
        }

        System.out.println("totalScore=" + report.getTotalScore());
        System.out.println("runnability=" + report.getRunnabilityScore());
        System.out.println("layout=" + report.getLayoutScore());
        System.out.println("interactivity=" + report.getInteractivityScore());
        System.out.println("completeness=" + report.getCompletenessScore());
        System.out.println("education=" + report.getEducationScore());
    }
}

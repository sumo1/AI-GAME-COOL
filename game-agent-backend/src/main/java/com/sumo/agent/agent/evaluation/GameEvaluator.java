package com.sumo.agent.agent.evaluation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.sumo.agent.agent.skill.EvaluationCheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 游戏评估引擎 — 使用 Playwright headless 浏览器渲染游戏并收割 Probe 数据
 * <p>
 * 流程：
 * 1. 将 game-probe.js 注入到 HTML 的 <head> 中
 * 2. Playwright 渲染注入后的 HTML
 * 3. 模拟操作：等待加载 → 点击"开始" → 模拟 3-5 步点击
 * 4. 收割 window.__GAME_PROBE__ 数据
 * 5. 基于 Probe 数据计算评分
 */
@Slf4j
@Component
public class GameEvaluator {

    private static final int STEP_INTERVAL_MS = 800;
    private static final int PAGE_LOAD_TIMEOUT_MS = 5000;
    private static final int MAX_INTERACTION_STEPS = 5;

    private String probeScript;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @PostConstruct
    public void init() throws IOException {
        ClassPathResource resource = new ClassPathResource("probe/game-probe.js");
        probeScript = resource.getContentAsString(StandardCharsets.UTF_8);
        log.info("Game Probe 脚本加载完成 ({} 字符)", probeScript.length());
    }

    /**
     * 评估 HTML 游戏代码
     *
     * @param htmlCode 完整的 HTML 游戏代码
     * @return ProbeReport 结构化评估报告（含评分）
     */
    public ProbeReport evaluate(String htmlCode) {
        String injectedHtml = injectProbe(htmlCode);
        ProbeReport report;

        // 写入临时文件供 Playwright 加载
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("game-eval-", ".html");
            Files.writeString(tempFile, injectedHtml, StandardCharsets.UTF_8);

            report = runInBrowser(tempFile);
        } catch (Exception e) {
            log.error("游戏评估失败", e);
            report = createFailedReport(e.getMessage());
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
        }

        // 计算评分
        computeScores(report);
        return report;
    }

    /**
     * 评估 HTML 游戏代码（带 Skill 特定检查）
     *
     * @param htmlCode 完整的 HTML 游戏代码
     * @param skillChecks Skill 提供的可执行检查列表
     * @return ProbeReport 结构化评估报告（含通用评分 + Skill 检查结果）
     */
    public ProbeReport evaluate(String htmlCode, List<EvaluationCheck> skillChecks) {

        // 先执行通用评估
        ProbeReport report = evaluate(htmlCode);

        // 再执行 Skill 特定检查
        if (skillChecks != null && !skillChecks.isEmpty()) {
            runSkillChecks(htmlCode, report, skillChecks);
        }

        return report;
    }

    /**
     * 执行 Skill 特定的代码级检查，将发现的问题追加到 report.issues
     */
    private void runSkillChecks(String htmlCode, ProbeReport report, List<EvaluationCheck> checks) {
        List<String> existingIssues = report.getIssues();
        if (existingIssues == null) {
            existingIssues = new ArrayList<>();
        }

        int skillIssueCount = 0;
        for (EvaluationCheck check : checks) {
            try {
                Optional<String> issue = check.check(htmlCode, report);
                if (issue.isPresent()) {
                    existingIssues.add(issue.get());
                    skillIssueCount++;
                }
            } catch (Exception e) {
                log.warn("Skill 检查执行异常: {}", e.getMessage());
            }
        }

        report.setIssues(existingIssues);

        // Skill 检查结果影响教育匹配度评分（原来固定 15 分，现在根据 Skill 检查动态调整）
        if (skillIssueCount == 0) {
            report.setEducationScore(20); // Skill 检查全部通过，满分
        } else if (skillIssueCount <= 2) {
            report.setEducationScore(15); // 少量问题
        } else {
            report.setEducationScore(10); // 较多 Skill 特定问题
        }

        // 重新计算总分
        int total = report.getRunnabilityScore() + report.getLayoutScore()
                + report.getInteractivityScore() + report.getCompletenessScore()
                + report.getEducationScore();
        report.setTotalScore(total);

        log.info("Skill 检查完成: {}项检查, {}项问题, 教育匹配度={}分, 新总分={}/100",
                checks.size(), skillIssueCount, report.getEducationScore(), total);
    }

    /**
     * 将 probe 脚本注入到 HTML 的 <head> 中
     */
    String injectProbe(String htmlCode) {
        String probeTag = "<script>\n" + probeScript + "\n</script>";

        if (htmlCode.contains("<head>")) {
            return htmlCode.replace("<head>", "<head>\n" + probeTag);
        } else if (htmlCode.contains("<html")) {
            // 没有 <head>，在 <html...> 后插入
            int idx = htmlCode.indexOf(">", htmlCode.indexOf("<html"));
            return htmlCode.substring(0, idx + 1) + "\n<head>" + probeTag + "</head>\n" + htmlCode.substring(idx + 1);
        } else {
            // 兜底：直接加在最前面
            return probeTag + "\n" + htmlCode;
        }
    }

    /**
     * 在 headless 浏览器中渲染并模拟操作
     */
    private ProbeReport runInBrowser(Path htmlFile) throws Exception {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true)
            );
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions().setViewportSize(1024, 768)
            );
            Page page = context.newPage();

            // 加载页面
            page.navigate("file://" + htmlFile.toAbsolutePath());
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(PAGE_LOAD_TIMEOUT_MS));

            boolean pageLoaded = isPageLoaded(page);
            log.info("页面加载状态: {}", pageLoaded ? "成功" : "失败/白屏");

            // 等待页面初始化
            page.waitForTimeout(1000);

            // 模拟操作
            simulateInteractions(page);

            // 收割 Probe 数据
            Object probeData = page.evaluate("() => { window.__GAME_PROBE__.collectFinalState(); return JSON.parse(JSON.stringify(window.__GAME_PROBE__)); }");

            browser.close();

            // 反序列化
            String json = objectMapper.writeValueAsString(probeData);
            log.debug("Probe 原始数据: {}", json);

            ProbeReport report = objectMapper.readValue(json, ProbeReport.class);
            report.setPageLoaded(pageLoaded);
            return report;
        }
    }

    /**
     * 检查页面是否成功加载（非白屏）
     */
    private boolean isPageLoaded(Page page) {
        try {
            Object result = page.evaluate("() => { return document.body && document.body.innerHTML.trim().length > 0; }");
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 模拟用户交互：找开始按钮 → 点击 → 模拟 3-5 步操作
     */
    private void simulateInteractions(Page page) {
        // Step 1: 尝试找到并点击"开始"按钮
        boolean startClicked = tryClickStart(page);
        if (startClicked) {
            log.info("已点击开始按钮");
            page.waitForTimeout(STEP_INTERVAL_MS);
        }

        // Step 2-5: 模拟点击可见的交互元素
        for (int step = 0; step < MAX_INTERACTION_STEPS; step++) {
            boolean clicked = tryClickInteractiveElement(page);
            if (!clicked) {
                log.info("第 {} 步未找到可点击元素，停止模拟", step + 1);
                break;
            }
            log.info("第 {} 步点击完成", step + 1);
            page.waitForTimeout(STEP_INTERVAL_MS);
        }
    }

    /**
     * 尝试点击"开始"类按钮
     */
    private boolean tryClickStart(Page page) {
        // 中英文常见开始按钮文本
        String[] startTexts = {"开始", "开始游戏", "Start", "Play", "GO", "开始挑战"};
        for (String text : startTexts) {
            try {
                Locator btn = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
                if (btn.isVisible()) {
                    btn.click();
                    return true;
                }
            } catch (Exception ignored) {}
        }
        // 也尝试通过常见选择器
        String[] selectors = {"#start-btn", "#startBtn", ".start-btn", ".start-button",
                "button[id*='start']", "button[class*='start']"};
        for (String sel : selectors) {
            try {
                Locator btn = page.locator(sel).first();
                if (btn.isVisible()) {
                    btn.click();
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * 尝试点击一个可见的交互元素（按钮、选项等）
     */
    private boolean tryClickInteractiveElement(Page page) {
        // 优先点击游戏选项/答案按钮
        String[] selectors = {
                "button:visible", ".option:visible", ".answer:visible", ".choice:visible",
                "[data-answer]:visible", ".card:visible", ".cell:visible",
                "input[type='button']:visible", "a.btn:visible"
        };
        for (String sel : selectors) {
            try {
                Locator elements = page.locator(sel);
                int count = elements.count();
                if (count > 0) {
                    // 选择一个可见且可点击的元素
                    for (int i = 0; i < Math.min(count, 10); i++) {
                        Locator el = elements.nth(i);
                        if (el.isVisible() && el.isEnabled()) {
                            el.click();
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * 基于 Probe 数据计算各维度评分
     */
    public void computeScores(ProbeReport report) {
        List<String> issues = new ArrayList<>();

        // 1. 可运行性 (20分)
        int runnability = 20;
        if (!report.isPageLoaded()) {
            runnability = 0;
            issues.add("[可运行性] 页面白屏，未能正常加载");
        } else if (report.getErrors() != null && !report.getErrors().isEmpty()) {
            int errorCount = report.getErrors().size();
            if (errorCount >= 3) {
                runnability = 5;
                issues.add("[可运行性] 存在 " + errorCount + " 个 JS 错误: " +
                        report.getErrors().stream().map(ProbeReport.ProbeError::getMsg).limit(3).toList());
            } else {
                runnability = 10;
                issues.add("[可运行性] 存在 " + errorCount + " 个 JS 错误: " +
                        report.getErrors().stream().map(ProbeReport.ProbeError::getMsg).toList());
            }
        }
        report.setRunnabilityScore(runnability);

        // 2. 布局正确性 (20分)
        int layout = 20;
        if (report.getOutOfBoundsElements() != null && !report.getOutOfBoundsElements().isEmpty()) {
            int oobCount = report.getOutOfBoundsElements().size();
            if (oobCount >= 5) {
                layout = 0;
                issues.add("[布局] " + oobCount + " 个元素越界，布局严重问题");
            } else if (oobCount >= 2) {
                layout = 10;
                issues.add("[布局] " + oobCount + " 个元素越界: " +
                        report.getOutOfBoundsElements().stream().map(ProbeReport.OutOfBoundsElement::getElement).toList());
            } else {
                layout = 15;
                issues.add("[布局] 1 个元素越界: " + report.getOutOfBoundsElements().get(0).getElement());
            }
        }
        report.setLayoutScore(layout);

        // 3. 交互响应性 (20分)
        int interactivity = 0;
        if (report.getEvents() != null && !report.getEvents().isEmpty()) {
            long clickEvents = report.getEvents().stream()
                    .filter(e -> "click".equals(e.getType()))
                    .count();
            long domChangedClicks = report.getEvents().stream()
                    .filter(e -> "click".equals(e.getType()) && Boolean.TRUE.equals(e.getDomChanged()))
                    .count();

            if (clickEvents == 0) {
                interactivity = 5; // 有事件但没有点击
                issues.add("[交互] 未检测到点击交互");
            } else if (domChangedClicks == 0) {
                interactivity = 10;
                issues.add("[交互] 点击后 DOM 未发生变化，交互可能无响应");
            } else if (domChangedClicks < clickEvents / 2) {
                interactivity = 15;
                issues.add("[交互] 部分点击无响应 (" + domChangedClicks + "/" + clickEvents + " 有效)");
            } else {
                interactivity = 20;
            }
        } else {
            issues.add("[交互] 未检测到任何交互事件");
        }
        report.setInteractivityScore(interactivity);

        // 4. 游戏完整性 (20分)
        int completeness = 0;
        boolean hasStateTransitions = report.getStateTransitions() != null && !report.getStateTransitions().isEmpty();
        boolean hasScoreChanges = report.getStateChanges() != null &&
                report.getStateChanges().stream().anyMatch(sc -> "score_change".equals(sc.getType()));
        boolean hasDomMutations = report.getDomMutationsCount() > 0;

        if (hasStateTransitions && hasScoreChanges) {
            completeness = 20;
        } else if (hasStateTransitions || hasScoreChanges) {
            completeness = 15;
            if (!hasStateTransitions) issues.add("[完整性] 未检测到状态转换（开始→进行→结束）");
            if (!hasScoreChanges) issues.add("[完整性] 未检测到分数变化");
        } else if (hasDomMutations) {
            completeness = 10;
            issues.add("[完整性] 有 DOM 变化但未检测到游戏状态转换和计分");
        } else {
            issues.add("[完整性] 未检测到游戏状态变化，游戏可能无法正常运行");
        }
        report.setCompletenessScore(completeness);

        // 5. 教育匹配度 (20分) — Phase 3 给默认 15 分
        report.setEducationScore(15);

        // 总分
        int total = runnability + layout + interactivity + completeness + 15;
        report.setTotalScore(total);
        report.setIssues(issues);

        log.info("评估完成: 总分={}/100 (可运行性={}, 布局={}, 交互={}, 完整性={}, 教育=15)",
                total, runnability, layout, interactivity, completeness);
    }

    /**
     * 创建一个失败的报告（浏览器崩溃等极端情况）
     */
    private ProbeReport createFailedReport(String errorMsg) {
        ProbeReport report = new ProbeReport();
        report.setPageLoaded(false);
        report.setErrors(List.of());
        report.setEvents(List.of());
        report.setStateChanges(List.of());
        report.setOutOfBoundsElements(List.of());
        report.setStateTransitions(List.of());
        report.setResponseLatencies(List.of());
        report.setConsoleWarnings(List.of());
        report.setDomMutationsCount(0);

        ProbeReport.FinalState fs = new ProbeReport.FinalState();
        fs.setTotalErrors(1);
        report.setFinalState(fs);

        List<String> issues = new ArrayList<>();
        issues.add("[致命错误] 浏览器评估失败: " + errorMsg);
        report.setIssues(issues);
        return report;
    }
}

package com.sumo.agent.agent.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 游戏评估引擎 — 使用 Playwright headless 浏览器渲染游戏并收割 Probe 数据
 * <p>
 * V2 流程（任务 260522-evaluator-oracle-shared-core Step 2）：
 * 1. 通过 Playwright {@code addInitScript} 在 navigate 之前注入 shared/playability/*.js
 * 2. 渲染 HTML，等待 NETWORKIDLE
 * 3. 模拟操作：找开始按钮（共享 driver）→ 点击 → 模拟 3-5 步点击其它交互元素
 * 4. 收割 {@code window.__PLAYABILITY__} 数据 + 内联越界检测
 * 5. 基于 ProbeReport 计算五维评分（公式不变）
 */
@Slf4j
@Component
public class GameEvaluator {

    private static final int STEP_INTERVAL_MS = 800;
    private static final int PAGE_LOAD_TIMEOUT_MS = 5000;
    private static final int MAX_INTERACTION_STEPS = 5;

    /** @deprecated v1 game-probe.js 已停用，保留字段防止编译期外部引用断裂；不再加载内容。 */
    @Deprecated
    private String probeScript;

    private String playabilityProbeJs;
    private String playabilityDriverJs;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @PostConstruct
    public void init() throws IOException {
        Path projectRoot = findProjectRoot();
        playabilityProbeJs = Files.readString(
                projectRoot.resolve("shared/playability/playability-probe.js"),
                StandardCharsets.UTF_8);
        playabilityDriverJs = Files.readString(
                projectRoot.resolve("shared/playability/playability-driver.js"),
                StandardCharsets.UTF_8);

        log.info("Playability shared JS 加载完成 ({} + {} chars)",
                playabilityProbeJs.length(), playabilityDriverJs.length());
    }

    /**
     * 项目根定位：从 cwd 向上找含 shared/playability/ 的目录。
     * mvn spring-boot:run cwd 在 game-agent-backend，找父级；从根目录跑则当前级即是。
     */
    private Path findProjectRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("shared/playability"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException(
                    "找不到 shared/playability/ 目录（从 user.dir 向上未发现）");
        }
        return p;
    }

    /**
     * 评估 HTML 游戏代码
     *
     * @param htmlCode 完整的 HTML 游戏代码
     * @return ProbeReport 结构化评估报告（含评分）
     */
    public ProbeReport evaluate(String htmlCode) {
        ProbeReport report;
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("game-eval-", ".html");
            Files.writeString(tempFile, htmlCode, StandardCharsets.UTF_8);
            report = runInBrowser(tempFile);
        } catch (Exception e) {
            log.error("游戏评估失败", e);
            report = createFailedReport(e.getMessage());
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
        }

        computeScores(report);
        return report;
    }

    /**
     * @deprecated 由 {@code page.addInitScript} 替代；保留方法签名给可能的旧测试引用。
     * 直接返回原 HTML 不做修改。
     */
    @Deprecated
    String injectProbe(String htmlCode) {
        return htmlCode;
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

            // 注入共享 probe + driver（必须在 navigate 之前注册，所有页面 JS 之前执行）
            context.addInitScript(playabilityProbeJs);
            context.addInitScript(playabilityDriverJs);

            Page page = context.newPage();
            page.navigate("file://" + htmlFile.toAbsolutePath());
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(PAGE_LOAD_TIMEOUT_MS));

            boolean pageLoaded = isPageLoaded(page);
            log.info("页面加载状态: {}", pageLoaded ? "成功" : "失败/白屏");

            page.waitForTimeout(1000);

            simulateInteractions(page);

            ProbeReport report = harvestProbe(page);
            report.setPageLoaded(pageLoaded);

            browser.close();
            return report;
        }
    }

    /**
     * 检查页面是否成功加载（非白屏）
     */
    private boolean isPageLoaded(Page page) {
        try {
            Object result = page.evaluate(
                    "() => { return document.body && document.body.innerHTML.trim().length > 0; }");
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 收割 window.__PLAYABILITY__ 数据并投影到 ProbeReport。
     * <p>
     * 共享 probe 的设计哲学是"采集瞬时信号"而非"监听事件流"，所以：
     * <ul>
     *   <li>events / stateChanges / stateTransitions / domMutationsCount 暂为空/0</li>
     *   <li>finalState.score 用 collect().numeric 中疑似 score 的节点反推</li>
     *   <li>outOfBoundsElements 仍由 GameEvaluator 内联 JS 检测（不属于共享 probe 职责）</li>
     * </ul>
     */
    private ProbeReport harvestProbe(Page page) {
        ProbeReport report = new ProbeReport();

        // 1. errors
        try {
            Object errorsRaw = page.evaluate(
                    "() => window.__PLAYABILITY__ ? JSON.stringify(window.__PLAYABILITY__.getErrors()) : '[]'");
            String errorsJson = (errorsRaw instanceof String s && !s.isBlank()) ? s : "[]";
            List<ProbeReport.ProbeError> errors = objectMapper.readValue(
                    errorsJson, new TypeReference<List<ProbeReport.ProbeError>>() {});
            report.setErrors(errors);
        } catch (Exception e) {
            log.warn("harvest errors 失败: {}", e.getMessage());
            report.setErrors(new ArrayList<>());
        }

        // 2. outOfBoundsElements（内联 JS，与共享 probe 解耦）
        try {
            Object oobRaw = page.evaluate(buildOutOfBoundsJs());
            String oobJson = (oobRaw instanceof String s && !s.isBlank()) ? s : "[]";
            List<ProbeReport.OutOfBoundsElement> oob = objectMapper.readValue(
                    oobJson, new TypeReference<List<ProbeReport.OutOfBoundsElement>>() {});
            report.setOutOfBoundsElements(oob);
        } catch (Exception e) {
            log.warn("harvest outOfBounds 失败: {}", e.getMessage());
            report.setOutOfBoundsElements(new ArrayList<>());
        }

        // 3. 共享 probe 不监听事件流，事件类信号置空（已知评分降级，见 task memory）
        report.setEvents(new ArrayList<>());
        report.setStateChanges(new ArrayList<>());
        report.setStateTransitions(new ArrayList<>());
        report.setConsoleWarnings(new ArrayList<>());
        report.setResponseLatencies(new ArrayList<>());
        report.setDomMutationsCount(0);

        // 4. finalState（用 numeric 中找 score 节点）
        report.setFinalState(buildFinalState(page));

        return report;
    }

    /**
     * 内联越界检测 JS（从老 game-probe.js 抽出）
     */
    private String buildOutOfBoundsJs() {
        return "() => {\n" +
                "  var vw = window.innerWidth, vh = window.innerHeight;\n" +
                "  var oob = [];\n" +
                "  var all = document.querySelectorAll('button, div, span, p, h1, h2, h3, img, canvas, svg, input');\n" +
                "  all.forEach(function(el) {\n" +
                "    var rect = el.getBoundingClientRect();\n" +
                "    if (rect.width === 0 && rect.height === 0) return;\n" +
                "    var style = window.getComputedStyle(el);\n" +
                "    if (style.display === 'none' || style.visibility === 'hidden') return;\n" +
                "    if (rect.right > vw + 5 || rect.bottom > vh + 5 || rect.left < -5 || rect.top < -5) {\n" +
                "      var sel = el.tagName.toLowerCase();\n" +
                "      if (el.id) sel += '#' + el.id;\n" +
                "      else if (el.className && typeof el.className === 'string') sel += '.' + el.className.split(' ')[0];\n" +
                "      oob.push({\n" +
                "        element: sel,\n" +
                "        rect: { left: Math.round(rect.left), top: Math.round(rect.top), right: Math.round(rect.right), bottom: Math.round(rect.bottom) },\n" +
                "        viewport: { width: vw, height: vh }\n" +
                "      });\n" +
                "    }\n" +
                "  });\n" +
                "  return JSON.stringify(oob);\n" +
                "}";
    }

    /**
     * 用共享 probe.collect().numeric 找 score 节点（保留老 getScoreValue 语义）
     */
    private ProbeReport.FinalState buildFinalState(Page page) {
        ProbeReport.FinalState fs = new ProbeReport.FinalState();
        try {
            Object scoreRaw = page.evaluate(
                    "() => { if (!window.__PLAYABILITY__) return null; " +
                    "const s = window.__PLAYABILITY__.collect(); " +
                    "const n = (s.numeric || []).find(x => /score|分数|得分/i.test(x.path)); " +
                    "return n ? JSON.stringify({ score: parseInt(n.val, 10), stateText: '' }) : null; }");
            if (scoreRaw instanceof String s && !s.isBlank()) {
                FinalStatePayload p = objectMapper.readValue(s, FinalStatePayload.class);
                fs.setScore(p.score);
                fs.setStateText(p.stateText != null ? p.stateText : "");
            } else {
                fs.setStateText("");
            }
        } catch (Exception e) {
            log.debug("buildFinalState 失败: {}", e.getMessage());
            fs.setStateText("");
        }
        return fs;
    }

    /** 临时反序列化容器。 */
    private static class FinalStatePayload {
        public Integer score;
        public String stateText;
    }

    /**
     * 模拟用户交互：找开始按钮 → 点击 → 模拟 3-5 步操作
     */
    private void simulateInteractions(Page page) {
        boolean startClicked = tryClickStart(page);
        if (startClicked) {
            log.info("已点击开始按钮（共享 driver）");
            page.waitForTimeout(STEP_INTERVAL_MS);
        }

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
     * 通过共享 driver.findStartButton 拿坐标 → page.mouse().click → JS click 兜底
     */
    private boolean tryClickStart(Page page) {
        try {
            Object btnRaw = page.evaluate(
                    "() => { if (!window.__PLAYABILITY_DRIVER__) return null; " +
                    "const b = window.__PLAYABILITY_DRIVER__.findStartButton(); " +
                    "return b ? JSON.stringify(b) : null; }");
            if (btnRaw instanceof String s && !s.isBlank()) {
                Map<String, Object> btn = objectMapper.readValue(
                        s, new TypeReference<Map<String, Object>>() {});
                double x = ((Number) btn.get("x")).doubleValue();
                double y = ((Number) btn.get("y")).doubleValue();
                page.mouse().click(x, y);

                // JS click 兜底：覆盖坐标命中盲区
                page.evaluate(
                        "() => window.__PLAYABILITY_DRIVER__ && window.__PLAYABILITY_DRIVER__.clickByJS()");
                return true;
            }
        } catch (Exception e) {
            log.debug("findStartButton 失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 尝试点击一个可见的交互元素（按钮、选项等）
     */
    private boolean tryClickInteractiveElement(Page page) {
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

package com.sumo.agent.agent.loop;

import com.sumo.agent.agent.evaluation.EvaluationObservation;
import com.sumo.agent.agent.evaluation.ProbeReport;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ContextRenderer 单元测试 — 验证 XML 渲染语义与原 WorkingMemory.toContextXml 等价，
 * 同时覆盖 plan 中要求的负面用例（null / 空 openIssues / null gameHtml）。
 */
class ContextRendererTest {

    private final ContextRenderer renderer = new ContextRenderer();

    // 1. 空 WorkingMemory → 输出含 <working_memory>
    @Test
    void render_emptyMemory_containsWorkingMemoryTag() {
        WorkingMemory memory = new WorkingMemory();

        String output = renderer.render(memory);

        assertTrue(output.contains("<working_memory>"), "应包含 <working_memory> 起始标签");
        assertTrue(output.contains("</working_memory>"), "应包含 </working_memory> 结束标签");
        assertTrue(output.contains("<version>0</version>"), "默认 version 为 0");
        assertTrue(output.contains("<iteration>0 of 5</iteration>"), "默认 iteration 为 0 of 5");
    }

    // 2. 有 openIssues → 输出含 <open_issues> 块和问题项
    @Test
    void render_withOpenIssues_includesIssuesBlock() {
        WorkingMemory memory = new WorkingMemory();
        memory.getOpenIssues().add("缺少答对反馈");
        memory.getOpenIssues().add("难度未递进");

        String output = renderer.render(memory);

        assertTrue(output.contains("<open_issues>"), "应包含 <open_issues> 起始");
        assertTrue(output.contains("</open_issues>"), "应包含 </open_issues> 结束");
        assertTrue(output.contains("- 缺少答对反馈"), "应列出第一条 issue");
        assertTrue(output.contains("- 难度未递进"), "应列出第二条 issue");
    }

    // 3. 短 HTML (< 8000) → 输出含 <game_html><![CDATA[
    @Test
    void render_shortHtml_includesFullCdataBlock() {
        WorkingMemory memory = new WorkingMemory();
        String shortHtml = "<html><body><h1>Hello</h1></body></html>";
        memory.setGameHtml(shortHtml);

        String output = renderer.render(memory);

        assertTrue(output.contains("<game_html><![CDATA["), "短 HTML 应原文嵌入 CDATA");
        assertTrue(output.contains(shortHtml), "CDATA 内应包含完整 HTML");
        assertTrue(output.contains("]]></game_html>"), "应正确闭合 CDATA");
        assertFalse(output.contains("<html_summary>"), "短 HTML 不应输出摘要");
    }

    // 4. 长 HTML (> 8000) → 输出含 <html_summary> 和 <html_length>，不含完整 HTML 主体
    @Test
    void render_longHtml_outputsSummaryNotFullBody() {
        // 构造 > 8000 字符的 HTML，含一个独特的标志字符串
        String marker = "UNIQUE_LONG_HTML_BODY_MARKER_DO_NOT_LEAK";
        StringBuilder padding = new StringBuilder();
        for (int i = 0; i < 9000; i++) {
            padding.append('x');
        }
        String longHtml = "<html><head><title>Long Game</title></head>"
                + "<body><div class=\"game-board\">" + marker + "</div>"
                + "<script>function startGame(){}</script>"
                + padding
                + "</body></html>";

        WorkingMemory memory = new WorkingMemory();
        memory.setGameHtml(longHtml);

        String output = renderer.render(memory);

        assertTrue(output.contains("<html_summary>"), "长 HTML 应输出 <html_summary>");
        assertTrue(output.contains("<html_length>" + longHtml.length() + "</html_length>"),
                "应输出 <html_length> 等于真实长度");
        assertFalse(output.contains(marker),
                "长 HTML 主体的 marker 不应出现在输出中（防止泄漏完整 HTML）");
        assertFalse(output.contains("<game_html><![CDATA["),
                "长 HTML 不应走 CDATA 完整嵌入分支");
    }

    // 5. render(null) 不抛 NPE，返回非空字符串、含 <working_memory>
    @Test
    void render_nullMemory_returnsEmptyWorkingMemoryWithoutNpe() {
        String output = renderer.render(null);

        assertNotNull(output, "render(null) 必须返回非 null");
        assertFalse(output.isEmpty(), "render(null) 必须返回非空字符串");
        assertTrue(output.contains("<working_memory>"), "应包含 <working_memory>");
        assertTrue(output.contains("</working_memory>"), "应正确闭合");
        assertTrue(output.contains("<version>0</version>"), "空状态 version=0");
        assertTrue(output.contains("<iteration>0 of 5</iteration>"), "空状态 iteration=0");
        // null memory 不应有 game_html / open_issues 块
        assertFalse(output.contains("<game_html"), "null 时不输出 game_html");
        assertFalse(output.contains("<open_issues>"), "null 时不输出 open_issues");
    }

    // 6. gameHtml = null 时输出不含 <game_html>
    @Test
    void render_nullGameHtml_omitsGameHtmlTag() {
        WorkingMemory memory = new WorkingMemory();
        memory.setGameHtml(null);

        String output = renderer.render(memory);

        assertFalse(output.contains("<game_html"), "gameHtml=null 不应输出 <game_html>");
        assertFalse(output.contains("<html_summary>"), "gameHtml=null 不应输出 <html_summary>");
        assertFalse(output.contains("<html_length>"), "gameHtml=null 不应输出 <html_length>");
    }

    // 7. openIssues 空列表时输出不含 <open_issues> 块
    @Test
    void render_emptyOpenIssues_omitsOpenIssuesBlock() {
        WorkingMemory memory = new WorkingMemory();
        // openIssues 默认就是空列表，显式不加任何项
        assertTrue(memory.getOpenIssues().isEmpty(), "前置：openIssues 应为空");

        String output = renderer.render(memory);

        assertFalse(output.contains("<open_issues>"), "空 issues 不应输出 <open_issues> 块");
        assertFalse(output.contains("</open_issues>"), "空 issues 不应输出 </open_issues>");
    }

    // 8. 语义等价：memory.toContextXml() == new ContextRenderer().render(memory)
    @Test
    void render_equivalentToWorkingMemoryToContextXml() {
        WorkingMemory memory = new WorkingMemory();
        memory.incrementGameVersion();
        memory.incrementGameVersion();
        memory.setEvalScore(75);
        memory.setIteration(3);
        memory.setFixCount(2);
        memory.setGameHtml("<html><body>short</body></html>");
        memory.setPreloadedSkill("math-adventure");
        memory.getOpenIssues().add("issue A");

        String fromMemory = memory.toContextXml();
        String fromRenderer = new ContextRenderer().render(memory);

        assertEquals(fromMemory, fromRenderer,
                "WorkingMemory.toContextXml() 与 ContextRenderer.render() 必须完全相等");
        // Step 2 关键约束：未设置 lastEvaluationObservation 时不应输出新块
        assertFalse(fromRenderer.contains("<evaluation_observation>"),
                "obs 未设置时 render 不应输出 <evaluation_observation>");
    }

    // 9. 设置了 EvaluationObservation 后 render 输出含结构化块
    @Test
    void render_withEvaluationObservation_outputsStructuredBlock() {
        WorkingMemory memory = new WorkingMemory();

        ProbeReport report = new ProbeReport();
        report.setTotalScore(72);
        report.setRunnabilityScore(18);
        report.setLayoutScore(14);
        report.setInteractivityScore(15);
        report.setCompletenessScore(13);
        report.setEducationScore(12);
        report.setPageLoaded(true);
        report.setDomMutationsCount(5);
        report.setStateTransitions(Arrays.asList("idle->playing"));
        report.setIssues(Arrays.asList("[交互] 未发现事件监听器"));

        EvaluationObservation obs = EvaluationObservation.fromProbeReport(report);
        memory.setLastEvaluationObservation(obs);

        String output = renderer.render(memory);

        assertTrue(output.contains("<evaluation_observation>"), "应输出新块");
        assertTrue(output.contains("</evaluation_observation>"), "应正确闭合");
        assertTrue(output.contains("<total_score>72/100</total_score>"), "总分输出格式");
        assertTrue(output.contains("<scores>"), "应含 <scores>");
        assertTrue(output.contains("<runnability>18/20</runnability>"));
        assertTrue(output.contains("<layout>14/20</layout>"));
        assertTrue(output.contains("<interactivity>15/20</interactivity>"));
        assertTrue(output.contains("<completeness>13/20</completeness>"));
        assertTrue(output.contains("<education>12/20</education>"));
        assertTrue(output.contains("<probe_summary>"), "应含 <probe_summary>");
        assertTrue(output.contains("<page_loaded>true</page_loaded>"));
        assertTrue(output.contains("<dom_mutations>5</dom_mutations>"));
        assertTrue(output.contains("<classified_issues>"), "应含 <classified_issues>");
        assertTrue(output.contains("category=\"interactivity\""), "issue 的 category 属性");
        assertTrue(output.contains("severity=\"major\""), "issue 的 severity 属性");
    }

    // 10. degraded 观察输出含 <degraded> 与 <degraded_reason>
    @Test
    void render_withDegradedObservation_outputsDegradedTags() {
        WorkingMemory memory = new WorkingMemory();
        EvaluationObservation obs = EvaluationObservation.degraded(
                50, "Playwright 超时", Collections.singletonList("[评估] 超时"));
        memory.setLastEvaluationObservation(obs);

        String output = renderer.render(memory);

        assertTrue(output.contains("<degraded>true</degraded>"), "降级标志应输出");
        assertTrue(output.contains("<degraded_reason>Playwright 超时</degraded_reason>"),
                "降级原因应输出");
        assertTrue(output.contains("<total_score>50/100</total_score>"));
    }
}

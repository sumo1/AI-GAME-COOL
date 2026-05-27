package com.sumo.agent.agent.loop;

import com.sumo.agent.agent.evaluation.EvaluationObservation;
import com.sumo.agent.agent.evaluation.ProbeReport;
import com.sumo.agent.agent.skill.SkillDefinition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    // 11. (Step 3) 默认 WorkingMemory 不输出 <control_signals> / <run_trace_summary>
    //     —— 维持与 Step 1/2 字节级相等基线。
    @Test
    void render_defaultMemory_omitsControlSignalsAndTrace() {
        WorkingMemory memory = new WorkingMemory();

        String output = renderer.render(memory);

        assertFalse(output.contains("<control_signals>"),
                "全 false 信号不应输出 <control_signals>");
        assertFalse(output.contains("<run_trace_summary>"),
                "空 trace 不应输出 <run_trace_summary>");
    }

    // 12. (Step 3) 设置了 trace + 信号后，render 输出含两块
    @Test
    void render_withTraceAndSignals_outputsBothBlocks() {
        WorkingMemory memory = new WorkingMemory();

        // 一条 trace
        TraceEntry entry = new TraceEntry();
        entry.setIteration(2);
        entry.setScoreBefore(60);
        entry.setScoreAfter(78);
        entry.setIssueCount(1);
        entry.setResponseLength(1234);
        entry.setGameVersion(2);
        entry.setSummary("score 60→78 (+18)");
        memory.getRunTrace().append(entry);

        // 至少一个信号为 true
        ControlSignals signals = new ControlSignals();
        signals.setScoreImproved(true);
        signals.setEvaluationDegraded(true);
        memory.setControlSignals(signals);

        String output = renderer.render(memory);

        assertTrue(output.contains("<control_signals>"), "应输出 <control_signals>");
        assertTrue(output.contains("<score_improved>true</score_improved>"));
        assertTrue(output.contains("<evaluation_degraded>true</evaluation_degraded>"));
        // 没设的信号不应出现
        assertFalse(output.contains("<same_issues_repeated>"),
                "未置位的信号不应渲染");
        assertFalse(output.contains("<critical_issue_exists>"),
                "未置位的信号不应渲染");
        assertFalse(output.contains("<should_full_rewrite>"),
                "未置位的信号不应渲染");

        assertTrue(output.contains("<run_trace_summary>"), "应输出 <run_trace_summary>");
        assertTrue(output.contains("iteration=\"2\""), "应含本轮 iteration 属性");
        assertTrue(output.contains("version=\"2\""), "应含 version 属性");
        assertTrue(output.contains("score 60→78 (+18)"), "应含本轮 summary");
    }

    // 14. (Step 1 / 260524) 设置 skillIndex 后 render 输出含 <skill_index> 与子标签
    @Test
    void render_withSkillIndex_outputsSkillIndexBlock() {
        WorkingMemory memory = new WorkingMemory();

        SkillDefinition s1 = new SkillDefinition();
        s1.setName("snake-adventure");
        s1.setDescription("生成贪吃蛇互动游戏，支持键盘控制");

        SkillDefinition s2 = new SkillDefinition();
        s2.setName("math-adventure");
        s2.setDescription("生成 4-8 岁儿童的数学加减法互动游戏");

        memory.setSkillIndex(List.of(s1, s2));

        String output = renderer.render(memory);

        assertTrue(output.contains("<skill_index>"), "应输出 <skill_index> 起始标签");
        assertTrue(output.contains("</skill_index>"), "应输出 </skill_index> 结束标签");
        assertTrue(output.contains("<skill name=\"snake-adventure\">"),
                "应含第一个 skill 的 name 属性");
        assertTrue(output.contains("生成贪吃蛇互动游戏，支持键盘控制"),
                "应含第一个 skill 的 description");
        assertTrue(output.contains("<skill name=\"math-adventure\">"),
                "应含第二个 skill 的 name 属性");
    }

    // 15. (Step 1) description 超过 120 字符时被截断到 120+"..."
    @Test
    void render_skillIndex_descriptionLongerThan120_isTruncated() {
        WorkingMemory memory = new WorkingMemory();

        StringBuilder longDescBuilder = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longDescBuilder.append('a');
        }
        String longDesc = longDescBuilder.toString();

        SkillDefinition s = new SkillDefinition();
        s.setName("loooong-skill");
        s.setDescription(longDesc);
        memory.setSkillIndex(List.of(s));

        String output = renderer.render(memory);

        assertTrue(output.contains(longDesc.substring(0, 120) + "..."),
                "description 超过 120 字符应被截断到 120+\"...\"");
        assertFalse(output.contains(longDesc),
                "原始的 200 字符 description 不应完整出现");
    }

    // 16. (Step 1) description 含 < > & 等 XML 特殊字符时被 escape
    @Test
    void render_skillIndex_descriptionWithXmlSpecialChars_isEscaped() {
        WorkingMemory memory = new WorkingMemory();

        SkillDefinition s = new SkillDefinition();
        s.setName("evil-skill");
        s.setDescription("含 <script> 与 & 还有 > 字符");
        memory.setSkillIndex(List.of(s));

        String output = renderer.render(memory);

        assertTrue(output.contains("&lt;script&gt;"), "< > 应被 escape");
        assertTrue(output.contains("&amp;"), "& 应被 escape");
        // 原始未 escape 的 <script> 不应出现
        assertFalse(output.contains("<script>"),
                "原始未 escape 的 <script> 不应出现在输出");
    }

    // 17. (Step 1) 默认 WorkingMemory（skillIndex 为空 List）不输出 <skill_index>
    //     —— 显式守护字节级相等基线（与用例 #8 互补）。
    @Test
    void render_defaultMemory_omitsSkillIndexBlock() {
        WorkingMemory memory = new WorkingMemory();

        String output = renderer.render(memory);

        assertFalse(output.contains("<skill_index>"),
                "默认空 skillIndex 不应输出 <skill_index> 块");
        assertFalse(output.contains("</skill_index>"),
                "默认空 skillIndex 不应输出 </skill_index>");
    }

    // 13. (Step 3) 超过 3 条 trace 时仅输出最近 3 条
    @Test
    void render_traceMoreThanThreeRounds_keepsOnlyLastThree() {
        WorkingMemory memory = new WorkingMemory();
        for (int i = 1; i <= 5; i++) {
            TraceEntry e = new TraceEntry();
            e.setIteration(i);
            e.setGameVersion(i);
            e.setSummary("round-" + i);
            memory.getRunTrace().append(e);
        }
        // 信号不需要为 true，只测 trace 截断
        String output = renderer.render(memory);

        assertTrue(output.contains("round-3"), "最近 3 轮：第 3 轮应在");
        assertTrue(output.contains("round-4"), "最近 3 轮：第 4 轮应在");
        assertTrue(output.contains("round-5"), "最近 3 轮：第 5 轮应在");
        assertFalse(output.contains("round-1"), "第 1 轮不应在");
        assertFalse(output.contains("round-2"), "第 2 轮不应在");
    }
}

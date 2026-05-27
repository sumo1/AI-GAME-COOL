package com.sumo.agent.agent.loop;

import com.sumo.agent.agent.evaluation.EvaluationObservation;
import com.sumo.agent.agent.evaluation.ObservationIssue;
import com.sumo.agent.agent.skill.SkillDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 上下文渲染器 — 把 WorkingMemory 的事实状态序列化为系统提示词中的 XML 片段。
 * <p>
 * 这个类只负责"渲染"：读取 WorkingMemory 字段，输出 XML。它不修改 WorkingMemory。
 * WorkingMemory.toContextXml() 现在委托到这里，保持向后兼容。
 */
public class ContextRenderer {

    /** HTML 摘要阈值：超过此长度时输出摘要而非完整 HTML */
    private static final int HTML_SUMMARY_THRESHOLD = 8000;

    /**
     * 渲染 WorkingMemory 为 XML 上下文片段。
     * <p>
     * memory 为 null 时返回一个语义上的"空 working memory"片段（不抛 NPE），
     * 便于 AgentLoop 在边界场景下仍能拼出合法 prompt。
     */
    public String render(WorkingMemory memory) {
        StringBuilder sb = new StringBuilder();
        sb.append("<working_memory>\n");
        sb.append("  <game_state>\n");

        int gameVersion = memory != null ? memory.getGameVersion() : 0;
        int evalScore = memory != null ? memory.getEvalScore() : 0;
        int iteration = memory != null ? memory.getIteration() : 0;
        int fixCount = memory != null ? memory.getFixCount() : 0;
        List<String> openIssues = memory != null ? memory.getOpenIssues() : List.of();
        String gameHtml = memory != null ? memory.getGameHtml() : null;
        String preloadedSkill = memory != null ? memory.getPreloadedSkill() : null;

        sb.append("    <version>").append(gameVersion).append("</version>\n");
        sb.append("    <last_eval_score>").append(evalScore).append("/100</last_eval_score>\n");
        if (openIssues != null && !openIssues.isEmpty()) {
            sb.append("    <open_issues>\n");
            for (String issue : openIssues) {
                sb.append("      - ").append(issue).append("\n");
            }
            sb.append("    </open_issues>\n");
        }
        sb.append("    <iteration>").append(iteration).append(" of 5</iteration>\n");
        sb.append("    <fix_count>").append(fixCount).append("</fix_count>\n");

        // HTML 摘要：超过阈值时只输出结构摘要
        if (gameHtml != null && !gameHtml.isEmpty()) {
            if (gameHtml.length() > HTML_SUMMARY_THRESHOLD) {
                sb.append("    <html_summary>\n");
                sb.append(summarizeHtml(gameHtml));
                sb.append("\n    </html_summary>\n");
                sb.append("    <html_length>").append(gameHtml.length()).append("</html_length>\n");
            } else {
                sb.append("    <game_html><![CDATA[\n");
                sb.append(gameHtml);
                sb.append("\n    ]]></game_html>\n");
            }
        }

        // 预加载的 Skill 提示
        if (preloadedSkill != null && !preloadedSkill.isEmpty()) {
            sb.append("    <suggested_skill>").append(preloadedSkill).append("</suggested_skill>\n");
        }

        // 评估观察（结构化反馈，由 GameEvaluationTool 写入）
        // 只有显式写入过 lastEvaluationObservation 才输出，保持与 Step 1 输出字节级相等
        EvaluationObservation obs = memory != null ? memory.getLastEvaluationObservation() : null;
        if (obs != null) {
            renderEvaluationObservation(sb, obs);
        }

        // 控制信号（Step 3）：只在至少有一个信号为 true 时输出，
        // 默认全 false 状态保持与 Step 1/2 字节级相等。
        ControlSignals signals = memory != null ? memory.getControlSignals() : null;
        if (signals != null && hasAnyTrueSignal(signals)) {
            renderControlSignals(sb, signals);
        }

        // 轻量轨迹（Step 3）：trace 非空才输出，最多 3 条最近迭代摘要。
        RunTrace trace = memory != null ? memory.getRunTrace() : null;
        if (trace != null && !trace.getEntries().isEmpty()) {
            renderRunTraceSummary(sb, trace);
        }

        // Skill Index（任务 260524 Step 1）：让 LLM 直接看到全部 Skill 摘要。
        // 守卫：skillIndex 为空时不输出此块，保 ContextRendererTest 用例 #8 字节级相等。
        List<SkillDefinition> skillIndex = memory != null ? memory.getSkillIndex() : null;
        if (skillIndex != null && !skillIndex.isEmpty()) {
            renderSkillIndex(sb, skillIndex);
        }

        sb.append("  </game_state>\n");
        sb.append("</working_memory>");
        return sb.toString();
    }

    /**
     * 渲染 Skill Index 块（Step 1）。description 截断到 120 字符避免 prompt 膨胀，
     * 对 name/description 做 XML escape 防止特殊字符破坏结构。
     */
    private void renderSkillIndex(StringBuilder sb, List<SkillDefinition> skillIndex) {
        sb.append("    <skill_index>\n");
        for (SkillDefinition s : skillIndex) {
            if (s == null || s.getName() == null) {
                continue;
            }
            String desc = s.getDescription() != null ? s.getDescription() : "";
            if (desc.length() > 120) {
                desc = desc.substring(0, 120) + "...";
            }
            sb.append("      <skill name=\"").append(escapeXmlAttr(s.getName())).append("\">")
              .append(escapeXmlText(desc))
              .append("</skill>\n");
        }
        sb.append("    </skill_index>\n");
    }

    private static String escapeXmlAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeXmlText(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * 至少一个信号为 true 才需要渲染 control_signals 块，
     * 借此保持空状态下与 Step 1/2 输出字节级相等。
     */
    private boolean hasAnyTrueSignal(ControlSignals s) {
        return s.isScoreImproved()
                || s.isSameIssuesRepeated()
                || s.isCriticalIssueExists()
                || s.isEvaluationDegraded()
                || s.isShouldFullRewrite();
    }

    private void renderControlSignals(StringBuilder sb, ControlSignals signals) {
        sb.append("    <control_signals>\n");
        if (signals.isScoreImproved())       sb.append("      <score_improved>true</score_improved>\n");
        if (signals.isSameIssuesRepeated())  sb.append("      <same_issues_repeated>true</same_issues_repeated>\n");
        if (signals.isCriticalIssueExists()) sb.append("      <critical_issue_exists>true</critical_issue_exists>\n");
        if (signals.isEvaluationDegraded())  sb.append("      <evaluation_degraded>true</evaluation_degraded>\n");
        if (signals.isShouldFullRewrite())   sb.append("      <should_full_rewrite>true</should_full_rewrite>\n");
        sb.append("    </control_signals>\n");
    }

    private void renderRunTraceSummary(StringBuilder sb, RunTrace trace) {
        List<TraceEntry> recent = trace.recent(3);
        if (recent.isEmpty()) {
            return;
        }
        sb.append("    <run_trace_summary>\n");
        for (TraceEntry te : recent) {
            sb.append("      <round iteration=\"").append(te.getIteration())
              .append("\" version=\"").append(te.getGameVersion()).append("\">")
              .append(te.getSummary() != null ? te.getSummary() : "")
              .append("</round>\n");
        }
        sb.append("    </run_trace_summary>\n");
    }

    /**
     * 渲染 EvaluationObservation 为 XML 子树，缩进 4 空格（处于 game_state 内部）。
     */
    private void renderEvaluationObservation(StringBuilder sb, EvaluationObservation obs) {
        sb.append("    <evaluation_observation>\n");
        if (obs.isDegraded()) {
            sb.append("      <degraded>true</degraded>\n");
            if (obs.getDegradedReason() != null) {
                sb.append("      <degraded_reason>").append(obs.getDegradedReason()).append("</degraded_reason>\n");
            }
        }
        sb.append("      <total_score>").append(obs.getTotalScore()).append("/100</total_score>\n");

        if (obs.getScoresByDimension() != null && !obs.getScoresByDimension().isEmpty()) {
            sb.append("      <scores>\n");
            // 固定顺序输出，避免不同 JVM Map 实现差异
            String[] dims = {"runnability", "layout", "interactivity", "completeness", "education"};
            for (String d : dims) {
                Integer s = obs.getScoresByDimension().get(d);
                if (s != null) {
                    sb.append("        <").append(d).append(">").append(s).append("/20</").append(d).append(">\n");
                }
            }
            sb.append("      </scores>\n");
        }

        EvaluationObservation.ProbeSummary ps = obs.getProbeSummary();
        if (ps != null) {
            sb.append("      <probe_summary>\n");
            sb.append("        <page_loaded>").append(ps.isPageLoaded()).append("</page_loaded>\n");
            sb.append("        <js_errors>").append(ps.getJsErrorCount()).append("</js_errors>\n");
            sb.append("        <events>").append(ps.getEventCount()).append("</events>\n");
            sb.append("        <dom_mutations>").append(ps.getDomMutationsCount()).append("</dom_mutations>\n");
            sb.append("        <out_of_bounds>").append(ps.getOutOfBoundsCount()).append("</out_of_bounds>\n");
            if (ps.getFinalScore() != null) {
                sb.append("        <final_score>").append(ps.getFinalScore()).append("</final_score>\n");
            }
            sb.append("      </probe_summary>\n");
        }

        if (obs.getIssues() != null && !obs.getIssues().isEmpty()) {
            sb.append("      <classified_issues>\n");
            for (ObservationIssue iss : obs.getIssues()) {
                sb.append("        <issue category=\"").append(iss.getCategory())
                  .append("\" severity=\"").append(iss.getSeverity()).append("\">")
                  .append(iss.getMessage()).append("</issue>\n");
            }
            sb.append("      </classified_issues>\n");
        }

        sb.append("    </evaluation_observation>\n");
    }

    /**
     * 提取 HTML 摘要：保留结构、关键函数名、CSS 类名，省略具体实现细节。
     * 完整 HTML 始终通过 WorkingMemory.getGameHtml() 获取。
     */
    String summarizeHtml(String gameHtml) {
        if (gameHtml == null || gameHtml.isEmpty()) {
            return "(无游戏 HTML)";
        }
        if (gameHtml.length() <= HTML_SUMMARY_THRESHOLD) {
            return gameHtml;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("      [HTML 摘要, 完整长度: ").append(gameHtml.length()).append(" 字符]\n");

        // 提取 <title>
        Matcher titleMatcher = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL).matcher(gameHtml);
        if (titleMatcher.find()) {
            summary.append("      标题: ").append(titleMatcher.group(1).trim()).append("\n");
        }

        // 提取 CSS 类名（从 class= 属性中提取）
        List<String> cssClasses = new ArrayList<>();
        Matcher classMatcher = Pattern.compile("class=[\"']([^\"']+)[\"']").matcher(gameHtml);
        while (classMatcher.find() && cssClasses.size() < 20) {
            String[] classes = classMatcher.group(1).split("\\s+");
            for (String cls : classes) {
                if (!cls.isEmpty() && !cssClasses.contains(cls)) {
                    cssClasses.add(cls);
                }
            }
        }
        if (!cssClasses.isEmpty()) {
            summary.append("      CSS 类名: ").append(String.join(", ", cssClasses.subList(0, Math.min(cssClasses.size(), 20)))).append("\n");
        }

        // 提取 JS 函数名（function xxx）
        List<String> functions = new ArrayList<>();
        Matcher fnMatcher = Pattern.compile("function\\s+(\\w+)\\s*\\(").matcher(gameHtml);
        while (fnMatcher.find() && functions.size() < 15) {
            String fnName = fnMatcher.group(1);
            if (!functions.contains(fnName)) {
                functions.add(fnName);
            }
        }
        if (!functions.isEmpty()) {
            summary.append("      JS 函数: ").append(String.join(", ", functions)).append("\n");
        }

        // 提取 id 属性
        List<String> ids = new ArrayList<>();
        Matcher idMatcher = Pattern.compile("id=[\"'](\\w[^\"']*)[\"']").matcher(gameHtml);
        while (idMatcher.find() && ids.size() < 15) {
            String id = idMatcher.group(1);
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        if (!ids.isEmpty()) {
            summary.append("      元素 ID: ").append(String.join(", ", ids)).append("\n");
        }

        // 提取 HTML 结构骨架（顶层标签）
        summary.append("      结构: ");
        boolean hasCanvas = gameHtml.contains("<canvas");
        boolean hasSvg = gameHtml.contains("<svg");
        boolean hasAudio = gameHtml.contains("<audio");
        List<String> tags = new ArrayList<>();
        if (hasCanvas) tags.add("canvas");
        if (hasSvg) tags.add("svg");
        if (hasAudio) tags.add("audio");
        if (gameHtml.contains("<style")) tags.add("style(内联)");
        if (gameHtml.contains("<script")) tags.add("script(内联)");
        summary.append(tags.isEmpty() ? "基本 HTML" : String.join(", ", tags));
        summary.append("\n");

        return summary.toString();
    }
}

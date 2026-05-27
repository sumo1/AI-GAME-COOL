package com.sumo.agent.agent.loop;

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

        sb.append("  </game_state>\n");
        sb.append("</working_memory>");
        return sb.toString();
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

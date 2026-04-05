package com.sumo.agent.agent.loop;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 工作记忆 — 追踪当前迭代状态
 * <p>
 * 对标 Agent Harness 的 WorkingMemoryCursors：
 * game_version / eval_score / issue_count / iteration
 */
public class WorkingMemory {

    /** HTML 摘要阈值：超过此长度时 toContextXml() 输出摘要而非完整 HTML */
    private static final int HTML_SUMMARY_THRESHOLD = 8000;

    private int gameVersion = 0;
    private int evalScore = 0;
    private int issueCount = 0;
    private int iteration = 0;
    private int fixCount = 0;
    private String gameHtml;
    private String gameTitle;
    private String preloadedSkill;
    private final List<String> openIssues = new ArrayList<>();

    public void incrementGameVersion() {
        gameVersion++;
    }

    /**
     * 渲染为 XML 上下文片段，注入到系统提示词中。
     * 当 gameHtml 超过阈值时，只输出摘要版本以减少 token 消耗。
     */
    public String toContextXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<working_memory>\n");
        sb.append("  <game_state>\n");
        sb.append("    <version>").append(gameVersion).append("</version>\n");
        sb.append("    <last_eval_score>").append(evalScore).append("/100</last_eval_score>\n");
        if (!openIssues.isEmpty()) {
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
                sb.append(getHtmlSummary());
                sb.append("\n    </html_summary>\n");
                sb.append("    <html_length>").append(gameHtml.length()).append("</html_length>\n");
            } else {
                sb.append("    <game_html><![CDATA[\n");
                sb.append(gameHtml);
                sb.append("\n    ]]></game_html>\n");
            }
        }

        // 预加载的 Skill 提示（SkillsTool 的 available_skills 中已有完整列表，这里只做提示）
        if (preloadedSkill != null && !preloadedSkill.isEmpty()) {
            sb.append("    <suggested_skill>").append(preloadedSkill).append("</suggested_skill>\n");
        }

        sb.append("  </game_state>\n");
        sb.append("</working_memory>");
        return sb.toString();
    }

    /**
     * 提取 HTML 摘要：保留结构、关键函数名、CSS 类名，省略具体实现细节。
     * 完整 HTML 始终通过 getGameHtml() 获取。
     */
    public String getHtmlSummary() {
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

    // --- getters / setters ---

    public int getGameVersion() { return gameVersion; }
    public void setGameVersion(int gameVersion) { this.gameVersion = gameVersion; }

    public int getEvalScore() { return evalScore; }
    public void setEvalScore(int evalScore) { this.evalScore = evalScore; }

    public int getIssueCount() { return issueCount; }
    public void setIssueCount(int issueCount) { this.issueCount = issueCount; }

    public int getIteration() { return iteration; }
    public void setIteration(int iteration) { this.iteration = iteration; }

    public int getFixCount() { return fixCount; }
    public void setFixCount(int fixCount) { this.fixCount = fixCount; }

    public String getGameHtml() { return gameHtml; }
    public void setGameHtml(String gameHtml) { this.gameHtml = gameHtml; }

    public String getGameTitle() { return gameTitle; }
    public void setGameTitle(String gameTitle) { this.gameTitle = gameTitle; }

    public String getPreloadedSkill() { return preloadedSkill; }
    public void setPreloadedSkill(String preloadedSkill) { this.preloadedSkill = preloadedSkill; }

    public List<String> getOpenIssues() { return openIssues; }
}

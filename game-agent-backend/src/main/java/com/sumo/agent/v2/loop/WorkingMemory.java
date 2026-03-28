package com.sumo.agent.v2.loop;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 工作记忆 — 追踪当前迭代状态
 * <p>
 * 对标 Agent Harness 的 WorkingMemoryCursors：
 * game_version / eval_score / issue_count / iteration
 */
public class WorkingMemory {

    private int gameVersion = 0;
    private int evalScore = 0;
    private int issueCount = 0;
    private int iteration = 0;
    private String gameHtml;
    private String gameTitle;
    private final List<String> openIssues = new ArrayList<>();

    public void incrementGameVersion() {
        gameVersion++;
    }

    /**
     * 渲染为 XML 上下文片段，注入到系统提示词中
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
        sb.append("  </game_state>\n");
        sb.append("</working_memory>");
        return sb.toString();
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

    public String getGameHtml() { return gameHtml; }
    public void setGameHtml(String gameHtml) { this.gameHtml = gameHtml; }

    public String getGameTitle() { return gameTitle; }
    public void setGameTitle(String gameTitle) { this.gameTitle = gameTitle; }

    public List<String> getOpenIssues() { return openIssues; }
}

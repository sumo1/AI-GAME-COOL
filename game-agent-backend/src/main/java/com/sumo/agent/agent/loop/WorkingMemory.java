package com.sumo.agent.agent.loop;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 工作记忆 — 追踪当前迭代状态
 * <p>
 * 对标 Agent Harness 的 WorkingMemoryCursors：
 * game_version / eval_score / issue_count / iteration
 * <p>
 * 渲染 XML 上下文片段的逻辑已迁移到 {@link ContextRenderer}。本类只持有事实状态。
 * {@link #toContextXml()} 保留为向后兼容入口，内部委托 ContextRenderer。
 */
public class WorkingMemory {

    /** 共享的渲染器实例（无状态，可复用） */
    private static final ContextRenderer CONTEXT_RENDERER = new ContextRenderer();

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
     * <p>
     * 内部委托 {@link ContextRenderer}，保留此方法作为向后兼容入口。
     * 新代码应直接使用 {@code new ContextRenderer().render(memory)}。
     */
    public String toContextXml() {
        return CONTEXT_RENDERER.render(this);
    }

    /**
     * 提取 HTML 摘要：保留结构、关键函数名、CSS 类名，省略具体实现细节。
     * 委托 {@link ContextRenderer#summarizeHtml(String)}，保留此方法作为向后兼容入口。
     */
    public String getHtmlSummary() {
        return CONTEXT_RENDERER.summarizeHtml(gameHtml);
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

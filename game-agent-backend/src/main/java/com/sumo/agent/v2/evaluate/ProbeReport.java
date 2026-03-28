package com.sumo.agent.v2.evaluate;

import java.util.List;
import java.util.Map;

/**
 * Game Runtime Probe 的结构化报告 — 对应 window.__GAME_PROBE__ 的 Java 模型
 */
public class ProbeReport {

    private long startTime;
    private List<ProbeError> errors;
    private List<ProbeEvent> events;
    private List<StateChange> stateChanges;
    private List<OutOfBoundsElement> outOfBoundsElements;
    private int domMutationsCount;
    private List<String> stateTransitions;
    private FinalState finalState;
    private List<Long> responseLatencies;
    private List<ConsoleWarning> consoleWarnings;

    /** 页面是否成功加载（非白屏） */
    private boolean pageLoaded;

    // === 内部数据类 ===

    public static class ProbeError {
        private String msg;
        private String file;
        private int line;
        private int col;
        private long ts;

        public String getMsg() { return msg; }
        public void setMsg(String msg) { this.msg = msg; }
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        public int getLine() { return line; }
        public void setLine(int line) { this.line = line; }
        public int getCol() { return col; }
        public void setCol(int col) { this.col = col; }
        public long getTs() { return ts; }
        public void setTs(long ts) { this.ts = ts; }
    }

    public static class ProbeEvent {
        private String type;
        private String target;
        private String text;
        private long ts;
        private String key;
        private Integer scoreBefore;
        private Integer scoreAfter;
        private Boolean domChanged;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public long getTs() { return ts; }
        public void setTs(long ts) { this.ts = ts; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public Integer getScoreBefore() { return scoreBefore; }
        public void setScoreBefore(Integer scoreBefore) { this.scoreBefore = scoreBefore; }
        public Integer getScoreAfter() { return scoreAfter; }
        public void setScoreAfter(Integer scoreAfter) { this.scoreAfter = scoreAfter; }
        public Boolean getDomChanged() { return domChanged; }
        public void setDomChanged(Boolean domChanged) { this.domChanged = domChanged; }
    }

    public static class StateChange {
        private String type;
        private Object from;
        private Object to;
        private long ts;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Object getFrom() { return from; }
        public void setFrom(Object from) { this.from = from; }
        public Object getTo() { return to; }
        public void setTo(Object to) { this.to = to; }
        public long getTs() { return ts; }
        public void setTs(long ts) { this.ts = ts; }
    }

    public static class OutOfBoundsElement {
        private String element;
        private Map<String, Integer> rect;
        private Map<String, Integer> viewport;

        public String getElement() { return element; }
        public void setElement(String element) { this.element = element; }
        public Map<String, Integer> getRect() { return rect; }
        public void setRect(Map<String, Integer> rect) { this.rect = rect; }
        public Map<String, Integer> getViewport() { return viewport; }
        public void setViewport(Map<String, Integer> viewport) { this.viewport = viewport; }
    }

    public static class FinalState {
        private Integer score;
        private String stateText;
        private int totalEvents;
        private int totalErrors;
        private int totalDomMutations;
        private int totalStateChanges;
        private long durationMs;

        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public String getStateText() { return stateText; }
        public void setStateText(String stateText) { this.stateText = stateText; }
        public int getTotalEvents() { return totalEvents; }
        public void setTotalEvents(int totalEvents) { this.totalEvents = totalEvents; }
        public int getTotalErrors() { return totalErrors; }
        public void setTotalErrors(int totalErrors) { this.totalErrors = totalErrors; }
        public int getTotalDomMutations() { return totalDomMutations; }
        public void setTotalDomMutations(int totalDomMutations) { this.totalDomMutations = totalDomMutations; }
        public int getTotalStateChanges() { return totalStateChanges; }
        public void setTotalStateChanges(int totalStateChanges) { this.totalStateChanges = totalStateChanges; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }

    public static class ConsoleWarning {
        private String msg;
        private long ts;

        public String getMsg() { return msg; }
        public void setMsg(String msg) { this.msg = msg; }
        public long getTs() { return ts; }
        public void setTs(long ts) { this.ts = ts; }
    }

    // === 评分结果 ===

    private int runnabilityScore;
    private int layoutScore;
    private int interactivityScore;
    private int educationScore;
    private int completenessScore;
    private int totalScore;
    private List<String> issues;

    // === Getters / Setters ===

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public List<ProbeError> getErrors() { return errors; }
    public void setErrors(List<ProbeError> errors) { this.errors = errors; }

    public List<ProbeEvent> getEvents() { return events; }
    public void setEvents(List<ProbeEvent> events) { this.events = events; }

    public List<StateChange> getStateChanges() { return stateChanges; }
    public void setStateChanges(List<StateChange> stateChanges) { this.stateChanges = stateChanges; }

    public List<OutOfBoundsElement> getOutOfBoundsElements() { return outOfBoundsElements; }
    public void setOutOfBoundsElements(List<OutOfBoundsElement> outOfBoundsElements) { this.outOfBoundsElements = outOfBoundsElements; }

    public int getDomMutationsCount() { return domMutationsCount; }
    public void setDomMutationsCount(int domMutationsCount) { this.domMutationsCount = domMutationsCount; }

    public List<String> getStateTransitions() { return stateTransitions; }
    public void setStateTransitions(List<String> stateTransitions) { this.stateTransitions = stateTransitions; }

    public FinalState getFinalState() { return finalState; }
    public void setFinalState(FinalState finalState) { this.finalState = finalState; }

    public List<Long> getResponseLatencies() { return responseLatencies; }
    public void setResponseLatencies(List<Long> responseLatencies) { this.responseLatencies = responseLatencies; }

    public List<ConsoleWarning> getConsoleWarnings() { return consoleWarnings; }
    public void setConsoleWarnings(List<ConsoleWarning> consoleWarnings) { this.consoleWarnings = consoleWarnings; }

    public boolean isPageLoaded() { return pageLoaded; }
    public void setPageLoaded(boolean pageLoaded) { this.pageLoaded = pageLoaded; }

    public int getRunnabilityScore() { return runnabilityScore; }
    public void setRunnabilityScore(int runnabilityScore) { this.runnabilityScore = runnabilityScore; }

    public int getLayoutScore() { return layoutScore; }
    public void setLayoutScore(int layoutScore) { this.layoutScore = layoutScore; }

    public int getInteractivityScore() { return interactivityScore; }
    public void setInteractivityScore(int interactivityScore) { this.interactivityScore = interactivityScore; }

    public int getEducationScore() { return educationScore; }
    public void setEducationScore(int educationScore) { this.educationScore = educationScore; }

    public int getCompletenessScore() { return completenessScore; }
    public void setCompletenessScore(int completenessScore) { this.completenessScore = completenessScore; }

    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }

    public List<String> getIssues() { return issues; }
    public void setIssues(List<String> issues) { this.issues = issues; }
}

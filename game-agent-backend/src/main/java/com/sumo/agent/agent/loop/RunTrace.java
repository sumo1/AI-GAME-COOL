package com.sumo.agent.agent.loop;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentLoop 单次会话的轻量轨迹 — 按迭代顺序追加 {@link TraceEntry}，仅内存驻留。
 * <p>
 * 不落库、不全量进 prompt，只在 {@link ContextRenderer} 中渲染最近若干条摘要。
 */
public class RunTrace {

    /** 已完成的迭代条目，按 iteration 升序追加 */
    private final List<TraceEntry> entries = new ArrayList<>();

    /**
     * 追加一条 TraceEntry。
     */
    public void append(TraceEntry entry) {
        if (entry == null) {
            return;
        }
        entries.add(entry);
    }

    public List<TraceEntry> getEntries() {
        return entries;
    }

    /**
     * 取最近 N 条；不足 N 条返回全部；n &lt;= 0 或 trace 为空时返回空列表。
     */
    public List<TraceEntry> recent(int n) {
        if (n <= 0 || entries.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, entries.size() - n);
        return List.copyOf(entries.subList(from, entries.size()));
    }

    /**
     * 上一轮（最近一条）；空 trace 返回 null。
     */
    public TraceEntry last() {
        if (entries.isEmpty()) {
            return null;
        }
        return entries.get(entries.size() - 1);
    }
}

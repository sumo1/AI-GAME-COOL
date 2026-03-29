package com.sumo.agent.agent.tools;

import com.sumo.agent.agent.loop.WorkingMemory;
import org.springframework.stereotype.Component;

/**
 * 工具上下文 — 拆分后的多个 Tool Bean 共享 WorkingMemory 的桥梁。
 * <p>
 * AgentLoop 每次 run() 时设置当前 WorkingMemory，
 * 各 Tool Bean 通过 ToolContext 读写共享状态。
 */
@Component
public class ToolContext {

    private WorkingMemory workingMemory;

    /** 累计修复次数，第 4 次起全量重写 */
    private int fixCount = 0;

    public void init(WorkingMemory memory) {
        this.workingMemory = memory;
        this.fixCount = 0;
    }

    public WorkingMemory getWorkingMemory() {
        return workingMemory;
    }

    public int incrementAndGetFixCount() {
        return ++fixCount;
    }

    public int getFixCount() {
        return fixCount;
    }
}

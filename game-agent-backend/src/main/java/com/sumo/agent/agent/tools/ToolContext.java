package com.sumo.agent.agent.tools;

import com.sumo.agent.agent.loop.WorkingMemory;
import com.sumo.agent.agent.skill.SkillDefinition;
import org.springframework.stereotype.Component;

/**
 * 工具上下文 — 拆分后的多个 Tool Bean 共享 per-loop 状态的桥梁。
 * <p>
 * 内部使用 ThreadLocal 隔离并发请求：每个线程（= 每个 HTTP 请求 = 每次 AgentLoop.run()）
 * 持有独立的状态副本，Singleton Bean 之间不会互相污染。
 * <p>
 * AgentLoop 每次 run() 开始时调用 {@link #init(WorkingMemory)} 初始化，
 * 结束时调用 {@link #clear()} 清理，防止 ThreadLocal 泄漏。
 */
@Component
public class ToolContext {

    /**
     * per-loop 的可变状态，ThreadLocal 隔离。
     */
    private static final class State {
        WorkingMemory workingMemory;
        SkillDefinition activeSkill;
        int fixCount;
    }

    private final ThreadLocal<State> state = ThreadLocal.withInitial(State::new);

    /**
     * 初始化当前线程的上下文（AgentLoop.run() 入口处调用）
     */
    public void init(WorkingMemory memory) {
        State s = state.get();
        s.workingMemory = memory;
        s.activeSkill = null;
        s.fixCount = 0;
    }

    /**
     * 清理当前线程的上下文（AgentLoop.run() 结束时调用，防止线程池场景下 ThreadLocal 泄漏）
     */
    public void clear() {
        state.remove();
    }

    public WorkingMemory getWorkingMemory() {
        return state.get().workingMemory;
    }

    public SkillDefinition getActiveSkill() {
        return state.get().activeSkill;
    }

    public void setActiveSkill(SkillDefinition skill) {
        state.get().activeSkill = skill;
    }

    public int incrementAndGetFixCount() {
        return ++state.get().fixCount;
    }

    public int getFixCount() {
        return state.get().fixCount;
    }
}

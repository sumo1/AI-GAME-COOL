package com.sumo.agent.agent.tools;

import com.sumo.agent.agent.loop.WorkingMemory;
import com.sumo.agent.agent.skill.SkillDefinition;
import org.springframework.stereotype.Component;

/**
 * 工具上下文 — 拆分后的多个 Tool Bean 共享状态的桥梁。
 * <p>
 * AgentLoop 每次 run() 时调用 init() 重置状态。
 * 各 Tool Bean 通过 ToolContext 读写：
 * <ul>
 *   <li>WorkingMemory — 游戏 HTML、评分、问题列表</li>
 *   <li>ActiveSkillDefinition — 当前加载的 Skill 元数据（含 gameType）</li>
 *   <li>fixCount — 累计修复次数</li>
 * </ul>
 */
@Component
public class ToolContext {

    private WorkingMemory workingMemory;

    /** 当前激活的 Skill 元数据（由 SkillsToolCallbackWrapper 或 tryPreloadSkill 设置） */
    private SkillDefinition activeSkillDefinition;

    /** 累计修复次数，第 4 次起全量重写 */
    private int fixCount = 0;

    public void init(WorkingMemory memory) {
        this.workingMemory = memory;
        this.activeSkillDefinition = null;
        this.fixCount = 0;
    }

    public WorkingMemory getWorkingMemory() {
        return workingMemory;
    }

    public SkillDefinition getActiveSkillDefinition() {
        return activeSkillDefinition;
    }

    public void setActiveSkillDefinition(SkillDefinition skillDefinition) {
        this.activeSkillDefinition = skillDefinition;
    }

    /**
     * 获取当前激活 Skill 的 gameType（用于 EvaluationCheck 派生）
     */
    public String getActiveGameType() {
        return activeSkillDefinition != null ? activeSkillDefinition.getGameType() : null;
    }

    public int incrementAndGetFixCount() {
        return ++fixCount;
    }

    public int getFixCount() {
        return fixCount;
    }
}

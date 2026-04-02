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
 *   <li>ActiveSkill — 当前加载的 SkillDefinition（纯文本数据，非策略对象）</li>
 *   <li>fixCount — 累计修复次数</li>
 * </ul>
 */
@Component
public class ToolContext {

    private WorkingMemory workingMemory;

    /** 当前激活的 Skill 定义（由 SkillLoadTool 设置） */
    private SkillDefinition activeSkill;

    /** 累计修复次数，第 4 次起全量重写 */
    private int fixCount = 0;

    public void init(WorkingMemory memory) {
        this.workingMemory = memory;
        this.activeSkill = null;
        this.fixCount = 0;
    }

    public WorkingMemory getWorkingMemory() {
        return workingMemory;
    }

    public SkillDefinition getActiveSkill() {
        return activeSkill;
    }

    public void setActiveSkill(SkillDefinition skill) {
        this.activeSkill = skill;
    }

    public int incrementAndGetFixCount() {
        return ++fixCount;
    }

    public int getFixCount() {
        return fixCount;
    }
}

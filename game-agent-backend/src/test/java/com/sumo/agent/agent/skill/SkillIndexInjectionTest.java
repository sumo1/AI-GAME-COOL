package com.sumo.agent.agent.skill;

import com.sumo.agent.agent.loop.ContextRenderer;
import com.sumo.agent.agent.loop.WorkingMemory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skill Index 注入测试（任务 260524 Step 1）。
 * <p>
 * 用真实启动的 SkillLoader 验证：
 * <ul>
 *   <li>listSkills() 返回非空列表，每条含 name + description</li>
 *   <li>WorkingMemory.skillIndex 设置后 ContextRenderer 输出含 &lt;skill_index&gt; 块</li>
 *   <li>默认 WorkingMemory 不输出 &lt;skill_index&gt;（保字节级相等基线）</li>
 * </ul>
 */
@SpringBootTest
class SkillIndexInjectionTest {

    @Autowired
    private SkillLoader skillLoader;

    @Test
    void listSkills_returnsNonEmpty_andEveryEntryHasNameAndDescription() {
        List<SkillDefinition> all = skillLoader.listSkills();

        assertNotNull(all, "listSkills 不应返回 null");
        assertFalse(all.isEmpty(), "resources/skills 下至少有一个 SKILL.md，listSkills 不应为空");
        assertTrue(all.stream().allMatch(s -> s.getName() != null && !s.getName().isBlank()),
                "每个 SkillDefinition 都应有非空 name");
        assertTrue(all.stream().allMatch(s -> s.getDescription() != null && !s.getDescription().isBlank()),
                "每个 SkillDefinition 都应有非空 description");
    }

    @Test
    void render_includesSkillIndex_whenSkillIndexSet() {
        WorkingMemory memory = new WorkingMemory();
        memory.setSkillIndex(skillLoader.listSkills());

        String output = new ContextRenderer().render(memory);

        assertTrue(output.contains("<skill_index>"),
                "skillIndex 已设置，render 应输出 <skill_index> 起始标签");
        assertTrue(output.contains("</skill_index>"),
                "render 应正确闭合 </skill_index>");
        assertTrue(output.contains("<skill name=\""),
                "应含至少一个 <skill name=\"...\"> 子标签");
    }

    @Test
    void render_omitsSkillIndex_onDefaultMemory() {
        WorkingMemory memory = new WorkingMemory();

        String output = new ContextRenderer().render(memory);

        assertFalse(output.contains("<skill_index>"),
                "默认空 skillIndex 时 render 不应输出 <skill_index>");
    }
}

package com.sumo.agent.agent.skill;

/**
 * 结构化修复提示 — "这个症状 → 这么修"。
 * <p>
 * 与通用 fixGame prompt 的区别：这是 Skill 领域知识的编码，
 * 比如数学游戏知道"答案计算错误"的根因是 JS parseInt 问题。
 *
 * @param symptom  症状描述（如"答案计算错误"）
 * @param solution 修复方案（如"检查 JS 算术逻辑，确保使用 parseInt"）
 */
public record FixHint(String symptom, String solution) {

    /** YAML 反序列化需要无参构造 */
    public FixHint() {
        this("", "");
    }
}

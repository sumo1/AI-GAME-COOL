package com.sumo.agent.agent.skill;

/**
 * 结构化修复提示 — "这个症状 → 这么修"。
 * <p>
 * 与通用 fixGame prompt 的区别：这是 Skill 领域知识的编码，
 * 比如数学游戏知道"答案计算错误"的根因是 JS parseInt 问题。
 * <p>
 * 用 POJO 而非 record：SnakeYAML 需要 JavaBean 协议（无参构造 + setter）来反序列化。
 */
public class FixHint {

    private String symptom;
    private String solution;

    public FixHint() {}

    public FixHint(String symptom, String solution) {
        this.symptom = symptom;
        this.solution = solution;
    }

    public String symptom() { return symptom; }
    public String solution() { return solution; }

    // SnakeYAML 需要标准 getter/setter
    public String getSymptom() { return symptom; }
    public void setSymptom(String symptom) { this.symptom = symptom; }

    public String getSolution() { return solution; }
    public void setSolution(String solution) { this.solution = solution; }
}

# Skill Index 主路径优化

## 背景

当前运行时把 `listSkills` 和 `loadSkill` 都作为 Function Calling tool 暴露给 LLM。这个链路符合渐进式披露的基本方向，但主路径不够稳：模型需要先“想起来”调用 `listSkills`，再从结果里选择 `loadSkill`。

用户指出一个关键问题：如果 Skill meta 本来就是运行时确定性事实，那么让 LLM 通过工具调用才知道有哪些 Skill，是把确定性上下文装配交给概率模型。

## 决策

后续链路优化中，把 Skill meta 作为 `Skill Index` 由 Java 侧注入高优先级 prompt；完整 `SKILL.md` 仍通过 `loadSkill(skillName)` 按需加载；`listSkills` 保留为 fallback / debug / filter，不作为主路径。

目标链路：

```text
SkillLoader.listSkills()
  -> Skill Index(name + description + triggers)
  -> System/Developer Prompt
  -> LLM 选择 Skill
  -> loadSkill(skillName)
  -> ToolContext.activeSkill
  -> EvidenceStore.skill_name
```

## 理由

- Skill meta 是“地图”，应该提前给模型；完整 Skill body 是“说明书”，应该按需加载。
- `listSkills` 作为主路径会增加一次 tool round-trip，并且存在模型忘记调用的随机性。
- 保留 `listSkills` 可以兼容现有 Function Calling 工具集合，不破坏用户空间。
- active skill 必须在后续 evidence 层落库，否则第二层蒸馏无法知道失败样本归属哪个 Skill。

## 影响

- 后续实现应修改 `AgentLoop.buildSystemPrompt()` 或新建 `ContextRenderer`，注入 Skill Index。
- `AgentPrompts` 的工具说明需要从“先 listSkills”改成“优先看 Skill Index，必要时 loadSkill”。
- `SkillListTool` 只改描述语义，不删除工具。
- `SkillLoadTool` 继续设置 `ToolContext.activeSkill`，后续 evidence 写入要读取这个事实。
- 如果 Skill 数量超过 prompt 预算，应引入 Java 侧 top-K skill routing，而不是全量注入。

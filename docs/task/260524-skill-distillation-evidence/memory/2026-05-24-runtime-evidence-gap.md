# 运行时证据缺口

## 背景

用户追问当前 Java 运行时是否已经记录了游戏运行数据、评分和结果，能否用于第二层 Skill 蒸馏。代码检查后确认，当前数据库记录主要服务历史查看和游戏回放，不是蒸馏证据链。

## 决策

新增独立任务 `260524-skill-distillation-evidence`。后续启动时先补证据层设计，再进入实现；不在当前阶段直接修改 schema 或 Java 代码。

## 理由

当前已有数据：

- `sessions`：会话、标题、模型、消息数、游戏数
- `messages`：用户输入、assistant 输出、最终迭代次数、最终评分
- `game_runs`：成功生成的最终 HTML、最终评分、迭代次数、收藏状态

当前缺失数据：

- active skill 名称
- 失败样本
- 每轮迭代 trace
- 结构化 `ProbeReport` / issue 明细
- 评测观察的分类摘要
- 候选蒸馏规则的生命周期状态

这些缺口不适合用“多写几段日志”解决。日志不是事实源，后续蒸馏会变成考古。

## 影响

- 后续 schema 设计应优先考虑新增 evidence 表，避免污染现有 `game_runs` 的回放语义。
- `260521-agent-harness` 若先落地 `EvaluationObservation / RunTrace`，本任务应复用这些结构并负责持久化。
- `SKILL.md` 演进仍走人工确认或独立蒸馏任务，运行时不自动写文件。

# 任务初始决策

> 时间：2026-05-21
> 上下文：task-designer 启动阶段
> 来源：与主会话讨论 + 上一任务（260521-game-storage-db）经验

## 结论

本任务（playability-oracle）落地 6 个核心决策：

1. **拆三任务**：oracle / snake-skill-evolve / skill-evolution-loop —— 各自独立可交付
2. **bash 独立工具，不进 Java**：纯外部 oracle，不与 GameEvaluator 抢职能
3. **WASD + 方向键交替**：兼容 LLM 可能用的不同控制方案
4. **严格判定**（baseline vs final + 排除自然变化）：拒绝"自动播放但不响应"伪游戏
5. **调试 v0 → fixed 由 LLM（我）做**：留任务 2，本任务不涉及
6. **中间 step 轻验、Step 3 即任务收口**：按 testing.md §1.4 节奏，避免 evaluator 反复跑真 LLM 烧 token

## 证据

### 拆三任务的证据

如果一锅炖：
- 任务范围模糊（"做 oracle"还是"演进 SKILL"？）
- 中间卡住没法独立交付（oracle 自身 bug → 演进数据不可信 → 文档没素材）
- dreamer 后续不好整理边界

### bash 独立工具的证据

- oracle 站系统外，黑盒观察 LLM 生成的 HTML 行为 —— 这是它的核心定位
- 进 Java 会与 GameEvaluator + Probe + AgentLoop 评分系统耦合，互相打架
- bash + browser-harness + python 已经能跑通，无需引入 JVM 依赖

### 严格判定的证据

LLM 可能生成"自动播放贪吃蛇但完全不响应键盘"的伪游戏（CSS 动画 + setInterval）。如果只看"画面变了"，oracle 会假阳性。
解法：baseline 阶段先采集 1 秒"自然变化扫描"，把那段时间内会自动变的元素加入白名单，验证时排除。

## 被否决的方案

### A：oracle 集成进 GameEvaluator

否决理由：
- 改动大，影响所有 V2 生成的评分逻辑
- GameEvaluator 是给 LLM 自评的（评分写到 DB / 影响 AgentLoop 迭代决策）；oracle 是给端到端验证的（只输出 PASS/FAIL）—— 角色不同
- 一旦集成，oracle 输出影响 LLM 行为，验证就变成"自我验证"了

### B：让用户人工玩验证

否决理由：
- 不可复现、不可自动化
- "SKILL 演进循环"需要每次改动 SKILL 后跑 oracle 多次采样 —— 完全无法人工

### C：在 prompt 里强制 LLM 生成 data-testid

否决理由：
- 那是给 oracle 做 fixture，不是真实交付
- LLM 自由发挥时不会主动加，强制反而让"我们的可玩性测试"和"用户的真实体验"分叉

### D：每个 step 都跑端到端

否决理由：
- 烧 LLM 配额（DashScope free tier 一天用满）
- 中间反馈延迟大（每 step 5-10 分钟）
- testing.md §1.4 已规定"任务收口才重验"

## 影响范围

- 任务结构：4 个 step，Step 3 即收口端到端
- 不变的边界：Java/TS 全栈、agents 体系、GameEvaluator
- 后续任务依赖：snake-skill-evolve 直接调本任务 oracle

## 仍需后续观察的不确定项

- LLM 生成的 v0 是否能用通用 DOM 扫描捕到信号（最坏 case：LLM 用 React + 复杂虚拟 DOM）
- canvas hash 取尾 40 字符的碰撞概率（10^-24 量级，但要在真贪吃蛇上验）
- WASD 与方向键同时发是否会导致 LLM 生成的事件 handler 错乱（unlikely，但有风险）

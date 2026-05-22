# 任务初始决策

> 时间：2026-05-22
> 上下文：task-designer 启动阶段
> 来源：与主会话讨论 + 上一任务（260521-playable-snake-evolution）经验

## 结论

抽公共底层任务的 5 个核心决策：

1. **共享层用 JS（非 Python/Java）** —— 浏览器内执行最自然；Java/Python 各做薄胶水层
2. **路径选 `shared/playability/` 顶层独立目录** —— 跨边界资源不属于 backend 或 scripts
3. **本任务只抽公共层，不修教育评分** —— 教育评分修复需要 SKILL.md 改造，独立 task
4. **一锁定到位** —— 不做并行兼容期。期间评估器临时不准是可接受代价
5. **shared/* 严禁引用项目代码** —— 必须纯 JS + 零依赖。否则不是真共享，是埋耦合

## 证据

### 决策 1：JS 而不是 Python/Java

候选对比：

| 选 | 优 | 劣 |
|---|---|---|
| **JS（选中）** | 浏览器内直接跑、与 game-probe.js 风格一致、Java 用 Playwright addInitScript 一行注入、Python 用 safe_js 注入也一行 | bash 端拼接 JS 字符串需小心 |
| Python | oracle 已用，Java 调要起子进程 / IPC | JVM 外套子进程不优雅 |
| Java | GameEvaluator 已用，oracle 要调 jar 或 native binary | 把 oracle 的"独立轻量"定位变成依赖 JVM |

JS 是唯一两边都自然的语言。

### 决策 2：shared/playability/ 顶层

| 选 | 优 | 劣 |
|---|---|---|
| **shared/playability/（选中）** | 跨边界资源、可发 npm 包 | Java 端读项目根需要找 root path |
| game-agent-backend/src/main/resources/probe/ | Java 零成本 | oracle 跑 ../game-agent-backend/src/...，跨语言读 backend 代码不优雅 |
| scripts/lib/ | oracle 零成本 | Java 端读 ../../scripts/lib，同样不优雅 |

### 决策 3：本任务不动教育评分

教育评分（GameEvaluator 第 5 维永远 = 15）是**SKILL 系统的洞**，不是评估器的洞。修法是 SKILL.md frontmatter 加 `evaluation_signals: [{kind: "dom_count", selector: ".math-question", min: 5}]` 这种结构化校验点，让 GameEvaluator 按 Skill 提供的信号去打分。

这件事独立成 `260522-evaluator-skill-signals` 任务做。本任务范围紧锁"抽公共底层"。

### 决策 4：一锁定到位

并行兼容期方案听起来稳，但实际：
- 增加一倍代码（新旧逻辑都要在）
- 调试窗口窄（哪个版本 bug 难判）
- Step 间依赖错综（cross verification 反而难做）

一锁到位的代价是"任务期内 V2 临时不可生产用"。任务期 < 4 小时（5 step），可接受。

退路：cross-verify 跑出来发现真有重大 bug，临时回滚到任务起始 commit 即可。

### 决策 5：shared 严禁引用项目代码

否则违反"共享底层"本质：如果 shared/playability 引用了 SkillLoader 或 ChatModel，它就**不是共享**而是**Java 项目的 JS 接口**。oracle 用它会变成"oracle 也间接依赖 JVM" → 任务白做。

写进 task-code-reviewer 第 1 条冻结边界。

## 被否决的方案

### A：把 oracle 改写成 Java（用 GameEvaluator 替代 oracle）

- 否决理由：oracle 是"系统外的客观判定"，进 Java 就和 GameEvaluator 共享 SKILL/Tool/Spring 上下文，再不能"客观"
- 详细论证见上一任务的"oracle 与 GameEvaluator 不同"

### B：把 GameEvaluator 改写成 bash（用 oracle 代替 GameEvaluator）

- 否决理由：AgentLoop 在 JVM 内、调 evaluateGame Tool 内联，用 bash 调用回路太长
- 而且 oracle 的 PASS/FAIL 二值不适合 LLM 渐进式迭代决策

### C：抽 npm 包发布

- 否决理由：增加版本管理负担、单仓库目前没必要
- 留作未来扩展（如果有第三方项目要用）

## 影响范围

- 任务结构：5 step + 任务收口在 Step 4
- 不变的边界：AgentLoop / SKILL / 五维评分公式 / oracle 退出码语义
- 后续任务依赖：evaluator-skill-signals / oracle-extend-click-drag / evaluator-keyboard-explore 都依赖本任务的 shared/playability/

## 仍需后续观察的不确定项

- evalScore 系统性下降的实际幅度（Step 4 看真值）
- AgentLoop QUALITY_GATE_SCORE = 80 是否需要临时降低（Step 2 退路）
- shared/playability/* 文件大小（Step 1 验过 < 250 行）
- bash 拼接 JS 字符串的实际可行性（Step 3 验过）

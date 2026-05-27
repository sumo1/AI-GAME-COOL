# 2026-05-27 evaluator 评分系统性下降（Step 2 已知代价）

## 背景

Step 2 把 GameEvaluator 的信号源从 `game-probe.js`（注入 head + 内部全程监听 click/keydown/MutationObserver/state-text-watch）切换到 `shared/playability/playability-probe.js`（addInitScript 注入，纯瞬时信号采集）。

**ProbeReport 字段未变**，但部分字段在新源下永远空/0：

| 字段 | 老 game-probe | 新 shared probe |
|---|---|---|
| `errors` | hook 错误 | hook 错误 ✅ |
| `outOfBoundsElements` | viewport 检测 | GameEvaluator 内联 JS ✅ |
| `pageLoaded` | Java 端判断 | 不变 ✅ |
| `finalState.score` | 监听 score-text 节点 | numeric 节点正则匹配（≈ 兼容）|
| **`events`** | 全程 click/keydown 监听 | **空数组**（设计哲学差异）|
| **`stateChanges`** | 监听 score 节点变更 | **空数组** |
| **`stateTransitions`** | 状态文本 watch | **空列表** |
| **`domMutationsCount`** | MutationObserver | **0** |

## 设计哲学差异

共享 probe 是为 oracle 设计的：**采集"任务前 / 任务后"的瞬时快照**做差异判定，**不挂全局事件流监听器**。这能让多个上层（GameEvaluator / oracle / Browser-MCP）共用同一份 probe 而互不干扰。

老 game-probe 是为 evaluator 单家服务的事件流采集器，假设 probe 与 evaluator 一对一。

## 评分影响

GameEvaluator.computeScores 中受影响的两个维度：

1. **interactivity（满分 20）**
   - 旧路径：clickEvents > 0 且 domChanged → 20 分
   - 新路径：events 永远空 → **走 else 分支 = 0 分**
   - 损失上限：**-20 分**

2. **completeness（满分 20）**
   - 旧路径：hasStateTransitions + hasScoreChanges → 20 分；其一 → 15 分；只有 dom mutations → 10 分
   - 新路径：三个 boolean 永远 false → **0 分**
   - 损失上限：**-20 分**

预期改造后所有游戏 totalScore **系统性下降 20-40 分**。

`AgentLoop.QUALITY_GATE_SCORE = 80` 在过渡期内可能让所有 LLM 生成进 5 轮迭代仍 fail。

## 退路

如果 Step 4 交叉验证发现 LLM 生成在新评分下完全跑不到 80：

- **临时方案**：把 `QUALITY_GATE_SCORE` 调到 50（在 AgentLoop 里）
- **不在本 step 调**，先看 Step 3/4 的实测数据再决定
- 后续任务 `evaluator-keyboard-explore` 会专门加键盘探索 + click 事件计数 + Skill 信号驱动评分，把这两维分恢复**且更准**（基于真信号而非状态文本启发式），再调回 80

## 不上浮 knowledge 的原因

这是**过渡期取舍**——评分降级不是设计目标也不是长期事实，是 evaluator 与 oracle 公共底层抽取过程中临时承担的代价。后续任务恢复后这条 memory 就过期了。所以只放 task memory，不污染跨任务知识库。

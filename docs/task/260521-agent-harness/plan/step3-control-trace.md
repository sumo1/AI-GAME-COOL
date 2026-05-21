# Step 3：控制信号与轻量轨迹

## 背景

当前外层循环只看 `evalScore == 0 || evalScore >= 80`，低于 80 就继续迭代。这个控制策略太粗：无法判断连续无进展、重复问题、评估降级、修复次数过多、是否应该全量重写。

本步骤引入轻量 `ControlSignals` 和 `RunTrace`，先记录事实，再做保守控制，不破坏现有行为。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/AgentLoop.java`
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/WorkingMemory.java`
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/ControlSignals.java`（新建）
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/RunTrace.java`（新建）
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/TraceEntry.java`（新建，可选）
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/ContextRenderer.java`
  - 对应单元测试

- **不可改文件**：
  - `GameEvaluator` 评分算法
  - `AgentPrompts.SYSTEM_PROMPT`
  - `@Tool` 方法签名
  - API 和前端

- **不可新增的抽象**：
  - 不新增完整 event sourcing
  - 不新增数据库持久化 trace
  - 不新增复杂策略引擎

### 产出清单

1. 新增 `RunTrace`：
   - 记录每轮：`iteration`、`scoreBefore`、`scoreAfter`、`issueCount`、`responseLength`、`gameVersion`、`summary`
   - 初始只保存在 `WorkingMemory`，不落库

2. 新增 `ControlSignals`：
   - 字段建议：`scoreImproved`、`sameIssuesRepeated`、`criticalIssueExists`、`evaluationDegraded`、`shouldFullRewrite`
   - 由 `WorkingMemory` 当前状态和上一轮 trace 计算

3. 修改 `AgentLoop`：
   - 每轮 LLM 调用后追加 trace
   - 每轮评估后更新 control signals
   - 初始策略保持保守：不改变 5 轮和 80 分门禁
   - 允许把 `shouldFullRewrite` 渲染给 LLM，但不强行改变工具调用

4. 修改 `ContextRenderer`：
   - 渲染简短 `<control_signals>` 和 `<run_trace_summary>`
   - 只输出最近 2-3 轮摘要，避免上下文膨胀

### 约束（已冻结的边界）

- 不改变 `MAX_ITERATIONS`
- 不改变 `QUALITY_GATE_SCORE`
- 不改变异常时“有 HTML 则返回当前版本”的兼容行为
- 不把完整 trace 全量塞进 prompt
- 不持久化 trace 到 DB

### 复用的现有模式

- `WorkingMemory.iteration/gameVersion/evalScore/issueCount/fixCount` 是信号输入
- `AgentLoop` 外层 for-loop 是控制策略执行点
- `ContextRenderer` 是 LLM 可见上下文出口

### 依赖的前置子任务

依赖 Step 1 和 Step 2。

## 【验收契约（Evaluator 输入）】

### 代码结构验证

- [ ] `ControlSignals.java` 存在
- [ ] `RunTrace.java` 存在
- [ ] `WorkingMemory` 包含 trace/control signals 字段或等价访问方法
- [ ] `AgentLoop` 每轮追加 trace
- [ ] `ContextRenderer` 输出 control signals / trace summary
- [ ] 未新增数据库表

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `mvn -pl game-agent-backend test -Dtest=ContextRendererTest` | exit 0 |
| `mvn -pl game-agent-backend test -Dtest=ControlSignalsTest` | 如新增该测试，exit 0 |
| `mvn -pl game-agent-backend -am compile` | exit 0，无编译错误 |

### 数据/字段验收

- [ ] 分数上升时 `scoreImproved = true`
- [ ] 分数不变或下降时 `scoreImproved = false`
- [ ] 连续两轮问题文本高度相同时 `sameIssuesRepeated = true`
- [ ] 降级评估 observation 存在时 `evaluationDegraded = true`
- [ ] `run_trace_summary` 只包含最近若干轮，不无限增长

### 负面用例

- [ ] 第一轮无上一轮 trace 时不抛异常
- [ ] `openIssues` 为空时重复问题判断为 false
- [ ] LLM 调用异常但已有 HTML 时，原有兼容返回行为不变


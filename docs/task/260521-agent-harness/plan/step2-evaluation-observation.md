# Step 2：评估观察结构化

## 背景

`GameEvaluator` 已经通过 Playwright 和 probe 得到了高价值运行时事实，但 `GameEvaluationTool` 当前只把 `evalScore`、`openIssues` 和 `issueCount` 写回 `WorkingMemory`。这会把环境反馈压扁成字符串，LLM 下一轮修复时缺少证据。

本步骤把 `ProbeReport` 中高信号信息转成结构化观察结果，但不改变 `GameEvaluator.evaluate()` 的返回类型。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/WorkingMemory.java`
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/ContextRenderer.java`
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/evaluation/ProbeReport.java`
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/evaluation/EvaluationObservation.java`（新建）
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/evaluation/ObservationIssue.java`（新建）
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/tools/evaluation/GameEvaluationTool.java`
  - 对应单元测试文件

- **不可改文件**：
  - `GameEvaluator.evaluate(String htmlCode)` 方法签名
  - `AgentLoop.run()` 方法签名
  - `AgentPrompts.java`
  - 前端文件

- **不可新增的抽象**：
  - 不引入通用 `Observation` runtime
  - 不复制 `yuntoo-smartcode` 的 `Observation` / `ToolExecutor`
  - 不把完整 `ProbeReport` 每轮全量塞进 prompt

### 产出清单

1. 新增 `ObservationIssue`：
   - 字段建议：`category`、`severity`、`message`、`evidence`
   - `severity` 用简单枚举或字符串：`critical / major / minor`
   - 从现有 issue 文本中做最小分类，不做复杂 NLP

2. 新增 `EvaluationObservation`：
   - 字段建议：`totalScore`、`scoresByDimension`、`issues`、`probeSummary`
   - `probeSummary` 包含：`pageLoaded`、`jsErrorCount`、`eventCount`、`domMutationsCount`、`outOfBoundsCount`、`stateTransitions`
   - 提供静态工厂：`fromProbeReport(ProbeReport report)`

3. 扩展 `WorkingMemory`：
   - 新增 `EvaluationObservation lastEvaluationObservation`
   - 保留 `evalScore/openIssues/issueCount`，它们作为兼容字段继续更新

4. 修改 `GameEvaluationTool`：
   - 成功评估后写入 `memory.setLastEvaluationObservation(...)`
   - 降级评估也生成一个 observation，标记为降级/timeout
   - 返回给 LLM 的 markdown 报告可以保持现有格式

5. 修改 `ContextRenderer`：
   - 在上下文中追加简短 `<evaluation_observation>` 块
   - 只渲染高信号摘要，不输出完整 report

### 约束（已冻结的边界）

- `ProbeReport` 仍是 `GameEvaluator` 的主返回对象
- `openIssues` 仍更新，兼容旧 prompt 和旧逻辑
- `buildEvalReportText()` 的人类可读返回格式不强制改变
- 不改变评分算法
- 不改变 Playwright 交互步骤

### 复用的现有模式

- `ProbeReport` 是事实来源
- `GameEvaluationTool` 是状态写回点
- `WorkingMemory` 保存 per-loop 状态

### 依赖的前置子任务

依赖 Step 1：`ContextRenderer` 已存在。

## 【验收契约（Evaluator 输入）】

### 代码结构验证

- [ ] `EvaluationObservation.java` 存在
- [ ] `ObservationIssue.java` 存在
- [ ] `WorkingMemory` 包含 `lastEvaluationObservation` getter/setter
- [ ] `GameEvaluationTool` 在成功评估路径写入 `lastEvaluationObservation`
- [ ] `GameEvaluationTool` 在降级评估路径写入 `lastEvaluationObservation`
- [ ] `ContextRenderer` 输出包含 `<evaluation_observation>` 或等价结构

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `mvn -pl game-agent-backend test -Dtest=ContextRendererTest` | exit 0 |
| `mvn -pl game-agent-backend test -Dtest=GameEvaluationToolTest` | 如新增该测试，exit 0 |
| `mvn -pl game-agent-backend -am compile` | exit 0，无编译错误 |

### 数据/字段验收

- [ ] `EvaluationObservation.fromProbeReport()` 能正确映射总分
- [ ] JS 错误数、交互事件数、DOM mutation 数、越界元素数进入 `probeSummary`
- [ ] 原有 `memory.getOpenIssues()` 行为不变
- [ ] 降级评估时 observation 明确包含 timeout/degraded 信号

### 负面用例

- [ ] `fromProbeReport(null)` 不抛 NPE，应返回空 observation 或明确失败语义
- [ ] `report.getIssues() == null` 时 issues 为空列表，不为 null
- [ ] `report.getFinalState() == null` 时不影响 observation 构造


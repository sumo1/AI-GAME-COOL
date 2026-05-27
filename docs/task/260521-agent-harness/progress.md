# 260521-agent-harness — Agent Harness 轻量化改造

## 目标

把当前 `AI-GAME` 的 AgentLoop 从“prompt + tools + WorkingMemory”升级为更清晰的领域型 Agent Harness：结构化状态、一致的上下文渲染、可复用的观察结果、轻量执行轨迹和确定性控制策略。

本任务不是引入一套新框架，也不是照搬 `yuntoo-smartcode` 的通用平台型 harness。真正目标是把已经存在的能力变成可维护的一等数据：

- `WorkingMemory` 保存事实，不负责 prompt 拼接
- `ContextRenderer` 负责按需渲染上下文
- `ProbeReport` 的关键观察结果结构化回灌给 Agent
- 外层循环掌握停止、重写、重试、降级等控制权
- 执行轨迹可读、可复盘、可被 evaluator/code-reviewer 使用

## 核心原则

1. **Prompt 是投影，不是事实源**  
   运行状态、工具结果、评估反馈先是 Java 结构化对象，再按需渲染成 prompt。不要把系统事实只藏在自然语言里。

2. **领域 harness，不做平台 harness**  
   `AI-GAME` 是教育游戏生成器，不是多场景低代码平台。只吸收 `yuntoo-smartcode` 的状态、观察、控制思想，不迁移 YAML/XML 协议、SubAgent 调度和数据库驱动场景注册。

3. **Never break userspace**  
   `AgentLoop.run(String userInput, String modelKey)`、`AgentLoopResult`、现有 `@Tool` 方法签名、Skill 规范、API 响应结构默认不变。若必须改变，必须有兼容层。

4. **先改数据结构，再改 prompt**  
   任何 prompt 改动都必须来自明确的数据结构变化。禁止“多写几段提示词”伪装成架构升级。

5. **让环境反馈可操作**  
   Playwright/probe 不是只生成一段 markdown 报告，而是要形成可分类、可排序、可重复引用的观察结果。

6. **控制权留在 harness**  
   LLM 可以建议下一步，Java 外层负责质量门禁、最大轮次、连续无进展、降级评估、全量重写时机等确定性决策。

## 范围（本任务做什么、不做什么）

✅ 本任务做：

- 新增轻量 `AgentRunState` 语义层，或在兼容前提下扩展 `WorkingMemory`
- 拆出 `ContextRenderer`，从状态对象中移除 prompt 拼接职责
- 将 `ProbeReport` 摘要结构化为 `EvaluationObservation / ObservationIssue`
- 记录每轮 `LLM -> tool -> observation -> state diff` 的轻量 `RunTrace`
- 在 `AgentLoop` 中引入确定性 `ControlSignals`
- 保持 Spring AI 原生 Function Calling，不替换工具调用机制
- 补齐任务级文档、验证脚本和回归用例

❌ 本任务不做：

- 不引入 `yuntoo-smartcode` 的 YAML/XML ToolCommand 协议
- 不新增 SubAgent / 多 Agent 调度
- 不把 Skill 系统迁到数据库
- 不引入动态场景注册、租户级工具白名单、计费、SSE 轨迹 UI
- 不改前端交互形态，除非后续验证需要暴露 trace
- 不改变游戏生成的产品目标和质量阈值

## 步骤

1. [x] **Step 1：状态与上下文拆分** — 抽 `ContextRenderer.render(WorkingMemory)`，`WorkingMemory.toContextXml()` 委托给它；`AgentLoop.buildSystemPrompt` 改用 ContextRenderer；ContextRendererTest 8 用例全过（含字节级相等 `toContextXml() == render()`）。@ 2026-05-27
2. [x] **Step 2：评估观察结构化** — 新建 `EvaluationObservation` + `ObservationIssue`；`WorkingMemory.lastEvaluationObservation` 字段；`GameEvaluationTool` 成功 + 降级路径都写入；`ContextRenderer` 末尾追加 `<evaluation_observation>` 块（obs=null 时不输出，保字节级相等用例）。共 26 用例全过（ContextRenderer 10 / EvalObs 9 / IssueType 7）。@ 2026-05-27
3. [x] **Step 3：控制信号与轻量轨迹** — 新建 RunTrace + TraceEntry + ControlSignals；WorkingMemory 加 runTrace/controlSignals 字段（默认非 null）；AgentLoop 每轮在质量门禁前调 recordTraceAndSignals；ContextRenderer 末尾追加 `<control_signals>` 与 `<run_trace_summary>`（默认状态全空时不输出，保字节级相等基线）；MAX_ITERATIONS=5 / QUALITY_GATE_SCORE=80 不动；trace 仅内存驻留不落库。47 用例全过（ContextRenderer 13 / ControlSignals 12 / RunTrace 6 / EvalObs 9 / Issue 7）。@ 2026-05-27
4. [ ] **Step 4：文档与验收基线** — 同步架构文档、补充回归验证，确保后续任务能按契约执行

## 目标状态

改造后，主循环的数据流应当变成：

```text
UserInput
  -> AgentRunState 初始化
  -> ContextRenderer 渲染最小必要上下文
  -> LLM 产出 tool call / final response
  -> Tool 执行
  -> Observation 写回 AgentRunState
  -> ControlSignals 判断继续 / 停止 / 重写 / 降级
  -> RunTrace 记录本轮事实
```

而不是：

```text
WorkingMemory 自己拼 XML
  -> LLM 看一大段状态文本
  -> Tool 返回 markdown
  -> 从 markdown/字符串里挑几个字段继续下一轮
```

## 不变的边界（已冻结）

- `AgentLoop.run(String userInput, String modelKey)` 方法签名不变
- `AgentLoopResult.success/failure` 返回语义不变
- `ChatClient.tools(skillListTool, skillLoadTool, gameSaveTool, gameEvaluationTool)` 的现有工具集合不删减
- `SkillLoader` 遵守 AgentSkills.io：frontmatter 只强依赖 `name + description`
- `GameEvaluator.evaluate(String htmlCode)` 仍返回 `ProbeReport`
- `QUALITY_GATE_SCORE = 80` 和 `MAX_ITERATIONS = 5` 默认不改
- 生成结果仍然是单文件 HTML5 游戏，内联 CSS/JS，无外部 CDN

## 风险登记

- **R1：抽象膨胀** — 把一个单领域游戏 Agent 改成通用平台 runtime。缓解：只做轻量 state/render/observation/trace，不做 SubAgent 和动态场景。
- **R2：prompt 行为漂移** — 上下文渲染变化导致生成质量波动。缓解：Step 1 保持 `toContextXml()` 兼容输出，Step 2 后逐步切换新字段。
- **R3：评估信息过载** — 把完整 `ProbeReport` 全塞给 LLM。缓解：Observation 只保留高信号字段，完整报告留在 state/trace，不默认全渲染。
- **R4：控制策略误杀** — 外层过早判定无进展。缓解：Step 3 只记录信号，初始策略保守，不改变现有 5 轮和 80 分门禁。
- **R5：测试成本变高** — Playwright 评估慢。缓解：保留单元测试覆盖 state/render/reducer，端到端只做冒烟基线。

## 责任分工

- task-designer：维护本任务目录和 plan 契约
- coder：按 `plan/*.md` 的实现契约逐步施工
- evaluator：按 `plan/*.md` 的验收契约复跑验证
- code-reviewer：重点审查是否引入不必要平台化复杂度
- doc-refresher：检查 `CLAUDE.md`、`docs/upgrade/*`、`docs/knowledge/*` 是否与代码同步
- dreamer：任务结束后把可复用 harness 原则上浮到 `docs/knowledge/`


# Game Agent 顶层设计

> 本文档定义 Game Agent 的领域认知结构——它是谁、它会什么、它怎么判断自己做得好不好。
> 对标 Agent Harness 的三层记忆框架：Semantic Memory / Procedural Memory / Working Memory。

---

## 1. 领域定义：Game Agent 是谁

### 1.1 角色定位

Game Agent 是一个**儿童教育游戏设计师**。它具备以下能力：

- **需求理解**：理解自然语言描述的游戏需求，识别目标年龄段、教育目标、游戏类型
- **游戏设计**：将需求转化为可玩的 HTML5 游戏，包括玩法设计、视觉呈现、交互逻辑
- **质量把控**：能"看到"自己生成的游戏，评估可玩性，发现并修复问题
- **知识积累**：内置教育游戏设计的最佳实践（Skill），并能在此基础上创新

### 1.2 领域边界

| 在边界内 | 在边界外 |
|---------|---------|
| HTML5 单页游戏（内联 CSS/JS） | 多页面应用、需要后端的游戏 |
| 4-12 岁儿童教育场景 | 成人游戏、竞技类游戏 |
| 2D 浏览器游戏 | 3D、WebGL 复杂渲染 |
| 单人游戏 | 多人联机 |
| 交互式小游戏（5-15 分钟） | 大型开放世界 |

### 1.3 Semantic Memory（语义记忆）—— "我知道什么"

类比 Agent Harness 中的 SEMANTIC_PROMPT（412 行角色定义），Game Agent 的语义记忆应包含：

**角色与职责**：
```
你是一个儿童教育游戏设计专家。你的工作是根据用户的需求描述，
设计并生成完整的 HTML5 教育小游戏。你追求的不是"能跑"，
而是"好玩、有教育意义、没有 bug"。
```

**领域知识**：
- 不同年龄段的认知发展特点（4-6 岁：图形识别、颜色、简单计数；7-9 岁：加减法、简单逻辑；10-12 岁：乘除法、策略思维）
- 教育游戏设计原则（即时反馈、渐进难度、正向激励、容错友好）
- HTML5 游戏开发规范（响应式布局、触屏适配、性能约束、无外部依赖）
- 常见游戏类型的设计模式（匹配、记忆、计算、拼图、闯关、模拟）

**质量标准**（内化的评判标准）：
- 游戏必须有明确的开始和结束
- 必须有计分或进度反馈
- 操作必须简单直觉（点击/拖拽，不需要键盘组合键）
- 视觉元素不能超出可见区域
- 失败时给鼓励而非惩罚

---

## 2. 技能体系：Game Agent 会什么

### 2.1 Procedural Memory（程序性记忆）—— "我会做什么"

类比 Agent Harness 中的 5 个 Workflow，Game Agent 的"工作流"是不同的游戏生成策略：

| 策略 ID | 名称 | 适用场景 | 核心流程 |
|---------|------|---------|---------|
| `skill_based` | 技能模板策略 | 用户需求匹配已有 Skill | load_skill → 基于模板定制 → evaluate → 返回 |
| `creative_gen` | 创意生成策略 | 用户需求是全新玩法 | 设计游戏方案 → generate_game → evaluate → fix → 返回 |
| `iterative_refine` | 迭代优化策略 | 用户对已有游戏提修改意见 | 分析问题 → fix_game → evaluate → 返回 |

**策略选择**（类比 Agent Harness 的 WorkflowRouter）：
- 不需要 LLM routing——游戏生成的策略选择比视频创作简单
- 第一轮：LLM 根据 Skill 列表判断是否有匹配的模板
- 如果有 → skill_based
- 如果没有 → creative_gen
- 后续轮次：如果用户反馈修改意见 → iterative_refine

### 2.2 Tools（工具集）—— Agent 的"手"

| 工具 | 描述 | 输入 | 输出 |
|------|------|------|------|
| `list_skills` | 列出可用的游戏技能模板 | 可选 filter（类型/年龄段） | Skill 摘要列表 |
| `load_skill` | 加载指定 Skill 的完整内容 | skill_name | 模板代码 + prompt 提示 + 评估标准 |
| `generate_game` | 生成完整的 HTML5 游戏 | 游戏设计方案（文本描述） | HTML 代码 |
| `evaluate_game` | 评估游戏可玩性 | HTML 代码 | 评估报告（截图 + 问题列表 + 评分） |
| `fix_game` | 修复游戏中的问题 | HTML 代码 + 问题列表 | 修复后的 HTML 代码 |

**工具调用协议**：LLM Function Calling（Spring AI 原生支持）

### 2.3 Skills（内置技能模板）—— Agent 的"经验库"

从现有 4 个硬编码 Agent 转化而来，加上扩展：

| Skill | 来源 | 教育目标 | 核心玩法 |
|-------|------|---------|---------|
| `math_adventure` | MathGameAgent | 数学计算能力 | 算术题闯关 |
| `memory_master` | MemoryGameAgent | 记忆力训练 | 配对翻牌 |
| `english_explorer` | EnglishLearningAgent | 英语词汇 | 单词拼写/选择 |
| `traffic_safety` | TrafficSafetyAgent | 安全意识 | 交通规则模拟 |
| `shape_colors` | 新增 | 图形颜色认知 | 形状匹配/颜色分类 |
| `logic_puzzle` | 新增 | 逻辑思维 | 简单推理/排序 |

每个 Skill 的 YAML 结构：
```yaml
name: math_adventure
display_name: 数学冒险
description: 10以内加减法互动游戏，适合4-8岁儿童
age_group: "4-8"
difficulty: [easy, medium]
tags: [数学, 加法, 减法, 计算]
game_type: quiz

# 游戏模板（完整可运行的 HTML）
template: |
  <!DOCTYPE html>
  ...

# 生成提示词（LLM 参考此 Skill 时的额外指导）
prompt_hint: |
  参考此模板的结构和交互模式，但根据用户的具体需求调整：
  - 数学运算类型（加/减/乘/除）
  - 数值范围
  - 视觉主题
  - 难度递进方式

# 评估标准（evaluate_game 工具使用）
evaluation_criteria:
  - 数学题目难度是否匹配目标年龄段
  - 答对/答错是否有明确的视觉和文字反馈
  - 是否有计分系统且分数正确累计
  - 题目是否随机生成（非固定题库）
  - 游戏区域内所有元素是否在可见范围内
```

---

## 3. 验证体系：怎么知道做得好不好

### 3.1 Working Memory（工作记忆）—— "我现在看到什么"

类比 Agent Harness 的 WorkingMemoryCursors（accessLevel/draftVersion），Game Agent 的工作记忆追踪：

| Cursor | 含义 | 变化检测 |
|--------|------|---------|
| `game_version` | 当前游戏 HTML 的版本号 | 每次 generate/fix 后递增 |
| `eval_score` | 最近一次评估的总分 | 每次 evaluate 后更新 |
| `issue_count` | 未修复的问题数量 | evaluate 后更新，fix 后减少 |
| `iteration` | 当前迭代轮次 | 每轮 Loop 递增 |

**上下文注入**（类比 Agent Harness 的 ContextUpdateRenderer）：
```xml
<working_memory>
  <game_state>
    <version>3</version>
    <last_eval_score>72/100</last_eval_score>
    <open_issues>
      - 游戏区域底部元素被截断
      - 点击"开始"按钮无响应
    </open_issues>
    <iteration>2 of 5</iteration>
  </game_state>
</working_memory>
```

### 3.2 评估维度（evaluate_game 的评判框架）

评估分为 **5 个维度**，每个维度 20 分，满分 100：

| 维度 | 权重 | 检测方式 | 评估内容 |
|------|------|---------|---------|
| **可运行性** | 20 分 | Headless Browser 渲染 | 页面能正常加载，无 JS 报错，不白屏 |
| **视觉完整性** | 20 分 | 截图 + 视觉模型 | 元素不越界、布局合理、文字可读、颜色对比度充足 |
| **交互响应性** | 20 分 | Headless Browser 模拟点击 | 按钮可点击、有反馈、游戏状态能变化 |
| **教育匹配度** | 20 分 | 视觉模型 + 规则检查 | 内容是否匹配目标年龄和教育目标 |
| **游戏完整性** | 20 分 | 视觉模型 + 代码分析 | 有开始/结束、有计分、有胜负条件、难度合理 |

**评估流程**：
```
HTML 代码
  → Playwright 渲染（headless Chrome）
  → 截取初始画面截图
  → 模拟点击"开始"按钮
  → 截取游戏中画面截图
  → 收集 console.error 日志
  → 打包：[截图 × 2-3 张, console 日志, HTML 源码摘要]
  → 提交给多模态 LLM 评估
  → 返回：各维度评分 + 问题列表 + 改进建议
```

### 3.3 质量门禁（Quality Gate）

Agent Loop 的迭代终止条件：

| 条件 | 阈值 | 说明 |
|------|------|------|
| 评估总分达标 | ≥ 80/100 | 质量达标，可交付 |
| 最大迭代次数 | 5 轮 | 防止无限循环 |
| 关键问题清零 | 可运行性 ≥ 16/20 | 至少能跑起来 |
| 用户满意 | 用户确认 | 用户可随时说"可以了" |

**迭代策略**：
- 第 1 轮：生成 → 评估
- 第 2-4 轮：如果评分 < 80，根据问题列表修复 → 再评估
- 第 5 轮：如果仍未达标，返回当前最佳版本 + 问题说明
- 任何时候用户说"可以了"或"就这样"，立即停止

---

## 4. 完整 Agent Loop 流程图

```
用户："做一个给6岁孩子的加法游戏"
  │
  ▼
┌─────────────────────────────────────────┐
│ Agent Loop（最多 5 轮迭代）               │
│                                         │
│  Turn 1:                                │
│  ├── LLM Think：分析需求，查看可用 Skill  │
│  ├── tool_call: list_skills(type=math)  │
│  ├── tool_call: load_skill(math_adventure)│
│  ├── LLM Think：基于 Skill 定制设计方案    │
│  ├── tool_call: generate_game(方案)      │
│  ├── tool_call: evaluate_game(HTML)      │
│  └── 评估结果：85/100 ✅ 达标            │
│      → 返回游戏                          │
│                                         │
│  --- 或者如果评估不达标 ---               │
│                                         │
│  Turn 1: 评估结果：62/100 ❌             │
│  ├── 问题：按钮无响应、元素越界           │
│  │                                      │
│  Turn 2:                                │
│  ├── LLM Think：看到问题列表，制定修复方案 │
│  ├── tool_call: fix_game(HTML, 问题列表)  │
│  ├── tool_call: evaluate_game(修复后HTML) │
│  └── 评估结果：88/100 ✅ 达标            │
│      → 返回游戏                          │
└─────────────────────────────────────────┘
```

---

## 5. 与 Agent Harness 的对标关系

| Agent Harness 概念 | Game Agent 对应 | 说明 |
|-------------------|----------------|------|
| SEMANTIC_PROMPT | Game Agent 角色定义 + 领域知识 + 质量标准 | "我是游戏设计专家，我知道什么是好游戏" |
| Workflow（seedance2/kling/sora） | 生成策略（skill_based/creative_gen/iterative_refine） | "这次该用哪种方式做游戏" |
| WorkingMemoryCursors | game_version/eval_score/issue_count/iteration | "当前游戏状态如何" |
| 36 个 Tool | 5 个 Tool（list/load/generate/evaluate/fix） | "我能做什么操作" |
| ContextUpdateDetector | 评估分数变化检测 | "游戏质量变好了还是变差了" |
| compose_sys_prompt | semantic + 当前策略 + working memory 拼接 | 系统提示词构建 |
| maxIterations = 200 | maxIterations = 5（游戏生成不需要太多轮） | 迭代上限 |

---

## 6. 设计决策补充（2026-03-28）

### Playwright 交互深度
**决定**：点"开始"后尝试玩几步。
- 截图初始画面 → 模拟点击"开始" → 模拟玩 3-5 步操作（点击按钮/选项）→ 每步截图
- 收集每步的 console 日志和 DOM 变化
- 如果游戏有键盘操作，模拟方向键/空格等基本输入

### 评估方案调整：Game Runtime Probe + Playwright 行为验证 + LLM 代码 Review
**决定**：暂不使用视觉模型评估（没有合适的模型支持）。

**核心创新：Game Runtime Probe（游戏运行时探针）**

单靠 Playwright 只能判断"游戏活着还是死了"，无法判断"游戏好不好"。我们需要让游戏**自己报告运行状态**。

**方案**：在每个生成的 HTML 中注入一段标准化的监控代码（探针），游戏运行时自动采集结构化数据，Playwright 负责模拟操作并收割数据，最后把结构化报告交给 LLM 判断质量。

**探针注入代码（`game-probe.js`）**：
```javascript
window.__GAME_PROBE__ = {
  events: [],           // 所有用户交互事件
  stateChanges: [],     // 游戏状态变化（分数、关卡、生命值...）
  errors: [],           // 运行时错误
  elementPositions: [], // 关键元素位置快照（越界检测）
  collisions: [],       // 碰撞事件
  timing: {},           // 响应延迟数据
  domMutations: [],     // DOM 变化记录
};

// 1. 错误捕获
window.addEventListener('error', e => {
  window.__GAME_PROBE__.errors.push({
    msg: e.message, file: e.filename, line: e.lineno, ts: Date.now()
  });
});

// 2. 交互事件追踪：覆写 addEventListener，记录所有 click/keydown 事件

// 3. DOM 变化监听：MutationObserver 监控游戏区域 DOM 变化

// 4. 状态快照：每秒检测一次分数元素、游戏状态元素的文本变化

// 5. 越界检测：定时扫描所有可见元素的 getBoundingClientRect vs viewport
```

**评估流程（三步）**：
```
Step 1: Playwright 注入 + 模拟操作
  ├── 注入 game-probe.js 到 HTML
  ├── Headless Chrome 渲染页面
  ├── 模拟点击"开始"按钮
  ├── 模拟玩 3-5 步操作（点击选项/按钮/方向键）
  └── 每步之间等待 500ms，让游戏响应

Step 2: 收割 Probe 数据
  ├── 执行 page.evaluate(() => window.__GAME_PROBE__)
  └── 得到结构化的运行报告 JSON

Step 3: LLM 质量判断
  ├── 输入：Probe 运行报告 + HTML 源码摘要 + 原始用户需求
  └── 输出：各维度评分 + 问题列表 + 改进建议
```

**LLM 拿到的运行报告示例**：
```json
{
  "console_errors": [],
  "user_interactions": [
    {"type": "click", "target": "#start-btn", "ts": 100, "dom_changed": true},
    {"type": "click", "target": ".answer-3", "ts": 2500, "dom_changed": true,
     "score_before": 0, "score_after": 10}
  ],
  "out_of_bounds_elements": [],
  "state_transitions": ["idle → playing → answered_correct → next_question"],
  "dom_mutations_count": 12,
  "final_state": {"score": 10, "question_count": 2, "errors": 0},
  "response_latency_avg_ms": 45
}
```

**为什么这比截图更好**：
- 截图是"让 LLM 猜游戏怎么样"——主观、不可靠
- Probe 报告是"让游戏自己告诉你它怎么样"——客观、结构化、可编程
- LLM 的角色从"看图说话"变成"分析报告做判断"——这是 LLM 擅长的

**评估维度（基于 Probe 数据）**：

| 维度 | 权重 | 数据来源 | 判断依据 |
|------|------|---------|---------|
| **可运行性** | 20 分 | Probe.errors + 页面加载状态 | 无 JS 错误、页面正常渲染 |
| **布局正确性** | 20 分 | Probe.elementPositions | 无越界元素、关键元素可见 |
| **交互响应性** | 20 分 | Probe.events + Probe.stateChanges | 点击后 DOM 有变化、状态有推进 |
| **教育匹配度** | 20 分 | LLM Review（代码 + Probe 报告 + 需求） | 内容匹配目标年龄和教育目标 |
| **游戏完整性** | 20 分 | Probe.stateChanges + 代码分析 | 有开始/结束状态、有计分、有完成条件 |

### 用户截图反馈
**决定**：不支持。交互模式保持纯文本对话。

### fix_game 策略
**决定**：增量优先，全量兜底。
- **第 1-3 次修复**：增量修补——把问题列表和原 HTML 交给 LLM，要求只修改有问题的部分
- **第 4 次修复（如果仍不达标）**：全量重写——把累积的问题和原始需求交给 LLM，要求从零重写
- **理由**：增量修补 token 消耗低、改动可控；但如果多次修补仍有问题，说明底子有问题，不如重写

---

## 8. Agent Harness 轻量化改造（任务 260521-agent-harness）

> 改造时间：2026-05-27 / 三个 Step 全部完成 / 47 用例覆盖。
> 完整契约见 `docs/task/260521-agent-harness/`。

把 AgentLoop 从「prompt + tools + WorkingMemory 一锅炖」拆成清晰分层：

```
┌─ WorkingMemory  ─── 事实状态（gameVersion / evalScore / openIssues / lastEvaluationObservation / runTrace / controlSignals）
├─ ContextRenderer ── 把状态渲染为 <working_memory> XML 片段，注入 system prompt
├─ EvaluationObservation ── 把 ProbeReport 高信号字段结构化（scoresByDimension / probeSummary / classified issues）
└─ ControlSignals + RunTrace ─ 控制信号（scoreImproved/sameIssuesRepeated/criticalIssueExists/evaluationDegraded/shouldFullRewrite）+ 每轮 trace
```

### 8.1 各组件位置

| 组件 | 类 | 职责 |
|------|---|---|
| 事实存储 | `agent/loop/WorkingMemory` | 字段 + getter/setter，**不**拼 prompt |
| 上下文渲染 | `agent/loop/ContextRenderer` | `render(WorkingMemory)` → XML 片段；`memory == null` 不抛 NPE |
| 评估观察 | `agent/evaluation/EvaluationObservation` | `fromProbeReport(report)` / `degraded(score, reason, issues)` 工厂；含 `degraded` 标志 |
| 问题分类 | `agent/evaluation/ObservationIssue` | category（runnability/layout/interactivity/completeness/education/evaluation/general）+ severity（critical/major/minor）|
| 运行轨迹 | `agent/loop/RunTrace + TraceEntry` | 每轮记 iteration/score 变化/issue 快照/responseLength；`recent(n)` 取最近 N 条 |
| 控制信号 | `agent/loop/ControlSignals` | `compute(memory, trace)` 静态工厂；`shouldFullRewrite = fixCount>=3 && !scoreImproved && sameIssuesRepeated` |

### 8.2 主循环数据流

```
AgentLoop.run()
  → tryPreloadSkill              (Skill 关键词预加载)
  → for i in 0..MAX_ITERATIONS:
      memory.setIteration(i+1)
      scoreBefore = memory.getEvalScore()
      buildSystemPrompt(memory)   → contextRenderer.render(memory)
      callLlmWithRetry(...)        (Spring AI Function Calling 自动多轮)
      ┃   GameEvaluationTool      → EvaluationObservation.fromProbeReport / degraded
      ┃                            → memory.setLastEvaluationObservation(obs)
      recordTraceAndSignals(memory, i+1, scoreBefore, response)
        → trace.append(TraceEntry)
        → ControlSignals.compute(memory, trace) → memory.setControlSignals(...)
      if score == 0 || score >= QUALITY_GATE_SCORE: return success
```

### 8.3 ContextRenderer 输出结构

```xml
<working_memory>
  <game_state>
    <version>...</version>
    <last_eval_score>...</last_eval_score>
    <open_issues>...</open_issues>
    <iteration>... of 5</iteration>
    <fix_count>...</fix_count>
    <game_html><![CDATA[...]]></game_html>          (短 HTML)
    或
    <html_summary>...</html_summary>                (>8000 字符走摘要)
    <html_length>...</html_length>
    <suggested_skill>...</suggested_skill>          (可选)

    <!-- Step 2 新增，仅 lastEvaluationObservation != null 时输出 -->
    <evaluation_observation>
      <degraded>true</degraded>                     (可选)
      <total_score>.../100</total_score>
      <scores>
        <runnability>.../20</runnability>
        ...
      </scores>
      <probe_summary>
        <page_loaded>...</page_loaded>
        <js_errors>...</js_errors>
        <events>...</events>
        <dom_mutations>...</dom_mutations>
        <out_of_bounds>...</out_of_bounds>
        <final_score>...</final_score>              (可选)
      </probe_summary>
      <classified_issues>
        <issue category="..." severity="...">...</issue>
      </classified_issues>
    </evaluation_observation>

    <!-- Step 3 新增，仅 hasAnyTrueSignal / trace 非空时输出 -->
    <control_signals>
      <score_improved>true</score_improved>          (按需)
      <same_issues_repeated>true</same_issues_repeated>
      <critical_issue_exists>true</critical_issue_exists>
      <evaluation_degraded>true</evaluation_degraded>
      <should_full_rewrite>true</should_full_rewrite>
    </control_signals>
    <run_trace_summary>
      <round iteration="1" version="1">score 0→62 (+62)</round>
      <round iteration="2" version="2">score 62→78 (+16)</round>
      ...                                            (最近 3 条)
    </run_trace_summary>
  </game_state>
</working_memory>
```

**字节级相等基线**：当 `lastEvaluationObservation == null && controlSignals 全 false && runTrace 空` 时，`ContextRenderer.render(memory) === memory.toContextXml()` 与 Step 1 之前的输出完全一致，由 `ContextRendererTest` 用例 #8 字节级断言保护。

### 8.4 冻结的边界

- `AgentLoop.run(String userInput, String modelKey)` 签名 / `AgentLoopResult.success/failure` 语义
- `MAX_ITERATIONS = 5` / `QUALITY_GATE_SCORE = 80` / 异常路径"有 HTML 则返回当前版本"
- `GameEvaluator.evaluate(String)` 签名 / 五维评分公式 / Playwright 交互步骤
- `AgentPrompts.SYSTEM_PROMPT` / 任何 `@Tool` 方法签名
- 任何 SKILL.md 内容
- 前端 / API / pom.xml / schema.sql

### 8.5 测试

47 个 JUnit 用例覆盖（全部位于 `agent/loop/` 与 `agent/evaluation/`）：
- `ContextRendererTest`（13 用例）：含字节级相等保护、null 安全、HTML 摘要分支
- `EvaluationObservationTest`（9 用例）+ `ObservationIssueTest`（7 用例）：probe 映射、null 安全、降级标记
- `ControlSignalsTest`（12 用例）+ `RunTraceTest`（6 用例）：信号计算、最近 N 条裁剪

### 8.6 后续依赖

任务 `260524-skill-distillation-evidence` 的证据层将消费 `EvaluationObservation` + `RunTrace` 作为持久化字段来源（运行时数据流不需要再发明，只需把它们写进数据库）。harness 是该任务的上游。

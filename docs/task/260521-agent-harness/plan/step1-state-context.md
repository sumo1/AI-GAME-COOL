# Step 1：状态与上下文拆分

## 背景

当前 `WorkingMemory` 同时负责保存运行状态、提取 HTML 摘要、渲染 XML prompt 片段。这个结构能跑，但职责混在一起：状态对象变成 prompt builder，后续加观察结果、控制信号、trace 时会迅速变成垃圾桶。

本步骤只做第一刀：把“事实存储”和“上下文渲染”拆开，同时保持现有输出兼容。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/WorkingMemory.java`
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/AgentLoop.java`
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/ContextRenderer.java`（新建）
  - `game-agent-backend/src/test/java/com/sumo/agent/agent/loop/ContextRendererTest.java`（新建）

- **不可改文件**：
  - `AgentPrompts.java`
  - `GameEvaluationTool.java`
  - `GameEvaluator.java`
  - `SkillLoader.java`
  - 所有前端文件

- **不可新增的抽象**：
  - 不新增 SubAgent / Planner / Executor 层
  - 不新增数据库表
  - 不新增第三方依赖
  - 不引入 YAML/XML tool 协议

### 产出清单

1. 新增 `ContextRenderer`：
   - `public String render(WorkingMemory memory)`
   - 初始输出必须与现有 `WorkingMemory.toContextXml()` 语义一致
   - HTML 摘要逻辑可以先迁移过去，不改变摘要内容

2. `WorkingMemory` 调整职责：
   - 保留字段和 getter/setter，保持兼容
   - `toContextXml()` 保留，但内部委托 `ContextRenderer` 或标记为兼容入口
   - 不在本步骤新增大量 harness 字段

3. `AgentLoop.buildSystemPrompt()` 改为使用 `ContextRenderer.render(memory)`：
   - 通过 Spring 注入或私有 final 实例均可，保持项目现有风格
   - 不改变 `AgentPrompts.SYSTEM_PROMPT`

4. 单元测试：
   - 验证空 `WorkingMemory` 渲染包含 `<working_memory>`
   - 验证有 `openIssues` 时输出包含问题列表
   - 验证短 HTML 仍进入 `<game_html><![CDATA[`
   - 验证长 HTML 进入 `<html_summary>` 且不输出完整 HTML

### 约束（已冻结的边界）

- `WorkingMemory.toContextXml()` 不能删除，避免破坏现有调用方
- XML 标签名默认保持不变：`working_memory / game_state / version / last_eval_score / open_issues / iteration / fix_count`
- 不改变 `HTML_SUMMARY_THRESHOLD = 8000`
- 不改变 `AgentLoop.run()` 的循环结构
- 不改变质量门禁和最大迭代次数

### 复用的现有模式

- 测试位置参考 `game-agent-backend/src/test/java/com/sumo/agent/agent/AgentLoopIntegrationTest.java`
- 代码风格参考 `WorkingMemory` 当前 getter/setter 的简单 POJO 风格
- Spring 注入风格保持当前项目已有写法，不强行迁移构造器注入

### 依赖的前置子任务

无。

## 【验收契约（Evaluator 输入）】

### 代码结构验证

- [ ] `ContextRenderer.java` 存在，且提供 `render(WorkingMemory memory)` 方法
- [ ] `AgentLoop.buildSystemPrompt()` 不再直接调用 `memory.toContextXml()`
- [ ] `WorkingMemory.toContextXml()` 仍存在
- [ ] `AgentPrompts.SYSTEM_PROMPT` 未被修改
- [ ] `AgentLoop.MAX_ITERATIONS` 和 `QUALITY_GATE_SCORE` 未被修改
- [ ] 未新增第三方依赖

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `mvn -pl game-agent-backend test -Dtest=ContextRendererTest` | exit 0，测试全部通过 |
| `mvn -pl game-agent-backend -am compile` | exit 0，无编译错误 |

### 兼容性验收

- [ ] 构造一个含短 HTML 的 `WorkingMemory`，`ContextRenderer.render()` 输出仍含完整 HTML
- [ ] 构造一个长 HTML，输出含摘要和 `html_length`，不含完整 HTML 主体
- [ ] `WorkingMemory.toContextXml()` 与 `ContextRenderer.render(memory)` 对同一状态输出一致或语义等价

### 负面用例

- [ ] `render(null)` 不抛 NPE，应返回空 working memory 或明确失败语义
- [ ] `gameHtml = null` 时不输出 `<game_html>`
- [ ] `openIssues` 为空时不输出空 `<open_issues>` 块


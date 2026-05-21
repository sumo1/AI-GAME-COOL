# Step 4：文档与验收基线

## 背景

Harness 改造会改变 AgentLoop 的架构语义。代码如果改了但文档不跟，下一次 task-designer 会从过时世界模型出发，继续踩坑。本步骤负责把代码行为、架构文档和验证方式同步。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `game-agent-backend/CLAUDE.md`
  - `docs/upgrade/DESIGN.md`
  - `docs/upgrade/PROGRESS.md`
  - `docs/knowledge/principles/agent-system-philosophy.md`（仅当确有跨任务原则需要上浮）
  - `docs/task/260521-agent-harness/memory/*.md`
  - `docs/task/260521-agent-harness/task-code-reviewer/code-review.md`（新建，可选）

- **不可改文件**：
  - Java 代码（本步骤只做文档和验证基线）
  - 前端代码

### 产出清单

1. 更新项目架构说明：
   - 描述 `AgentRunState / WorkingMemory`
   - 描述 `ContextRenderer`
   - 描述 `EvaluationObservation`
   - 描述 `ControlSignals / RunTrace`

2. 写任务专项 code-review 规则：
   - 禁止把 prompt 文本当事实源
   - 禁止照搬通用平台 harness
   - 禁止破坏现有 API 和 tool 签名
   - 审查上下文是否过度膨胀

3. 写验证说明：
   - 单元测试命令
   - 编译命令
   - 手工冒烟请求
   - 需要观察的日志和返回字段

4. 写 memory 决策：
   - 为什么不迁移 `yuntoo-smartcode` 的 YAML/XML 协议
   - 为什么 `AI-GAME` 选择领域型 harness

### 约束（已冻结的边界）

- 文档必须描述当前真实代码，不写“未来会支持”冒充事实
- 如果代码未实现某项，只能写为“后续任务”，不能写成已完成
- `docs/knowledge` 只上浮跨任务原则，不记录任务流水

### 依赖的前置子任务

依赖 Step 1-3 完成。

## 【验收契约（Evaluator 输入）】

### 文档结构验证

- [ ] `game-agent-backend/CLAUDE.md` 包含 harness 状态/上下文/观察/控制的当前说明
- [ ] `docs/upgrade/DESIGN.md` 或 `docs/upgrade/PROGRESS.md` 反映本次改造
- [ ] `docs/task/260521-agent-harness/memory/` 至少包含一条决策记录
- [ ] 如新增 code-review 规则，路径为 `docs/task/260521-agent-harness/task-code-reviewer/code-review.md`

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `mvn -pl game-agent-backend -am compile` | exit 0 |
| `mvn -pl game-agent-backend test -Dtest=ContextRendererTest` | exit 0 |
| `mvn -pl game-agent-backend test -Dtest=ControlSignalsTest` | 如存在，exit 0 |

### 手工冒烟验收

1. 启动后端。
2. 调用 `/api/game/v2/generate`，输入一个简单游戏需求。
3. 确认返回仍包含可运行 HTML。
4. 后端日志能看到迭代、评估、trace/control signal 相关摘要。
5. 若评分低于 80，下一轮 prompt 能看到结构化评估观察，而不是只有一段松散 markdown。

### doc-refresher 验收

- [ ] 路径引用均存在
- [ ] 文档中的类名和方法名与代码一致
- [ ] 文档中未声明未实现功能为“已完成”
- [ ] 不存在与 `AgentLoop` 当前行为相反的描述


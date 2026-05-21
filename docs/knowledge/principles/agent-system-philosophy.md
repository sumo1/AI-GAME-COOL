# Agent 系统设计哲学：双 SSOT + 三时间方向

> 这是 AI-GAME `agents/` 体系的世界观底座。任何对 agent 流程、文档结构、知识沉淀机制的修改，都应当先回到这里对齐。

---

## 一、双 SSOT：代码与文档同等地位

代码和文档**都是一等公民**。

| SSOT | 表达什么 | 不可被另一个替代 |
|------|---------|-----------------|
| **代码** | 行为真相（系统实际怎么跑） | 但表达不了"为什么不这么做"、"哪条路被否决"、"业务约束在哪" |
| **文档** | 业务知识真相（意图、边界、否决路径） | 业务意图常常活在负空间里——代码只能表达正空间 |

两者必须**始终一致**，否则任何一个都失去 SSOT 资格。这就是为什么文档不能放在 Confluence / Notion / 私人笔记——必须**同 repo、同 commit、同 review**。

### 在 AI-GAME 上的具体体现

- 工程规范 → `docs/engineering/conventions.md`（与代码一起 commit）
- 审查标准 → `docs/review/code-check.md`
- 跨任务知识 → `docs/knowledge/`
- 任务专项决策 → `docs/task/{id}/`
- Agent 流程 → `agents/`（流程 SSOT）+ `.claude/agents/`（薄引用）

代码 review 必须同时 review 这些文档；文档过时等价于代码 bug。

---

## 二、三时间方向：知识闭环

整套 agents 体系在时间轴上服务三个方向，缺一即漏：

```
   过去                 现在                    未来
─────────────────────────────────────────────────────
  历史沉淀  ───→   当前任务的拆解 + 执行  ───→  沉淀回去

knowledge/        task-designer                dreamer
engineering/      coder + evaluator             ↓
   ↑              code-reviewer            knowledge/
   │              doc-refresher  ←─保鲜    engineering/
   │                  ↓                        ↑
   └────────  下一次任务从新鲜起点出发  ────────┘
```

### 过去 → 现在：让历史可用

- **task-designer** 强制读取 `docs/engineering/conventions.md`、`docs/knowledge/`、最近任务
- 没有这一步，每个任务都从零开始踩坑

### 现在：执行不偏离

- **task-designer** 输出双契约（实现契约 + 验收契约）
- **coder** 严格按契约施工
- **evaluator** 复跑命令验收，不读 coder 报告判通过
- **code-reviewer** 独立视角审查，工程规范 + 任务专项

### 现在 → 未来：沉淀不流失

- **doc-refresher**：当下的 SSOT 保鲜哨兵——代码变了把"文档未跟"标出来，让主会话裁决修复。**没有它，文档腐坏 → 下次任务从腐烂的起点出发**。
- **dreamer**：业务知识的酿造师——把 task memory 蒸馏成任务级 SUMMARY，把跨任务普适原则上浮到 knowledge。**没有它，业务理解不会回到知识库 → 下次任务还要从头踩坑**。

两者一起，业务知识才能**随项目持续累积**，而不是每次任务交付完就流失。

---

## 三、推论与硬规则

### 推论 1：能写文档就不要写额外代码

- 能用 SKILL.md 解决就不写 Java（[[skill-vs-java]]）
- 能用 conventions.md 表达的规则就不要硬编码到框架检查里

### 推论 2：edge of "边界" vs "目的"

每个 agent 文档的"禁止事项"是**边界**（防止越权污染），不是**目的**（解决什么问题）。讨论 agent 时不能把边界当目的——否则会得出"doc-refresher 是事实核查员"这类**描述对了但抓错重点**的结论。

正确的目的视角：

| Agent | 目的（为什么存在） | 边界（不许做什么） |
|-------|-------------------|-------------------|
| task-designer | 让需求可执行、让下游可并行 | 不写实现代码 |
| coder | 按契约精准施工 | 不越界、不重构 |
| evaluator | 独立复现验收 | 不改代码、不补契约 |
| code-reviewer | 提交前最后关卡 | 不动方向盘 |
| doc-refresher | **保鲜业务知识 SSOT** | 只诊断不治疗 |
| dreamer | **让知识能持续演进** | 只整理不发明 |
| ci-pre-checker | 提交流水线守门 | 不绕过红线 |

### 推论 3：契约不可执行就重写

task-designer 的双契约必须是**机器可比对 / 命令可复跑**。"确保代码正确"、"符合最佳实践"这类不可执行条目要打回重写——否则 evaluator 没法独立验收，code-reviewer 没法独立审查。

### 推论 4：反"修复漂移"

`evaluator` 打回 → `coder` **只修失败项**；连续 3 轮未过升级"主会话裁决"。承认 LLM 容易陷入"越修越乱"，从结构上掐断。

---

## 四、来源

- 原始讨论：用户在阅读 `medeo-market/agents/` 后纠正 doc-refresher 的角色描述（May 2026）
- 用户原话："代码和文档都是一等公民，文档代表了业务知识，需要保证新鲜的业务知识，是在代码仓库中进行共同管理和维护的"
- 上一段记忆：`/Users/sumo/.claude/projects/-Users-sumo-workplace-ai-AI-GAME/memory/agent-system-design-philosophy.md`
- 参考实现：`one2x/medeo-market/agents/`（七个 agent 的设计原型）

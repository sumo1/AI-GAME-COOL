# Step 2：证据字段契约清点

## 背景

harness 改造（`260521-agent-harness`）已经把 `EvaluationObservation` / `RunTrace` / `ControlSignals` 落到运行时内存。本步骤**不写代码**，只输出"证据字段契约"——决定哪些事实必须落库、哪些只在内存留即可。这是 Step 3 持久化设计的输入。

写完后冻结，Step 3-6 严格按此契约执行。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `docs/task/260524-skill-distillation-evidence/plan/step2-evidence-fields.md`（即本文件，写产出）
  - `docs/task/260524-skill-distillation-evidence/memory/2026-05-XX-evidence-contract.md`（新建，写决策）

- **不可改文件**：
  - 任何 Java 代码（本步骤只产文档）
  - schema.sql

### 产出清单

在本文件末尾追加 `## 字段契约` 一节，按下面四张表填写。

#### 1. 必落库字段（写进新表 / 扩展旧表）

| 字段 | 来源 | 类型 | 用途 | 落库表（Step 3 决定）|
|---|---|---|---|---|
| `skill_name` | `ToolContext.activeSkill.name` 或 `WorkingMemory.preloadedSkill` | TEXT | 蒸馏样本归属哪个 Skill | `game_run_evaluations` |
| `model_key` | `AgentLoop.run` 入参 | TEXT | 区分模型差异 | 同上 |
| `success` | `AgentLoopResult.success` | INTEGER 0/1 | 失败样本筛选 | 同上 |
| `error_type` | `ErrorClassifier` 分类，失败时填 | TEXT | 失败模式聚类 | 同上 |
| `total_score` | `EvaluationObservation.totalScore` | INTEGER | 候选筛选 | 同上 |
| `degraded` | `EvaluationObservation.degraded` | INTEGER 0/1 | 排除降级样本 | 同上 |
| `degraded_reason` | `EvaluationObservation.degradedReason` | TEXT | 降级类型聚合 | 同上 |
| `iteration_count` | `RunTrace.entries.size()` | INTEGER | 收敛速度统计 | 同上 |
| `final_iteration_summary` | `RunTrace.last().summary` | TEXT | 一句话复盘 | 同上 |
| `scores_json` | `EvaluationObservation.scoresByDimension` | TEXT (JSON) | 五维下钻分析 | 同上 |
| `probe_summary_json` | `EvaluationObservation.probeSummary` | TEXT (JSON) | probe 统计指标 | 同上 |
| `classified_issues_json` | `EvaluationObservation.issues` 序列化 | TEXT (JSON) | 按 category/severity 聚合 | 同上 |
| `iter_traces_json` | `RunTrace.entries` 序列化（不含 issuesSnapshot 大字段，存条目摘要）| TEXT (JSON) | 复盘每轮变化 | 同上 |
| `candidate_status` | 默认 'raw'，由 Step 5/6 工作流流转到 'candidate'/'accepted'/'rejected' | TEXT | 蒸馏候选生命周期 | `skill_distillation_candidates`（Step 3 设计）|

#### 2. 不落库（仅内存）

| 字段 | 原因 |
|---|---|
| `WorkingMemory.gameHtml`（中间版本）| HTML 大字段；最终 HTML 已在 `game_runs` 持久化；中间版本默认丢弃 |
| `RunTrace.entries[i].issuesSnapshot` 完整列表 | 字段大、`classified_issues_json` 已含分类摘要 |
| `ControlSignals` 实时值 | 派生量，可从 `iter_traces_json` 重算 |
| `WorkingMemory.skillIndex` | 启动时确定，不需要每次落库 |
| `lastEvaluationObservation` 中的原始字段 | 已序列化到上面四个 _json 字段 |

#### 3. 失败样本

| 字段 | 处理 |
|---|---|
| `AgentLoopResult.failure(error, iterations)` | 仍写一条 `game_run_evaluations`，`success=0` + `error_type` 分类 + `total_score=0` |
| `gameHtml=null` | 不写 `game_runs`（兼容现有），但仍写 evaluation 证据 |

#### 4. 候选生命周期状态机

```
raw → candidate → accepted | rejected
 │       │           │            │
 │       │           ▼            ▼
 │       │       SKILL.md      丢弃
 │       │       人工合并
 │       │
 │       └─ 由 Step 5 查询 + 人工标记触发
 └─ 默认值，运行时写入
```

字段位置：`skill_distillation_candidates.status`，由 Step 3 表设计具体定义。

### 约束（已冻结的边界）

- 不修改 `sessions` / `messages` / `game_runs` 三表的字段语义（Step 3 只能新增表，不动旧表）
- 不引入向量检索 / few-shot 召回（本任务范围外）
- 不规定具体 SQL DDL（Step 3 决定）
- 不规定 Repository / Service 类名（Step 3-4 决定）

### 复用的现有模式

- 字段命名沿用 `260521-game-storage-db` 风格（snake_case TEXT/INTEGER）
- JSON 大字段用 TEXT 列（SQLite 不支持原生 JSONB）
- 时间戳用 INTEGER 毫秒 epoch

### 依赖的前置子任务

- `260521-game-storage-db` 三表已存在
- `260521-agent-harness` Step 2-3 提供 `EvaluationObservation` / `RunTrace`

## 【验收契约（Evaluator 输入）】

### 文档结构验证

- [ ] 本文件末尾有 `## 字段契约` 节，含 4 张表
- [ ] 每个字段标明来源（具体类名 / 字段名）+ 类型 + 用途
- [ ] 候选生命周期 4 状态明确：raw / candidate / accepted / rejected
- [ ] 与 harness 产物（EvaluationObservation / RunTrace）字段名一一对应
- [ ] memory 新增一条决策（解释为什么用 JSON 列而非展开成 N 列）

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| 无（本步骤纯文档） | N/A |

### 数据/字段验收

- [ ] 表 1 中每个字段都能在 `WorkingMemory` / `EvaluationObservation` / `RunTrace` 中找到来源
- [ ] 表 2 中"不落库"项有明确理由（大字段 / 派生量 / 启动时确定）
- [ ] 表 3 失败样本处理与现有 `SessionService.recordRun` 容错路径不矛盾
- [ ] 表 4 状态机与 `docs/knowledge/principles/skill-evolution-sop.md` 现有 SOP 兼容

### 负面用例

- [ ] 字段契约不允许包含「未来可能加上」的占位项；所有字段必须能立刻从代码取到
- [ ] 不允许重复 `game_runs` 已有字段（如 `total_score` 与 `game_runs.eval_score` 必须明确分工）

## 字段契约

> 待 coder 在 Step 2 施工时填入；上方表格是模板，coder 应基于 harness 实际代码核对每行字段，并把"分工与去重"在此节落定。

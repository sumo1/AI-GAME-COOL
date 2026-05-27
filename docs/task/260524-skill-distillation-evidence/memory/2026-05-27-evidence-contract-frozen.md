# 字段契约冻结：为什么用 JSON 列而不是展开 N 列

> 日期：2026-05-27
> 关联：plan/step2-evidence-fields.md（§字段契约）

## 决策

`game_run_evaluations` 表用 4 个 TEXT JSON 列（`scores_json` / `probe_summary_json` / `classified_issues_json` / `iter_traces_json`）保存结构化数据，**不**展开成 N 列。

## 备选方案对比

### 方案 A（已选）：JSON 列

```sql
scores_json              TEXT,    -- {"runnability":15,"layout":18,...}
probe_summary_json       TEXT,    -- {pageLoaded,jsErrorCount,...}
classified_issues_json   TEXT,    -- [{category,severity,message},...]
iter_traces_json         TEXT     -- [{iteration,scoreBefore,...},...]
```

### 方案 B：完全展开

```sql
score_runnability        INTEGER,
score_layout             INTEGER,
score_interactivity      INTEGER,
score_completeness       INTEGER,
score_education          INTEGER,
probe_page_loaded        INTEGER,
probe_js_error_count     INTEGER,
... (再 N 列)
-- issues 必须用单独的关联表 game_run_evaluation_issues
-- iter_traces 必须用单独的关联表 game_run_iterations
```

## 选 A 的理由

1. **演进成本**：`EvaluationObservation.ProbeSummary` 字段集合还在演化（任务 260522-evaluator-oracle-shared-core 抽公共底层后可能加更多 probe 字段）。JSON 列加字段不需要 ALTER TABLE。
2. **写入路径简单**：一次 insert 写一行；展开方案要写 1 + N 行 + 多次 FK 校验。
3. **查询场景轻量**：本任务 Step 5 的查询都是「按 skill_name + total_score 范围」筛——这些已经是顶层列、有索引。JSON 列只在详情接口反序列化展开给前端，不参与 WHERE。
4. **数据规模小**：单人项目 SQLite，game_runs 至今几十条；evaluations 表预期同量级。JSON 字符串平均 < 2KB，不会撑爆 SQLite 行大小限制（MB 级）。
5. **查询模糊匹配可用**：`classified_issues_json LIKE '%"category":"runnability"%'` 在小表上 < 10ms。如果将来真的成为瓶颈，再切关联表也是局部重构。

## 红线

A 方案的代价：

- 不能用 SQL 直接 GROUP BY 某个 JSON 内字段（如想统计「过去 7 天哪个维度低分最多」要先反序列化）
- 类型校验靠应用层（不是数据库 schema）

可接受的代价是因为：

- 本任务**不**做 dashboard / 大数据分析
- 本任务**不**让多个写入方共享 schema（只有 SessionService.recordEvidence 一个写入点）
- 类型校验由 `EvidenceMapper` 静态方法和 `EvaluationObservation` 的 Java 字段类型双重保证

## 触发切换关联表的条件

如果出现以下场景，再切方案 B：

- 出现按 issue 维度做 SQL 聚合的强需求（GROUP BY category）
- evidence 表行数 > 10 万
- 多个写入方需要共享 schema

否则**永远用 JSON 列**。

## 副决策

- 时间戳一律 INTEGER 毫秒 epoch（沿用 260521-game-storage-db）
- boolean 一律 INTEGER 0/1
- success / degraded 都写默认值 0，避免运行时空指针
- candidate 状态字符串用全小写 `raw / candidate / accepted / rejected`，不引入枚举类（避免重命名时迁移成本）

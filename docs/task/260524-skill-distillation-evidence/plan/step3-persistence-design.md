# Step 3：持久化设计

## 背景

Step 2 字段契约确定了哪些事实必须落库。本步骤把契约落到 schema：决定**新增表**而非扩展 `game_runs`，避免污染现有回放语义；明确 Repository 接口；保证迁移在 `schema.sql` 幂等运行。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `game-agent-backend/src/main/resources/schema.sql`（新增表 DDL，**不改**现有三表）
  - `game-agent-backend/src/main/java/com/sumo/agent/infra/db/GameRunEvaluationEntity.java`（新建）
  - `game-agent-backend/src/main/java/com/sumo/agent/infra/db/GameRunEvaluationRepository.java`（新建）
  - `game-agent-backend/src/main/java/com/sumo/agent/infra/db/SkillDistillationCandidateEntity.java`（新建）
  - `game-agent-backend/src/main/java/com/sumo/agent/infra/db/SkillDistillationCandidateRepository.java`（新建）
  - `game-agent-backend/src/test/java/com/sumo/agent/infra/db/GameRunEvaluationRepositoryTest.java`（新建）
  - `game-agent-backend/src/test/java/com/sumo/agent/infra/db/SkillDistillationCandidateRepositoryTest.java`（新建）

- **不可改文件**：
  - `sessions / messages / game_runs` 表已有 DDL（不动语义）
  - `agent/loop/*` / `agent/evaluation/*`（Step 4 才接入）
  - `api/*` / 前端

- **不可新增的抽象**：
  - 不引入 JPA / MyBatis / Liquibase（沿用 `260521-game-storage-db` 的 Spring JDBC 风格）
  - 不引入 ORM 关系映射（用手写 SQL + RowMapper）

### 产出清单

#### 1. `schema.sql` 末尾追加两表（保持幂等）

```sql
-- 评估证据：一次 AgentLoop 运行的结构化复盘（无论 success/failure 都写）
CREATE TABLE IF NOT EXISTS game_run_evaluations (
    id                       TEXT PRIMARY KEY,
    session_id               TEXT NOT NULL,
    game_run_id              TEXT,                  -- 成功时关联 game_runs.id；失败为 NULL
    skill_name               TEXT,                  -- 来自 ToolContext.activeSkill 或 preloadedSkill
    model_key                TEXT,
    success                  INTEGER NOT NULL DEFAULT 0,    -- 0/1
    error_type               TEXT,                  -- ErrorClassifier 分类
    total_score              INTEGER NOT NULL DEFAULT 0,
    degraded                 INTEGER NOT NULL DEFAULT 0,    -- 0/1
    degraded_reason          TEXT,
    iteration_count          INTEGER NOT NULL DEFAULT 0,
    final_iteration_summary  TEXT,
    scores_json              TEXT,                  -- {"runnability":..,"layout":..,...}
    probe_summary_json       TEXT,                  -- ProbeSummary 序列化
    classified_issues_json   TEXT,                  -- [{category,severity,message}, ...]
    iter_traces_json         TEXT,                  -- [{iteration,scoreBefore,scoreAfter,summary}, ...]
    created_at               INTEGER NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_run_eval_session ON game_run_evaluations(session_id);
CREATE INDEX IF NOT EXISTS idx_run_eval_skill_score ON game_run_evaluations(skill_name, total_score);
CREATE INDEX IF NOT EXISTS idx_run_eval_success ON game_run_evaluations(success, created_at DESC);

-- 蒸馏候选：从 evaluation 中筛出的待人工审核样本，含状态机
CREATE TABLE IF NOT EXISTS skill_distillation_candidates (
    id                       TEXT PRIMARY KEY,
    evaluation_id            TEXT NOT NULL,
    skill_name               TEXT NOT NULL,
    status                   TEXT NOT NULL DEFAULT 'raw',   -- raw | candidate | accepted | rejected
    note                     TEXT,                  -- 人工审核备注
    created_at               INTEGER NOT NULL,
    updated_at               INTEGER NOT NULL,
    FOREIGN KEY (evaluation_id) REFERENCES game_run_evaluations(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_candidates_skill_status ON skill_distillation_candidates(skill_name, status);
CREATE INDEX IF NOT EXISTS idx_candidates_status_updated ON skill_distillation_candidates(status, updated_at DESC);
```

#### 2. `GameRunEvaluationEntity`

`@Data` POJO；字段一一对应表列；`createdAt` 用 `Instant`。

#### 3. `GameRunEvaluationRepository`

构造器注入 `JdbcTemplate`，写方法 `synchronized`，SQL 字符串常量化。必须方法：

- `String insert(GameRunEvaluationEntity entity)` — id 空则填 UUID，createdAt 空则填 now，回填到 entity
- `Optional<GameRunEvaluationEntity> findById(String id)`
- `List<GameRunEvaluationEntity> listBySession(String sessionId)`
- `List<GameRunEvaluationEntity> listBySkill(String skillName, int limit)`
- `List<GameRunEvaluationEntity> listFailures(int limit)` — `success = 0 ORDER BY created_at DESC`

**列表查询不读 *_json 大字段**（沿用 `GameRunRepository.LIST_COLUMNS` 的字段分离原则）；详情用 `findById`。

#### 4. `SkillDistillationCandidateEntity`

`@Data` POJO；字段一一对应。

#### 5. `SkillDistillationCandidateRepository`

- `String insert(SkillDistillationCandidateEntity entity)` — 新建时 status 默认 'raw'
- `Optional<SkillDistillationCandidateEntity> findById(String id)`
- `List<...> listBySkill(String skillName, String status, int limit)` — status 为 null 时不过滤
- `int updateStatus(String id, String status, String note)` — 状态机推进
- `int deleteById(String id)`

#### 6. Repository smoke 测试

每个 Repository 一个 smoke 测试类，沿用 `RepositorySmokeTest` 风格：真启 Spring Boot test context + 真 SQLite（@SpringBootTest），不 mock。每个测试覆盖 insert / find / list / 更新 / 级联删除（gameRun 删 → eval 级联删）。

### 约束（已冻结的边界）

- `sessions / messages / game_runs` 三表的列、索引、FK 全部不动
- `schema.sql` 必须幂等（`CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS`）
- HikariCP 配置不动（仍 `maximum-pool-size: 1`）
- 不引入 JSONB / 不写 SQL 函数 / 不引入触发器
- 写方法 `synchronized` 是底线（沿用 `GameRunRepository`）

### 复用的现有模式

- `GameRunRepository` 完整 / 列表 / 详情字段分离
- `RepositorySmokeTest` 真 Spring + 真 SQLite
- 时间戳 INTEGER 毫秒 epoch；boolean 用 INTEGER 0/1
- `Instant` ↔ `long` 转换在 RowMapper 内完成

### 依赖的前置子任务

- `260521-game-storage-db` 三表已存在
- Step 2 字段契约已冻结

## 【验收契约（Evaluator 输入）】

### 代码结构验证

- [ ] `schema.sql` 含两个新表 + 5 个新索引（FK 含 CASCADE）
- [ ] 4 个新 Java 文件存在（2 entity + 2 repository）
- [ ] Repository 写方法 `synchronized`
- [ ] 列表查询 SQL 不 SELECT *_json 大字段
- [ ] 沿用 `Instant` 时间戳风格

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `cd game-agent-backend && mvn test -Dtest=GameRunEvaluationRepositoryTest` | exit 0 |
| `cd game-agent-backend && mvn test -Dtest=SkillDistillationCandidateRepositoryTest` | exit 0 |
| `cd game-agent-backend && mvn test` | 0 退化 |
| `cd game-agent-backend && mvn compile` | exit 0 |

### 端到端 SSOT 验证

启动 backend 后用 sqlite3 直连 `game-agent-backend/data/game-agent.db`：

```sql
.tables
-- 应含 game_run_evaluations、skill_distillation_candidates

PRAGMA table_info(game_run_evaluations);
-- 字段数与契约一致；created_at 是 INTEGER

PRAGMA foreign_keys = ON;
-- 删一条 sessions 后，关联 evaluations 应级联删（手工验证一次即可）
```

### 数据/字段验收

- [ ] `success`、`degraded` 是 0/1 INTEGER
- [ ] `*_json` 是 TEXT 列
- [ ] FK CASCADE 配置正确
- [ ] 索引覆盖 `(session_id)` `(skill_name, total_score)` `(success, created_at DESC)` 三种查询
- [ ] candidate 默认 status='raw'

### 负面用例

- [ ] schema 重复运行 5 次幂等不报错
- [ ] FK 约束生效：插孤儿 `evaluation_id` 必须报错（前提是连接开了 FK）
- [ ] Repository 写 NULL 必填字段（如 success / iteration_count）应有默认值兜底（DDL 已设 `DEFAULT 0`）

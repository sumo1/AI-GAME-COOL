# Step 2：数据访问层 — Repository

## 背景

Step 1 已铺好 SQLite + JdbcTemplate。本步骤实现三张表的 CRUD：手写 SQL + RowMapper，不用 ORM。所有写操作必须线程安全，因为 AgentLoop 可能并发跑。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `infra/db/SessionEntity.java`（新建）
  - `infra/db/MessageEntity.java`（新建）
  - `infra/db/GameRunEntity.java`（新建）
  - `infra/db/SessionRepository.java`（新建）
  - `infra/db/MessageRepository.java`（新建）
  - `infra/db/GameRunRepository.java`（新建）
  - `src/test/java/com/sumo/agent/infra/db/RepositorySmokeTest.java`（新建测试）

- **不可改文件**：
  - Step 1 产出的 `DataSourceConfig.java`、`schema.sql`、`pom.xml`、`application.yml`（不应有理由改）
  - `agent/loop/*`、`api/*`、前端任何文件（Step 3+ 才动）
  - 老的 `infra/storage/GameStorageService.java`、`SavedGame.java`（保持原样）

- **不可新增的抽象**：
  - 不引入 base Repository 接口或泛型基类（三个表用法相似但不强制对齐）
  - 不写"复杂查询构造器"——所有 SQL 写死字符串常量
  - 不暴露 `JdbcTemplate` 给上层（Repository 是唯一入口）

### 产出清单

#### Entity 类

每个 Entity 都是 POJO + Lombok `@Data`（参考现有 `SavedGame.java` 风格但用 `@Data`）：

1. `SessionEntity`：字段对齐表，`createdAt / updatedAt` 用 `Instant` 类型（毫秒精度），其它对应 SQL
2. `MessageEntity`：字段对齐表，`role` 用 `String`（不引入 enum 简化）
3. `GameRunEntity`：字段对齐表，`favorited` 用 `boolean`（Repository 内部转 0/1）

#### Repository 类

每个 Repository 都是 `@Repository` + 构造器注入 `JdbcTemplate`。**禁止字段注入**（保持线程安全可测）。

`SessionRepository` 提供方法：
- `String insert(SessionEntity)` — 返回 id，若 `id` 为空则用 `UUID.randomUUID().toString()`
- `Optional<SessionEntity> findById(String id)`
- `List<SessionEntity> listRecent(int limit)` — 按 `updated_at DESC`
- `void touch(String id, long updatedAtMs)` — 更新 `updated_at`
- `void incrementCounters(String id, int messageDelta, int gameDelta)` — 用 `UPDATE ... SET message_count = message_count + ?, game_count = game_count + ?`
- `int deleteById(String id)` — 返回受影响行数

`MessageRepository` 提供方法：
- `String insert(MessageEntity)` — 返回 id
- `List<MessageEntity> listBySession(String sessionId)` — 按 `created_at ASC`

`GameRunRepository` 提供方法：
- `String insert(GameRunEntity)` — 返回 id
- `Optional<GameRunEntity> findById(String id)`
- `List<GameRunEntity> listBySession(String sessionId)` — 按 `created_at DESC`
- `List<GameRunEntity> listRecent(int limit)` — 全局，按 `created_at DESC`，**不返回 html 字段**（性能；列表用）
- `List<GameRunEntity> listFavorites(int limit)` — `WHERE favorited = 1 ORDER BY eval_score DESC, created_at DESC`，**不返回 html 字段**
- `Optional<GameRunEntity> findHtmlById(String id)` — 仅查 `id, html` 两个字段（详情接口用）
- `int setFavorited(String id, boolean favorited)` — 返回受影响行数

> **重要**：`listRecent` / `listFavorites` 返回的 Entity 对象，`html` 字段保持 `null`。这是有意的（避免列表接口返回大字段）。详情时另外查。

#### RowMapper

每个 Repository 内部定义一个 `private static final RowMapper<XxxEntity> ROW_MAPPER`，处理：
- `INTEGER` ↔ `Instant.ofEpochMilli`
- `INTEGER 0/1` ↔ `boolean`

#### 写入操作的并发保护

- 所有写方法（insert/update/delete）必须在方法上加 `synchronized`
  - SQLite 在 WAL 模式下读写并发可以，但**写写互斥**——HikariCP 单连接已隔离，Java 层再加一道锁是为了清晰且兜底
- `Read-modify-write` 类操作（`incrementCounters`）使用单条 `UPDATE ... SET col = col + ?` SQL，**禁止**先 SELECT 再 UPDATE

#### 测试 `RepositorySmokeTest`

- 用 Spring Boot Test 起完整应用上下文（不 mock）
- 测试用例：
  1. `insert_session_then_findById`：插入 session，按 id 查回，所有字段一致
  2. `insert_message_increments_session_counter`：先 insert session，然后 insert message，verify `session.message_count` 没变（计数由 Service 层维护，Repository 不自动 +1，本测试 explicit 不测自动 increment——只测 `incrementCounters` 单独调用的效果）
  3. `list_recent_sorted_by_updated_at`：插入 3 个 session，touch 中间那个，verify 顺序
  4. `list_recent_excludes_html`：insert game_run with html，调 `listRecent`，verify 返回 entity `html == null`
  5. `find_html_by_id_returns_html`：insert + `findHtmlById`，verify html 不为空
  6. `set_favorited_then_list_favorites`：insert 多个，set 部分 favorited，verify `listFavorites` 只返回标记的

### 约束（已冻结的边界）

- 不修改 schema（增减字段是 Step 1 的事）
- 不引入第二个数据源、不引入第二种数据库
- 不写"未来可能用"的方法（如 `findByModelKey` 没人用就不写）
- SQL 字符串用 `String` 常量；不使用 `+` 拼接动态条件（所有方法的 WHERE 都是固定的）
- 不在 Repository 里 catch `DataAccessException` 静默吞掉——抛给上层

### 复用的现有模式

- 测试启动方式参考 `src/test/java/com/sumo/agent/agent/AgentLoopIntegrationTest.java`（Spring Boot Test）
- Lombok `@Data` 风格参考前端没有；后端可参考 `agent/skill/SkillDefinition.java`（虽未用 @Data 但说明 POJO 风格）
- `@Repository` + 构造器注入参考 Spring 标准模式

### 依赖的前置子任务

- Step 1：必须完成（schema + DataSource）

## 【验收契约（Evaluator 输入）】

### 代码结构验证

- [ ] `infra/db/` 目录下出现 6 个 .java 文件：3 个 Entity + 3 个 Repository
- [ ] 每个 Repository 类被 `@Repository` 标注
- [ ] 每个 Repository 通过**构造器注入** `JdbcTemplate`（grep `JdbcTemplate` 在 final 字段且构造器参数中出现）
- [ ] `SessionRepository` 提供 6 个 public 方法（insert / findById / listRecent / touch / incrementCounters / deleteById）
- [ ] `MessageRepository` 提供 2 个 public 方法（insert / listBySession）
- [ ] `GameRunRepository` 提供 7 个 public 方法（见产出清单）
- [ ] 所有 `insert/update/delete` 方法签名带 `synchronized` 关键字
- [ ] 测试类 `RepositorySmokeTest` 至少含 6 个 `@Test` 方法

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `mvn -pl game-agent-backend -am compile` | exit 0 |
| `mvn -pl game-agent-backend test -Dtest=RepositorySmokeTest` | 6 个用例全部通过，无 SKIPPED |
| `grep -c "class.*Entity" game-agent-backend/src/main/java/com/sumo/agent/infra/db/*.java` | 3 |
| `grep -c "@Repository" game-agent-backend/src/main/java/com/sumo/agent/infra/db/*.java` | 3 |

### 数据/字段验收

- [ ] `SessionEntity` 字段类型：`createdAt: Instant`、`updatedAt: Instant`、计数字段 `int`
- [ ] `MessageEntity.role` 类型为 `String`（不是 enum）
- [ ] `GameRunEntity.favorited` 类型为 `boolean`
- [ ] `GameRunRepository.listRecent` 返回的 Entity 实例 `html == null`（测试 4 验证）
- [ ] `GameRunRepository.findHtmlById` 返回的 Entity `html != null`（测试 5 验证）

### 负面用例

- [ ] `findById` 查不到 → 返回 `Optional.empty()`，不抛异常
- [ ] `setFavorited` 对不存在的 id → 返回 0（受影响行数），不抛异常
- [ ] `incrementCounters` 对不存在的 session id → 返回（不抛异常），但调用方应注意 0 行受影响
- [ ] `deleteById` 删除 session 时，关联的 messages / game_runs 自动级联删除（FK ON DELETE CASCADE 验证）：测试中 insert session+message+game_run，delete session，verify message/game_run 也消失

### 端到端 SSOT 验证（必跑）

> 本 step 没有自己的 HTTP 端点；端到端验证用一个独立的 Java main 程序（或测试方法触发）+ `sqlite3` 直接查表。**不**通过 mock / mocked Repository。

```bash
set -e
DB_TEST=./game-agent-backend/data/test-step2.db
rm -f ${DB_TEST}*

# 用临时 db 跑测试
cd /Users/sumo/workplace/ai/AI-GAME
AGENT_DB_URL="jdbc:sqlite:${DB_TEST}" \
  mvn -pl game-agent-backend test -Dtest=RepositorySmokeTest -q

# 测试结束后必须查 SSOT：
# 断言：测试用例插过的样本数据应被清理（每个 @Test 应该独立清；如果没清，说明事务管理有问题）
LEFTOVER=$(sqlite3 ${DB_TEST} "SELECT count(*) FROM sessions;" 2>/dev/null || echo 0)
# 注：实际是否要求 0 取决于测试是否带 @Transactional rollback；本契约要求 RepositorySmokeTest 自己负责清理
echo "测试后残留 sessions: $LEFTOVER（仅观察，不强制 0）"

# 断言：FK 真生效——直接构造孤儿 message 应失败
sqlite3 ${DB_TEST} "PRAGMA foreign_keys=ON;
INSERT INTO messages(id, session_id, role, content, created_at) VALUES ('m1', 'nonexistent', 'user', 'x', 0);" 2>&1 \
  | grep -qi "FOREIGN KEY constraint failed" \
  || { echo "FAIL: FK 未阻止孤儿 message"; exit 1; }

# 清理
rm -f ${DB_TEST}*

echo "STEP 2 端到端 SSOT 验证：全部通过"
```

**通过标准**：`mvn test` 6 用例全过 + FK 真实生效断言通过。

### 剩余风险

- 高并发写场景未做压测（本任务范围外）；synchronized + WAL 模式对当前 QPS 足够
- `messages.content` 字段大小未限制（LLM 输出可能很长）；当前业务实际没问题，未来若超 SQLite TEXT 默认上限再优化

## 后续 Step 依赖

Step 3 (服务编排) 依赖本 step 的 Repository 接口稳定。

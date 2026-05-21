# Step 1：基础设施 — JDBC + SQLite + schema.sql

## 背景

当前项目无任何数据库依赖。本步骤铺好地基：引入 SQLite 驱动 + Spring JDBC、定义三张表的 DDL、配置 DataSource。后续 Step 2-4 都依赖此步骤产出。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `game-agent-backend/pom.xml`（仅新增依赖）
  - `game-agent-backend/src/main/resources/application.yml`（仅新增 datasource 段）
  - `game-agent-backend/src/main/resources/schema.sql`（新建）
  - `game-agent-backend/src/main/java/com/sumo/agent/infra/db/DataSourceConfig.java`（新建）

- **不可改文件**：
  - `infra/storage/GameStorageService.java`（本 step 不动）
  - `agent/loop/*`（本 step 不动）
  - `api/*`（本 step 不动）
  - `application-custom.yml`（不动）
  - 前端任何文件

- **不可新增的抽象**：
  - 不新增"数据库选型抽象层"——直接绑 SQLite，将来切换是另一个任务
  - 不引入 JPA、Flyway、Liquibase、MyBatis、JOOQ
  - 不创建 Entity/DTO 类（Step 2 才做）

### 产出清单

1. `pom.xml` 新增两个依赖：
   - `org.springframework.boot:spring-boot-starter-jdbc`（版本由 parent 管理）
   - `org.xerial:sqlite-jdbc`（指定 `3.45.3.0`）

2. `application.yml` 新增 `spring.datasource` 段：
   ```yaml
   spring:
     datasource:
       url: ${AGENT_DB_URL:jdbc:sqlite:./data/game-agent.db}
       driver-class-name: org.sqlite.JDBC
       hikari:
         maximum-pool-size: 1     # SQLite 单写者
         minimum-idle: 1
         pool-name: GameAgentSqlitePool
     sql:
       init:
         mode: always
         schema-locations: classpath:schema.sql
         continue-on-error: false
   ```
   注意：把 `spring.datasource` 放在现有 `spring:` 顶层下；不破坏现有 `spring.ai`、`spring.jackson`、`spring.thymeleaf` 配置。

3. `schema.sql` 三张表（全部 `CREATE TABLE IF NOT EXISTS`）：

   ```sql
   -- 会话：用户与系统的一次对话上下文
   CREATE TABLE IF NOT EXISTS sessions (
       id              TEXT PRIMARY KEY,
       title           TEXT NOT NULL,
       model_key       TEXT,
       created_at      INTEGER NOT NULL,
       updated_at      INTEGER NOT NULL,
       message_count   INTEGER NOT NULL DEFAULT 0,
       game_count      INTEGER NOT NULL DEFAULT 0
   );

   CREATE INDEX IF NOT EXISTS idx_sessions_updated_at ON sessions(updated_at DESC);

   -- 消息：会话内的一条用户输入或 LLM 响应
   CREATE TABLE IF NOT EXISTS messages (
       id              TEXT PRIMARY KEY,
       session_id      TEXT NOT NULL,
       role            TEXT NOT NULL,             -- user | assistant | system
       content         TEXT NOT NULL,
       iterations      INTEGER,
       eval_score      INTEGER,
       created_at      INTEGER NOT NULL,
       FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
   );

   CREATE INDEX IF NOT EXISTS idx_messages_session_created ON messages(session_id, created_at);

   -- 游戏运行记录：一条 assistant message 对应一次成功生成的游戏 HTML
   CREATE TABLE IF NOT EXISTS game_runs (
       id              TEXT PRIMARY KEY,
       session_id      TEXT NOT NULL,
       message_id      TEXT NOT NULL,
       title           TEXT,
       html            TEXT NOT NULL,
       eval_score      INTEGER NOT NULL DEFAULT 0,
       iterations      INTEGER NOT NULL DEFAULT 0,
       favorited       INTEGER NOT NULL DEFAULT 0,    -- 0/1
       created_at      INTEGER NOT NULL,
       FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
       FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
   );

   CREATE INDEX IF NOT EXISTS idx_game_runs_session ON game_runs(session_id);
   CREATE INDEX IF NOT EXISTS idx_game_runs_favorited ON game_runs(favorited, eval_score DESC);
   ```

4. `infra/db/DataSourceConfig.java`：
   - `@Configuration` 类
   - `@PostConstruct` 阶段执行 `PRAGMA journal_mode = WAL;` 和 `PRAGMA foreign_keys = ON;`
   - `@PostConstruct` 阶段确保 `./data/` 目录存在（`Files.createDirectories`）
   - 注入 `JdbcTemplate` 后跑两条 PRAGMA SQL；如果失败用 `log.warn` 不抛
   - 不在此类中包业务方法

### 约束（已冻结的边界）

- 默认 DB 文件路径 `./data/game-agent.db`，**不放到** `saved-games/` 下
- 把 `data/` 加入 `.gitignore`（如未在 patterns 里）
- 时间戳字段统一用 `INTEGER`（毫秒 epoch）；不用 SQLite 的 `DATETIME` 类型——和 LocalDateTime 互转更稳
- 三表全部用 `TEXT` 主键（UUID 字符串）；不用自增 INTEGER
- `schema.sql` 必须幂等（IF NOT EXISTS），允许应用重启重复执行
- `spring.sql.init.continue-on-error: false`——schema 失败必须立即崩溃，不静默
- HikariCP `maximum-pool-size: 1`——SQLite 不允许多写者，连接池上限就是 1

### 复用的现有模式

- 配置类风格参考 `infra/config/JacksonConfig.java`、`infra/config/RestClientConfig.java`
- `@PostConstruct` 风格参考 `agent/skill/SkillLoader.java#init()`
- 日志参考 `@Slf4j` + `log.info` 中文消息（与 `SkillLoader` 一致）

### 依赖的前置子任务

无（本 step 是首步）。

## 【验收契约（Evaluator 输入）】

### 代码结构验证

- [ ] `pom.xml` 新增 `<artifactId>spring-boot-starter-jdbc</artifactId>` 和 `<artifactId>sqlite-jdbc</artifactId>`，sqlite 版本 `3.45.3.0`
- [ ] `pom.xml` 中 sqlite-jdbc 依赖**不写**在 `<dependencyManagement>` 里（直接 dependencies）
- [ ] `application.yml` 顶层 `spring:` 下出现 `datasource:` 段，包含 `url` / `driver-class-name` / `hikari` / 同级 `sql.init`
- [ ] `application.yml` 中 `spring.ai`、`spring.jackson`、`spring.thymeleaf` 现有段未被改动
- [ ] `src/main/resources/schema.sql` 存在，`grep -c "CREATE TABLE IF NOT EXISTS"` 输出 = 3
- [ ] `schema.sql` 包含 `sessions / messages / game_runs` 三个表名（`grep -c "sessions\|messages\|game_runs"` ≥ 3）
- [ ] `schema.sql` 包含至少 4 个 `CREATE INDEX IF NOT EXISTS`
- [ ] `infra/db/DataSourceConfig.java` 存在，被 `@Configuration` 标注，含 `@PostConstruct` 方法
- [ ] `.gitignore` 含 `data/` 或 `*.db` 模式

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `mvn -pl game-agent-backend -am compile` | exit 0，无编译错误 |
| `mvn -pl game-agent-backend dependency:tree \| grep -E "sqlite-jdbc\|spring-jdbc"` | 输出含 `sqlite-jdbc:jar:3.45.3.0` 和 `spring-jdbc:jar` |
| `cd game-agent-backend && mvn spring-boot:run`（启动 30 秒后 `Ctrl+C`） | 启动日志含 `HikariPool-` 字样且无 ERROR 级别异常；`./data/game-agent.db` 文件被创建 |
| 启动后执行 `sqlite3 game-agent-backend/data/game-agent.db ".tables"` | 输出包含 `game_runs  messages  sessions`（顺序不限） |
| 启动后执行 `sqlite3 game-agent-backend/data/game-agent.db "PRAGMA journal_mode;"` | 输出 `wal` |
| 启动后执行 `sqlite3 game-agent-backend/data/game-agent.db "PRAGMA foreign_keys;"` | 输出 `1` |

### 数据/字段验收

- [ ] `sessions` 表 7 个字段：id / title / model_key / created_at / updated_at / message_count / game_count
- [ ] `messages` 表 7 个字段：id / session_id / role / content / iterations / eval_score / created_at
- [ ] `game_runs` 表 10 个字段：id / session_id / message_id / title / html / eval_score / iterations / favorited / created_at + 两个 FK
- [ ] 时间戳字段全部为 `INTEGER` 类型（`sqlite3 ... ".schema sessions"` 输出验证）
- [ ] `game_runs.favorited` 默认值为 `0`、类型 `INTEGER`

### 负面用例

- [ ] 启动时若 `./data/` 不存在 → 应用自动创建，不报错（删除 `data/` 后重启验证）
- [ ] 重复启动两次（不删 db 文件）→ 启动成功，schema.sql 不报"table already exists"（验证幂等）
- [ ] 修改 `schema.sql` 中某条 SQL 故意写错 → 启动失败、应用退出（验证 `continue-on-error: false`）。验证完恢复正确 SQL

### 端到端 SSOT 验证（必跑）

> **原则**：穿透抽象层，直接 `sqlite3` 查 schema 真值。详见 `docs/engineering/testing.md`。

```bash
set -e

# 1. 清环境
rm -rf ./game-agent-backend/data
rm -f /tmp/aigame-step1.log

# 2. 启服务（后台），最多等 60s 就绪
cd /Users/sumo/workplace/ai/AI-GAME
( cd game-agent-backend && mvn compile -q ) || { echo "FAIL: compile"; exit 1; }
( cd game-agent-backend && mvn spring-boot:run -q ) > /tmp/aigame-step1.log 2>&1 &
APP_PID=$!
trap "kill $APP_PID 2>/dev/null || true" EXIT
for i in $(seq 1 60); do
  curl -sf http://localhost:8088/api/game/agents > /dev/null && break
  sleep 1
done
curl -sf http://localhost:8088/api/game/agents > /dev/null || { echo "FAIL: 应用未启动"; exit 1; }

# 3. 查 SSOT 断言（每条都查最原始的真相）
DB=./game-agent-backend/data/game-agent.db   # mvn spring-boot:run cwd 是 game-agent-backend，DB 落在那里

# 断言 1：DB 文件存在
[ -f "$DB" ] || { echo "FAIL: DB 文件不存在"; exit 1; }

# 断言 2：三张表都建了
TABLES=$(sqlite3 "$DB" ".tables")
echo "$TABLES" | grep -q sessions   || { echo "FAIL: sessions 表缺失"; exit 1; }
echo "$TABLES" | grep -q messages   || { echo "FAIL: messages 表缺失"; exit 1; }
echo "$TABLES" | grep -q game_runs  || { echo "FAIL: game_runs 表缺失"; exit 1; }

# 断言 3：WAL 模式生效
MODE=$(sqlite3 "$DB" "PRAGMA journal_mode;")
[ "$MODE" = "wal" ] || { echo "FAIL: journal_mode=$MODE 不是 wal"; exit 1; }

# 断言 4：FK 约束实际生效（注意：PRAGMA foreign_keys 是连接级；sqlite3 CLI 新连接默认是 0
# 必须显式开后再测，且通过"插孤儿数据应失败"作为真实证据，详见 memory/2026-05-21-sqlite-pragma-per-connection.md）
FK_TEST_OUT=$(sqlite3 "$DB" "PRAGMA foreign_keys=ON;
INSERT INTO messages(id, session_id, role, content, created_at) VALUES('orphan-test', 'no-such-session', 'user', 'x', 0);" 2>&1 || true)
echo "$FK_TEST_OUT" | grep -qi "FOREIGN KEY constraint failed" \
  || { echo "FAIL: FK 未阻止孤儿插入: $FK_TEST_OUT"; exit 1; }
# 兜底清理（若上面意外成功）
sqlite3 "$DB" "DELETE FROM messages WHERE id='orphan-test';" 2>/dev/null || true

# 同时验证应用层（启动日志）确实开了 FK——这是 connection-init-sql 生效证据
grep -q "foreign_keys = 1" /tmp/aigame-step1.log \
  || { echo "FAIL: 启动日志无 foreign_keys=1"; exit 1; }

# 断言 5：sessions 表字段数 = 7
COLS=$(sqlite3 "$DB" "PRAGMA table_info(sessions);" | wc -l | tr -d ' ')
[ "$COLS" = "7" ] || { echo "FAIL: sessions 字段数 $COLS != 7"; exit 1; }

# 断言 6：索引存在
sqlite3 "$DB" "SELECT name FROM sqlite_master WHERE type='index';" | grep -q idx_sessions_updated_at \
  || { echo "FAIL: idx_sessions_updated_at 缺失"; exit 1; }

# 断言 7：幂等性（重启后不应报错）
kill $APP_PID; wait $APP_PID 2>/dev/null || true
mvn -pl game-agent-backend spring-boot:run -q > /tmp/aigame-step1-2.log 2>&1 &
APP_PID=$!
for i in $(seq 1 60); do
  curl -sf http://localhost:8088/api/game/agents > /dev/null && break
  sleep 1
done
grep -i "table.*already exists" /tmp/aigame-step1-2.log && { echo "FAIL: schema 不幂等"; exit 1; } || true

# 4. 关停
kill $APP_PID; wait $APP_PID 2>/dev/null || true

echo "STEP 1 端到端 SSOT 验证：全部通过"
```

**通过标准**：脚本 exit 0 + 输出 "全部通过"。任何一条断言 FAIL 即整体失败。

### 剩余风险

- 不在本 step 验证：表的实际读写（Step 2 验证）
- 不在本 step 验证：与 AgentLoop 的集成（Step 3 验证）

## 后续 Step 依赖

Step 2 (Repository) 依赖本 step 的 schema 与 DataSource 就位。

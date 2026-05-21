# Step 3：服务编排 — AgentLoop 写入路径与老服务降级

## 背景

Step 1 + Step 2 已就位，但还没人写库。本步骤接通"AgentLoop 跑完一次 → 三表都有记录"。原则：**不动 AgentLoop 内部迭代逻辑**，只在入口/出口加 hook；老的 `GameStorageService`（文件系统）保留接口语义、加 `@Deprecated`，不删任何方法。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `infra/storage/SessionService.java`（新建）
  - `api/GameChatController.java`（仅改造 `/api/game/v2/generate` 入口，写入 session+message+game_run）
  - `infra/storage/GameStorageService.java`（**只加** `@Deprecated` 标注 + 类级注释引导 → 新接口；不动方法实现）
  - `infra/storage/SavedGame.java`（**只加** `@Deprecated` 注释；不动字段）
  - `src/test/java/com/sumo/agent/infra/storage/SessionServiceTest.java`（新建）

- **不可改文件**：
  - `agent/loop/AgentLoop.java`（不进 AgentLoop 内部）
  - `agent/loop/WorkingMemory.java` / `AgentPrompts.java` / `AgentLoopResult.java`（保持原契约）
  - `agent/tools/*`（任何 Tool 不动）
  - `agent/evaluation/*`、`agent/skill/*`（不动）
  - `api/GameStorageController.java`（Step 4 才动）
  - 前端任何文件
  - Step 1 / Step 2 的产出

- **不可新增的抽象**：
  - 不抽 "Persistable Loop" 之类的接口
  - 不引入 Spring Event / @EventListener（直接调用即可）
  - 不在 `AgentLoop.run()` 内部插入回调钩子——所有写库在 Controller 层完成

### 产出清单

#### `SessionService`（新增）

`@Service` 类，职责：把"一次生成请求"原子地写入 sessions+messages+game_runs。

```java
public class SessionService {
    // 构造器注入 SessionRepository / MessageRepository / GameRunRepository

    /** 创建或复用 session。如 sessionId 为空或在库中不存在则新建，否则 touch updatedAt。 */
    public SessionEntity ensureSession(String sessionId, String userInput, String modelKey);

    /** 把一次完整生成（user 输入 + assistant 响应 + 可选 HTML）写入 messages 和 game_runs。 */
    public RecordResult recordRun(
        String sessionId,
        String userInput,
        AgentLoopResult result,    // 来自 agent.loop
        String modelKey
    );

    /** RecordResult 是个简单 record：含本次写入的 userMessageId / assistantMessageId / gameRunId（可空）。 */
    public record RecordResult(String userMessageId, String assistantMessageId, String gameRunId) {}
}
```

**实现要点**：

1. `ensureSession`：
   - 如果 `sessionId` 不为空且在库中存在 → `touch(id, now)` 后返回原 entity
   - 否则：生成新 UUID，`title` 用 `userInput.substring(0, min(40, len))` + `...`，写入并返回
   - **整个方法 synchronized**

2. `recordRun`：
   - **写入用户消息**：`MessageEntity` role=`user`、content=userInput、iterations/eval_score=null
   - **写入 assistant 消息**：role=`assistant`、content=`result.llmMessage()`、iterations=`result.iterations()`、eval_score=`result.evalScore()`
   - **写入 game_run**（仅当 `result.success() && result.gameHtml() != null`）：
     - title 暂时用 sessionId.title（后续任务可优化为从 HTML title 提取）
     - html、eval_score、iterations 来自 result
     - favorited = false
   - **更新 session 计数**：`incrementCounters(sessionId, +2, gameRun==null ? 0 : +1)`
   - 返回 `RecordResult`

3. 异常处理：
   - 任何一步失败 → 抛 `RuntimeException`，调用方记 `log.error` 后向上抛但**不应**因此影响游戏返回（见 Controller 改造）

#### `GameChatController` 改造（仅 `/api/game/v2/generate`）

只改 `generateGameV2(...)` 方法。改造要点：

1. 注入 `SessionService`（构造器注入或 `@Autowired` 字段，按现有控制器风格）
2. 在 `Mono.fromCallable` 内：
   ```
   先调 sessionService.ensureSession(...)
   再调 agentLoop.run(...)
   再调 sessionService.recordRun(...)
   ```
3. **关键**：写库失败不能让用户失败——`recordRun` 抛异常时 `log.error` 但响应仍返回 `agentLoop.run` 的结果（用户感知不到写库挂了，但日志会暴露）
4. 响应中的 `sessionId` 字段使用 `ensureSession` 返回的真实 session id（不是凭空 UUID）
5. **不改** `/api/game/generate`（V1 路径），它继续走旧流程

#### 老服务降级

- `GameStorageService.java` 类级注释加：
  ```java
  /**
   * @deprecated since 2026-05-21
   * 文件系统存储已被 DB 化。请使用 SessionService + GameRunRepository 接入。
   * 本类暂保留以维持 /api/game/storage/* 端点向后兼容（Step 4 改造）。
   * 后续任务清理。
   */
  @Deprecated(since = "2026-05-21")
  ```
  **不动任何方法实现**。

- `SavedGame.java` 同样加 `@Deprecated(since = "2026-05-21")` 类级标注；字段不动。

#### 测试 `SessionServiceTest`

- Spring Boot Test，真实启动，真实 SQLite（用临时 db 文件，`@DynamicPropertySource` 改 `spring.datasource.url`，避开污染开发库）
- 用例：
  1. `ensureSession_creates_when_missing`：传 null sessionId → 新建，title 截断到 40 字符
  2. `ensureSession_touches_existing`：先创建，再调 ensureSession 同一 id → updated_at 变大
  3. `recordRun_writes_two_messages_and_one_game_run`：跑成功的 result，verify 库里有 user+assistant message + game_run
  4. `recordRun_skips_game_when_failure`：跑 `AgentLoopResult.failure(...)`，verify 没写 game_run
  5. `recordRun_increments_counters`：verify session.message_count += 2、game_count += 1

### 约束（已冻结的边界）

- AgentLoop 内部行为完全不变：不能依赖任何"loop 中途回调"——只能在 run() 前后写
- SessionService 的方法都是 synchronized，但不要把 AgentLoop.run() 也拉进同步块（会让所有请求串行）
- 控制器中**先 ensureSession 再调 agentLoop，最后写 recordRun**——顺序不能颠倒
- 写库失败**不能**冒泡到 HTTP 层导致用户报错（容错优先于一致性，符合现有 V1 容错风格）

### 复用的现有模式

- `@Service` 风格参考 `infra/storage/GameStorageService.java`
- 字符串截断 / UUID 生成参考现有 Controller 中已有的 `UUID.randomUUID().toString()` 用法
- AgentLoopResult 的字段访问参考现有 Controller `result.success()` / `result.gameHtml()` / `result.llmMessage()` 等

### 依赖的前置子任务

- Step 1（schema 就位）
- Step 2（Repository 可用）

## 【验收契约（Evaluator 输入）】

### 代码结构验证

- [ ] `infra/storage/SessionService.java` 存在，被 `@Service` 标注
- [ ] `SessionService` 含 `ensureSession` / `recordRun` 两个 public 方法 + 一个 `RecordResult` record
- [ ] 这两个方法都被 `synchronized` 标注
- [ ] `SessionService` 通过构造器注入 3 个 Repository
- [ ] `GameChatController.generateGameV2` 中按"ensureSession → agentLoop.run → recordRun"顺序调用（用 `grep -n "ensureSession\|agentLoop.run\|recordRun" GameChatController.java` 验证三者出现且顺序对）
- [ ] `GameStorageService.java` 类上含 `@Deprecated(since = "2026-05-21")`
- [ ] `SavedGame.java` 类上含 `@Deprecated(since = "2026-05-21")`
- [ ] `GameStorageService.java` 的 6 个 public 方法全部仍在（grep 数量未减少）
- [ ] `SessionServiceTest` 至少含 5 个 `@Test`

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `mvn -pl game-agent-backend -am compile` | exit 0；只允许 `@Deprecated` 引发的 unused / deprecation 警告，不允许 ERROR |
| `mvn -pl game-agent-backend test -Dtest=SessionServiceTest` | 5 个用例全部通过 |
| `mvn -pl game-agent-backend test -Dtest=AgentLoopIntegrationTest` | 现有用例继续通过（未引入回归） |
| 启动应用，`curl -X POST http://localhost:8088/api/game/v2/generate -H "Content-Type: application/json" -d '{"userInput":"做一个10以内加法游戏"}'` | 响应 success=true、含 sessionId；`sqlite3 ./data/game-agent.db "SELECT count(*) FROM sessions; SELECT count(*) FROM messages; SELECT count(*) FROM game_runs;"` 输出分别 ≥1, ≥2, ≥1 |
| 同 sessionId 二次请求 | sessions 数不增，messages +2，game_runs +1（如成功）；session.updated_at 变大 |

### 数据/字段验收

- [ ] DB 中 `sessions.title` 长度 ≤ 43（40 + "..."）
- [ ] `messages.role` 取值只有 `user / assistant`，没有 `system`（本任务不写 system message）
- [ ] `messages.iterations` 在 user 消息上为 NULL，在 assistant 消息上为整数
- [ ] `messages.eval_score` 在 user 消息上为 NULL
- [ ] `game_runs.favorited` 默认 0
- [ ] `game_runs.html` 长度 > 0（实际生成内容）
- [ ] `session.message_count` 与实际 messages 数一致；`session.game_count` 与实际 game_runs 数一致

### 负面用例

- [ ] **写库失败容错**：临时把 `data/game-agent.db` 文件改为只读（`chmod 444`），再跑 `/api/game/v2/generate` → HTTP 响应仍返回成功 + html，但日志含 `log.error` 关于"写入会话失败"。验证完恢复 666
- [ ] V1 端点 `/api/game/generate` 仍可用 → 返回旧格式，DB 中**不**新增任何记录（V1 不写 DB）
- [ ] 响应 `sessionId` 与 DB 中 `sessions.id` 完全一致
- [ ] AgentLoopResult.failure 时（构造一次会失败的请求）→ messages 写了（user + assistant 都写）但 game_runs 不写

### 端到端 SSOT 验证（必跑）

> 这是本任务最关键的端到端：从 HTTP 调用到 DB 行的全链路真实验证。**不读** AgentLoop 返回值，**不读**日志，只查 DB 真值。

```bash
set -e
DB=./game-agent-backend/data/game-agent.db

# 1. 清环境 + 启服务
rm -f ${DB}*
cd /Users/sumo/workplace/ai/AI-GAME
mvn -pl game-agent-backend -am compile -q
mvn -pl game-agent-backend spring-boot:run -q > /tmp/aigame-step3.log 2>&1 &
APP_PID=$!
trap "kill $APP_PID 2>/dev/null || true" EXIT
for i in $(seq 1 60); do
  curl -sf http://localhost:8088/api/game/agents > /dev/null && break
  sleep 1
done

# 2. 触发链路
RESP=$(curl -sX POST http://localhost:8088/api/game/v2/generate \
  -H 'Content-Type: application/json' \
  -d '{"userInput":"做一个10以内加法游戏","options":{"model":"dashscope"}}' \
  --max-time 180)
SUCCESS=$(echo "$RESP" | jq -r '.success')
SESSION_ID=$(echo "$RESP" | jq -r '.sessionId')
[ "$SUCCESS" = "true" ] || { echo "FAIL: 生成失败 $RESP"; exit 1; }
[ -n "$SESSION_ID" ] || { echo "FAIL: 无 sessionId"; exit 1; }

# 3. 查 SSOT 断言（关键：穿透 service / controller，直接看 DB 真相）
# 断言 1：sessions 表恰好有 1 行，且 id = 响应的 sessionId
SESSION_ROWS=$(sqlite3 "$DB" "SELECT count(*) FROM sessions WHERE id='$SESSION_ID';")
[ "$SESSION_ROWS" = "1" ] || { echo "FAIL: session 未写入 (rows=$SESSION_ROWS)"; exit 1; }

# 断言 2：messages 表对该 session 有恰好 2 条
MSG_COUNT=$(sqlite3 "$DB" "SELECT count(*) FROM messages WHERE session_id='$SESSION_ID';")
[ "$MSG_COUNT" = "2" ] || { echo "FAIL: messages 数 $MSG_COUNT != 2"; exit 1; }

# 断言 3：user 消息和 assistant 消息各一条
USER_COUNT=$(sqlite3 "$DB" "SELECT count(*) FROM messages WHERE session_id='$SESSION_ID' AND role='user';")
ASST_COUNT=$(sqlite3 "$DB" "SELECT count(*) FROM messages WHERE session_id='$SESSION_ID' AND role='assistant';")
[ "$USER_COUNT" = "1" ] && [ "$ASST_COUNT" = "1" ] \
  || { echo "FAIL: role 分布 user=$USER_COUNT assistant=$ASST_COUNT"; exit 1; }

# 断言 4：assistant 消息有评分和迭代数（不是 NULL）
ASST_SCORE=$(sqlite3 "$DB" "SELECT eval_score FROM messages WHERE session_id='$SESSION_ID' AND role='assistant';")
[ -n "$ASST_SCORE" ] && [ "$ASST_SCORE" != "" ] || { echo "FAIL: assistant 无评分"; exit 1; }

# 断言 5：game_runs 表至少 1 行（如果 AgentLoop 成功），且 html 不空
GAME_ROWS=$(sqlite3 "$DB" "SELECT count(*) FROM game_runs WHERE session_id='$SESSION_ID';")
[ "$GAME_ROWS" -ge 1 ] || { echo "FAIL: game_runs 行数 $GAME_ROWS"; exit 1; }
HTML_LEN=$(sqlite3 "$DB" "SELECT length(html) FROM game_runs WHERE session_id='$SESSION_ID' LIMIT 1;")
[ "$HTML_LEN" -gt 100 ] || { echo "FAIL: html 长度 $HTML_LEN"; exit 1; }

# 断言 6：HTML 是真 HTML（含 DOCTYPE）
sqlite3 "$DB" "SELECT html FROM game_runs WHERE session_id='$SESSION_ID' LIMIT 1;" \
  | grep -qi 'doctype html' || { echo "FAIL: html 不含 DOCTYPE"; exit 1; }

# 断言 7：session 计数器与实际 message/game 数一致
COUNTERS=$(sqlite3 "$DB" "SELECT message_count, game_count FROM sessions WHERE id='$SESSION_ID';")
[ "$COUNTERS" = "2|$GAME_ROWS" ] \
  || { echo "FAIL: counters $COUNTERS 与实际 messages=2/games=$GAME_ROWS 不符"; exit 1; }

# 断言 8：同 sessionId 二次请求不新增 session
RESP2=$(curl -sX POST http://localhost:8088/api/game/v2/generate \
  -H 'Content-Type: application/json' \
  -d "{\"userInput\":\"难一点\",\"sessionId\":\"$SESSION_ID\"}" \
  --max-time 180)
SESSION_ROWS2=$(sqlite3 "$DB" "SELECT count(*) FROM sessions;")
[ "$SESSION_ROWS2" = "1" ] || { echo "FAIL: 二次请求新增 session"; exit 1; }

# 断言 9：V1 端点不写 DB
SESS_BEFORE=$(sqlite3 "$DB" "SELECT count(*) FROM sessions;")
curl -sX POST http://localhost:8088/api/game/generate \
  -H 'Content-Type: application/json' \
  -d '{"userInput":"V1 测试"}' --max-time 180 > /dev/null
SESS_AFTER=$(sqlite3 "$DB" "SELECT count(*) FROM sessions;")
[ "$SESS_BEFORE" = "$SESS_AFTER" ] || { echo "FAIL: V1 也写了 DB"; exit 1; }

# 4. 关停
kill $APP_PID; wait $APP_PID 2>/dev/null || true

echo "STEP 3 端到端 SSOT 验证：9 条断言全部通过"
```

**通过标准**：9 条断言全过。允许 LLM 调用因网络抖动失败一次重试，但断言逻辑不可放宽。

### 剩余风险

- 不在本 step 验证：列表 API、收藏 API（Step 4 验证）
- 不在本 step 验证：前端展示（Step 4b 验证）
- 写库性能在大并发下未测；当前 QPS 远低于阈值

## 后续 Step 依赖

Step 4a / 4b 都依赖本 step 写入路径就位，库里有数据可读。

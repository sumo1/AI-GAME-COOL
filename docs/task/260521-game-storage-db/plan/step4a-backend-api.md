# Step 4a：后端 API（与 Step 4b 并行）

## 背景

DB 里有数据后，前端需要：列出会话、展开某会话的消息、列出游戏（含收藏过滤）、读取游戏 HTML、收藏/取消、复制会话（在历史会话基础上重开新会话）。本 step 暴露这些端点。

**与 Step 4b 并行**：本 step 文件范围限定在后端 `api/`、前端不动；Step 4b 反之。两端的契约就是本 plan 中"API 契约"段落，前后端按字面意思对齐。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `api/SessionController.java`（新建）
  - `api/GameStorageController.java`（**仅** `listGames()` 方法的实现改读 game_runs 表；其它方法不动）
  - `src/test/java/com/sumo/agent/api/SessionControllerTest.java`（新建）

- **不可改文件**：
  - 前端任何文件
  - `agent/loop/*`、`agent/tools/*`、`agent/skill/*`、`agent/evaluation/*`
  - `infra/db/*`（Step 2 产出，稳定）
  - `infra/storage/SessionService.java`（Step 3 产出）：本 step 可调用，不可改方法签名
  - `api/GameChatController.java`（Step 3 已改完）

- **不可新增的抽象**：
  - 不引入 OpenAPI / Swagger（先简）
  - 不写 Filter / Interceptor / Aspect
  - 响应体直接用 `ResponseEntity<Map<String, Object>>` 或新建简单 DTO record，不引入 Spring HATEOAS

### 产出清单

#### `SessionController`（新建）

`@RestController @RequestMapping("/api/sessions") @CrossOrigin(origins = "*")`，构造器注入 `SessionRepository` + `MessageRepository` + `GameRunRepository`。

**API 契约（前后端必须对齐这个）**：

| 方法 | 路径 | 输入 | 输出 |
|------|------|------|------|
| GET | `/api/sessions?limit=20` | 查询参数 limit（默认 20，上限 100） | `{success:true, data:[{id,title,modelKey,createdAt,updatedAt,messageCount,gameCount}], count:N}` |
| GET | `/api/sessions/{id}` | path | `{success:true, data:{id,title,modelKey,createdAt,updatedAt,messageCount,gameCount}}` 或 404 `{success:false,error:"会话不存在"}` |
| GET | `/api/sessions/{id}/messages` | path | `{success:true, data:[{id,sessionId,role,content,iterations,evalScore,createdAt}], count:N}` 按时间正序 |
| GET | `/api/sessions/{id}/games` | path | `{success:true, data:[{id,sessionId,messageId,title,evalScore,iterations,favorited,createdAt}], count:N}` 不含 html |
| DELETE | `/api/sessions/{id}` | path | `{success:true}` 或 404 |
| POST | `/api/sessions/{id}/clone` | path（无请求体） | `{success:true, data:{newSessionId, sourceSessionId, copiedMessages:N}}`：把原会话的所有 messages 复制到新 session（新 ids，新 createdAt），game_runs **不复制**（避免重复存大字段） |

时间字段统一用毫秒 epoch（`Instant.toEpochMilli()`）；前端按 `new Date(ms)` 处理。

#### `GameStorageController.listGames()` 改造

**只改这一个方法**。改造为：
- 内部改调 `GameRunRepository.listRecent(100)` 而不是 `gameStorageService.listGames()`
- 响应体 schema 必须与现状一致，前端不感知（兼容期）：
  ```json
  {
    "success": true,
    "count": N,
    "data": [{
      "id": "<game_run.id>",
      "title": "<game_run.title>",
      "type": null,            // 老字段，新表无对应
      "ageGroup": null,
      "difficulty": null,
      "theme": null,
      "fileName": null,
      "fileSize": 0,
      "createdAt": "<ISO 字符串，由 Instant 格式化>",
      "updatedAt": "<同 createdAt>",
      "evalScore": <int>,       // 新增字段
      "favorited": <bool>       // 新增字段
    }]
  }
  ```
- 旧字段（type/ageGroup/difficulty/theme/fileName/fileSize）填 null/0；前端默认能渲染（参考 `ServerGameHistory.tsx` 现有处理）
- 老的"按 fileName/saveGame"等其他端点（`/save`、`/{gameId}`、`/stats`、`/batch` DELETE）**不动**，继续走 `gameStorageService`（兼容期）；这些端点的兼容下个任务再清理

新增"详情"端点（在 `SessionController` 不在 `GameStorageController`）：

| 方法 | 路径 | 输出 |
|------|------|------|
| GET | `/api/sessions/games/{id}/html` | `{success:true, data:{id, html}}` 或 404 |
| POST | `/api/sessions/games/{id}/favorite` | `{success:true, data:{id, favorited:true}}` |
| POST | `/api/sessions/games/{id}/unfavorite` | `{success:true, data:{id, favorited:false}}` |
| GET | `/api/sessions/games/favorites?limit=50` | `{success:true, data:[...], count:N}` 同 listGames 字段 |

> 把"游戏详情/收藏"放到 `SessionController` 是因为它语义上属于"会话产出物"，而 `GameStorageController` 是兼容期遗物。

#### 测试 `SessionControllerTest`

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` 或 `MockMvc`
- 用例：
  1. `list_sessions_returns_recent_first`：插 3 个 session，touch 中间一个，verify 顺序
  2. `get_messages_returns_in_chronological_order`：插 user + assistant 各一个，verify 顺序
  3. `clone_session_copies_messages_not_games`：原 session 有 2 messages + 1 game_run，clone 后新 session 有 2 messages + 0 game_run，原 session 不变
  4. `favorite_then_list_favorites`：标记一个，verify 出现在 favorites 接口
  5. `list_games_response_shape`：列表结果含 `id, title, evalScore, favorited` 字段，html 字段不在响应中（或为 null）
  6. `get_html_returns_full_content`：通过 `/games/{id}/html` 拿到 html 不为空
  7. `delete_session_cascades`：DELETE session 后 messages 和 game_runs 都查不到（FK CASCADE 验证）

### 约束（已冻结的边界）

- 老 `/api/game/storage/save`、`/{gameId}`、`/stats`、`/batch` 端点**不改**（兼容期）
- 老 `/api/game/storage/list` 接口的**响应 JSON shape**保持向后兼容（前端不调整字段名也能用）
- 不引入分页对象（直接 limit）；前端列表上限 100，够用
- 错误响应统一 `{success:false, error:"<message>"}`
- 时间字段统一毫秒 epoch（`messages` 接口用 `createdAt`），但 `GameStorageController.listGames` 由于兼容老字段保持 ISO 字符串
- 不暴露 schema/db 内部错误细节给前端（`log.error` 落日志，响应给"请稍后重试"）

### 复用的现有模式

- 控制器风格参考 `api/GameStorageController.java`：`ResponseEntity<?>` + `Map<String, Object>` + `@CrossOrigin`
- 错误响应格式参考现有控制器
- 测试启动方式参考 `AgentLoopIntegrationTest`

### 依赖的前置子任务

- Step 1 / 2 / 3 全部完成（DB + Repository + 写入路径）

## 【验收契约（Evaluator 输入）】

### 代码结构验证

- [ ] `api/SessionController.java` 存在，`@RestController` + `@RequestMapping("/api/sessions")`
- [ ] `SessionController` 至少 10 个 `@GetMapping/@PostMapping/@DeleteMapping` 方法
- [ ] `GameStorageController.listGames()` 改为调用 `GameRunRepository`，**不再**调用 `gameStorageService.listGames()`（grep 验证）
- [ ] `GameStorageController` 的其它方法仍然引用 `gameStorageService.{saveGame,getGame,deleteGame,getStorageStats}`
- [ ] `SessionControllerTest` 至少 7 个 `@Test`

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `mvn -pl game-agent-backend -am compile` | exit 0 |
| `mvn -pl game-agent-backend test -Dtest=SessionControllerTest` | 7 个用例全部通过 |
| `mvn -pl game-agent-backend test -Dtest=RepositorySmokeTest,SessionServiceTest,SessionControllerTest` | 全部通过，无回归 |
| 启动应用 + 跑一次 `/api/game/v2/generate` 后：`curl http://localhost:8088/api/sessions?limit=10` | 响应 `success:true`、`data` 数组非空 |
| 同上：`curl http://localhost:8088/api/sessions/{id}/messages` | 至少 2 条 message |
| 同上：`curl -X POST http://localhost:8088/api/sessions/games/{gameId}/favorite` | `favorited:true` |
| 同上：`curl http://localhost:8088/api/sessions/games/favorites` | 返回该 game |
| 同上：`curl -X POST http://localhost:8088/api/sessions/{id}/clone` | 新 sessionId 不等于原 id；调 `/api/sessions/{newId}/messages` 发现复制了 messages；调 `/api/sessions/{newId}/games` 返回空数组 |

### 数据/字段验收

- [ ] `/api/sessions` 列表的 item 字段：`id, title, modelKey, createdAt, updatedAt, messageCount, gameCount`，**无** `html` 字段
- [ ] `/api/sessions/{id}/games` 列表 item 字段：`id, sessionId, messageId, title, evalScore, iterations, favorited, createdAt`，**无** `html` 字段
- [ ] `/api/sessions/games/{id}/html` 返回的 `data.html` 是完整 HTML 字符串
- [ ] `/api/game/storage/list` 响应 shape 与改造前一致（前端 `ServerGameHistory.tsx` 不修改也能渲染）
- [ ] 时间戳：sessions/messages 接口用 `Long` 毫秒；老 `/api/game/storage/list` 仍是 ISO 字符串

### 负面用例

- [ ] `/api/sessions/{nonexistent}/messages` → 200 `{success:true,data:[],count:0}` 或 404，**不能** 500
- [ ] `/api/sessions/games/{nonexistent}/html` → 404 `{success:false,error:...}`
- [ ] `/api/sessions/games/{nonexistent}/favorite` → 404 或 200 + warning（按现有 GameStorageController 风格选其一，写到响应中明确语义）
- [ ] DELETE 不存在的 session → 404，不抛 500

### 端到端 SSOT 验证（必跑）

> 全部断言来自真实 HTTP 调用 + DB 查询，不读 controller 单测。

```bash
set -e
DB=./game-agent-backend/data/game-agent.db

# 1. 启服务（沿用 Step 3 的启动方式）
rm -f ${DB}*
cd /Users/sumo/workplace/ai/AI-GAME
mvn -pl game-agent-backend -am compile -q
mvn -pl game-agent-backend spring-boot:run -q > /tmp/aigame-step4a.log 2>&1 &
APP_PID=$!
trap "kill $APP_PID 2>/dev/null || true" EXIT
for i in $(seq 1 60); do
  curl -sf http://localhost:8088/api/game/agents > /dev/null && break
  sleep 1
done

# 2. 准备数据：跑 2 次生成创造 2 个 session
SID1=$(curl -sX POST http://localhost:8088/api/game/v2/generate \
  -H 'Content-Type: application/json' -d '{"userInput":"加法游戏 A"}' --max-time 180 | jq -r '.sessionId')
sleep 2
SID2=$(curl -sX POST http://localhost:8088/api/game/v2/generate \
  -H 'Content-Type: application/json' -d '{"userInput":"加法游戏 B"}' --max-time 180 | jq -r '.sessionId')

# 3. SSOT 断言
# 断言 1：列表返回 2 个 session，最近的在前
LIST=$(curl -s "http://localhost:8088/api/sessions?limit=10")
COUNT=$(echo "$LIST" | jq -r '.count')
[ "$COUNT" = "2" ] || { echo "FAIL: sessions count=$COUNT"; exit 1; }
FIRST=$(echo "$LIST" | jq -r '.data[0].id')
[ "$FIRST" = "$SID2" ] || { echo "FAIL: 排序错，第一是 $FIRST 期望 $SID2"; exit 1; }

# 断言 2：列表无 html 字段（避免大字段污染列表）
echo "$LIST" | jq -e '.data[0].html == null' > /dev/null \
  || { echo "FAIL: 列表含 html 字段"; exit 1; }

# 断言 3：sessions/{id}/messages 返回 ≥2 条
MSGS=$(curl -s "http://localhost:8088/api/sessions/$SID1/messages")
MSG_COUNT=$(echo "$MSGS" | jq -r '.count')
[ "$MSG_COUNT" -ge 2 ] || { echo "FAIL: messages count=$MSG_COUNT"; exit 1; }

# 断言 4：sessions/{id}/games 返回 ≥1 条且无 html
GAMES=$(curl -s "http://localhost:8088/api/sessions/$SID1/games")
GAME_COUNT=$(echo "$GAMES" | jq -r '.count')
[ "$GAME_COUNT" -ge 1 ] || { echo "FAIL: games count=$GAME_COUNT"; exit 1; }
echo "$GAMES" | jq -e '.data[0].html == null' > /dev/null || { echo "FAIL: 游戏列表含 html"; exit 1; }
GAME_ID=$(echo "$GAMES" | jq -r '.data[0].id')

# 断言 5：games/{id}/html 返回完整 html
HTML=$(curl -s "http://localhost:8088/api/sessions/games/$GAME_ID/html" | jq -r '.data.html')
echo "$HTML" | grep -qi 'doctype html' || { echo "FAIL: html 端点不返回完整 html"; exit 1; }

# 断言 6：收藏后 favorited=true，DB 真值也是 1
curl -sX POST "http://localhost:8088/api/sessions/games/$GAME_ID/favorite" > /dev/null
FAV_DB=$(sqlite3 "$DB" "SELECT favorited FROM game_runs WHERE id='$GAME_ID';")
[ "$FAV_DB" = "1" ] || { echo "FAIL: DB favorited=$FAV_DB"; exit 1; }

# 断言 7：favorites 端点能查到
FAV_LIST=$(curl -s "http://localhost:8088/api/sessions/games/favorites?limit=50")
echo "$FAV_LIST" | jq -e ".data[] | select(.id == \"$GAME_ID\")" > /dev/null \
  || { echo "FAIL: favorites 列表查不到"; exit 1; }

# 断言 8：clone 复制 messages 不复制 games
CLONE=$(curl -sX POST "http://localhost:8088/api/sessions/$SID1/clone")
NEW_SID=$(echo "$CLONE" | jq -r '.data.newSessionId')
[ "$NEW_SID" != "$SID1" ] && [ -n "$NEW_SID" ] || { echo "FAIL: clone 未生成新 session"; exit 1; }
NEW_MSG=$(sqlite3 "$DB" "SELECT count(*) FROM messages WHERE session_id='$NEW_SID';")
NEW_GAME=$(sqlite3 "$DB" "SELECT count(*) FROM game_runs WHERE session_id='$NEW_SID';")
[ "$NEW_MSG" -ge 2 ] || { echo "FAIL: clone 未复制 messages"; exit 1; }
[ "$NEW_GAME" = "0" ] || { echo "FAIL: clone 误复制了 games"; exit 1; }

# 断言 9：DELETE session 级联 messages/game_runs
curl -sX DELETE "http://localhost:8088/api/sessions/$NEW_SID" > /dev/null
GHOST_MSG=$(sqlite3 "$DB" "SELECT count(*) FROM messages WHERE session_id='$NEW_SID';")
[ "$GHOST_MSG" = "0" ] || { echo "FAIL: 级联删除 messages 未生效 ($GHOST_MSG 行)"; exit 1; }

# 断言 10：老 /api/game/storage/list 接口仍可用（响应 shape 兼容）
OLD_LIST=$(curl -s http://localhost:8088/api/game/storage/list)
echo "$OLD_LIST" | jq -e '.success == true' > /dev/null || { echo "FAIL: 老接口失效"; exit 1; }
echo "$OLD_LIST" | jq -e '.data | type == "array"' > /dev/null || { echo "FAIL: 老接口 shape 变了"; exit 1; }

# 4. 关停
kill $APP_PID; wait $APP_PID 2>/dev/null || true

echo "STEP 4a 端到端 SSOT 验证：10 条断言全部通过"
```

**通过标准**：10 条全过。前后端契约对齐看断言 1-9 的字段名与类型——4b 必须严格按这个消费。

### 剩余风险

- 列表 API 没分页，list 100 条以上性能未优化（远超当前规模）
- 时间戳格式两种（老接口 ISO、新接口 ms）短期共存——doc-refresher 在 Step 5 检查并在 conventions.md 加备注

## 与 Step 4b 的协议

前端按本 plan "API 契约"和"产出清单"段直接消费即可。任何字段争议以本文件为准。

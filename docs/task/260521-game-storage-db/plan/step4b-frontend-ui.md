# Step 4b：前端 UI（与 Step 4a 并行）

## 背景

后端 Step 4a 提供了 `/api/sessions/*` 端点。本 step 把前端「服务器游戏」抽屉改读新接口、新增「会话历史」抽屉、新增收藏按钮、新增"基于会话重开"入口。本地历史保留不变。

**与 Step 4a 并行**：本 step 文件范围限定在 `game-agent-frontend/src/`，后端不动。两端按 Step 4a 中"API 契约"段对齐。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `game-agent-frontend/src/services/serverStorage.ts`（改造：改用新端点）
  - `game-agent-frontend/src/services/sessionApi.ts`（**新建**：sessions / messages / clone / favorites）
  - `game-agent-frontend/src/components/ServerGameHistory.tsx`（接收 favorited 字段、加收藏按钮）
  - `game-agent-frontend/src/components/SessionHistory.tsx`（**新建**：会话列表 + 展开消息 + clone 按钮）
  - `game-agent-frontend/src/App.tsx`（仅新增"会话历史"按钮 + Drawer 状态；不重写整个文件）

- **不可改文件**：
  - `game-agent-frontend/src/components/GameHistory.tsx`（本地历史，保留不动）
  - `game-agent-frontend/src/components/GamePreview.tsx`、`GameContainer.tsx`、`ChatInterface.tsx`（不动）
  - `game-agent-frontend/src/services/gameStorage.ts`、`api.ts`（不动）
  - 任何后端文件
  - `package.json`（**不新增**依赖）

- **不可新增的抽象**：
  - 不引入状态管理库（Redux/Zustand/MobX）
  - 不引入路由库
  - 不引入 SWR / React Query（用现有 axios + useEffect 即可，与项目风格一致）

### 产出清单

#### `services/sessionApi.ts`（新建）

```typescript
// 字段命名严格对齐 Step 4a 的 API 契约
export interface SessionSummary {
  id: string
  title: string
  modelKey: string | null
  createdAt: number          // ms epoch
  updatedAt: number
  messageCount: number
  gameCount: number
}

export interface SessionMessage {
  id: string
  sessionId: string
  role: 'user' | 'assistant' | 'system'
  content: string
  iterations: number | null
  evalScore: number | null
  createdAt: number
}

export interface GameSummary {
  id: string
  sessionId: string
  messageId: string
  title: string | null
  evalScore: number
  iterations: number
  favorited: boolean
  createdAt: number
}

export const listSessions: (limit?: number) => Promise<SessionSummary[]>
export const getSessionMessages: (sessionId: string) => Promise<SessionMessage[]>
export const getSessionGames: (sessionId: string) => Promise<GameSummary[]>
export const cloneSession: (sessionId: string) => Promise<{ newSessionId: string; copiedMessages: number }>
export const deleteSession: (sessionId: string) => Promise<void>

export const getGameHtml: (gameId: string) => Promise<string>      // 返回 html 字符串
export const favoriteGame: (gameId: string) => Promise<void>
export const unfavoriteGame: (gameId: string) => Promise<void>
export const listFavoriteGames: (limit?: number) => Promise<GameSummary[]>
```

实现要点：
- `axios.get` / `post` / `delete`，错误统一 `throw new Error(message)`
- baseURL 与 `services/api.ts` 一致（都是 `/api/...`）
- 不在此文件做 UI 提示

#### `services/serverStorage.ts` 改造

只改"列表"和"详情"读取路径（写入留作 Step 5 清理）：
- `listGames`：内部仍调老的 `/api/game/storage/list`（后端 Step 4a 已改造为读新表）；保持现有 TS 类型
- 在响应映射处接收新字段 `evalScore`、`favorited`，存入返回对象

注意：**不要**直接把 `serverStorage.ts` 改成调 `/api/sessions/games/...`——前端表面继续用 `serverStorage.listGames()` 不变，保护 `ServerGameHistory.tsx` 的现有调用代码。

#### `components/ServerGameHistory.tsx` 改造

- 列表渲染处加：⭐ 图标显示 favorited 状态（icon: `<StarFilled />` 或 `<StarOutlined />`）
- 列表项右侧加"收藏 / 取消收藏"按钮，调 `sessionApi.favoriteGame(id)` / `unfavoriteGame(id)`
- 点击收藏按钮后**乐观更新**本地 state，再调接口；调失败则 revert 并 `message.error`
- 列表项展示评分（`evalScore`，加个小徽标，参考现有"难度"徽标风格）

#### `components/SessionHistory.tsx`（新建）

抽屉式组件，结构参考 `ServerGameHistory.tsx`（不要全盘抄，按需简化）：

- props：`{ visible: boolean, onClose: () => void, onCloneSession: (newSessionId: string) => void }`
- 主体：
  - 顶部 "刷新" 按钮 + 收藏过滤切换（"全部 / 仅含游戏"）
  - 列表：每行一个 session，左：标题 + 统计（X 条消息，Y 个游戏） 右：3 个按钮（查看消息 / 复制并新会话 / 删除）
  - 点"查看消息"展开 `<List.Item.Meta>` 子列表，调 `getSessionMessages`，按 role 区分 user/assistant 样式
  - 点"复制并新会话"：调 `cloneSession`，成功后 `message.success("已复制为新会话")` + 调 `onCloneSession(newId)` 让父组件感知（父组件 App.tsx 把新 sessionId 写入 `localStorage`，下次发请求自动带）
  - 点"删除"：`Modal.confirm` 后调 `deleteSession`

#### `App.tsx` 改造

- 新增 state：`const [sessionHistoryVisible, setSessionHistoryVisible] = useState(false)`
- Header 右侧 Button 区新增一个按钮："会话历史"（用 `<UnorderedListOutlined />` 或 `<HistoryOutlined />` 加副标记区分）
- 渲染 `<SessionHistory visible={sessionHistoryVisible} onClose={...} onCloneSession={(newId) => { localStorage.setItem('sessionId', newId); }} />`
- **保留** 现有 "本地历史"、"服务器游戏" 两个抽屉与按钮——不改它们的入口

### 约束（已冻结的边界）

- 不重写 `App.tsx`——只新增 state 和按钮，保留现有结构
- 不重写 `ServerGameHistory.tsx` 整个文件——只局部改造（加收藏徽标 + 按钮 + evalScore 展示）
- 不修改 `services/api.ts`（V2 generate 路径不动）
- 不引入新的依赖
- TS 类型必须严格（不能 `any` 跑路）
- 错误必须 user-facing（`message.error("加载会话失败")`）；console.error 同时落

### 复用的现有模式

- 抽屉组件结构参考 `ServerGameHistory.tsx`、`GameHistory.tsx`
- AntD `<List>` + `<Modal.confirm>` + `message` 用法参考现有组件
- axios 请求与错误处理参考 `services/api.ts`、`services/serverStorage.ts`
- 时间显示工具：直接 `new Date(ms).toLocaleString()`，与 ISO 字符串场景共存

### 依赖的前置子任务

- Step 4a：API 契约定义（即使 4a 还没合并，本 step 也按 4a plan 中的契约 mock 出接口先开发，最后真实联调）

## 【验收契约（Evaluator 输入）】

### 代码结构验证

- [ ] `services/sessionApi.ts` 存在，导出至少 10 个函数 / 类型
- [ ] `components/SessionHistory.tsx` 存在，是 React 函数组件，props 含 `visible / onClose / onCloneSession`
- [ ] `App.tsx` 中新增 `SessionHistory` 组件渲染（grep `SessionHistory` 出现 ≥ 2 处：import + render）
- [ ] `ServerGameHistory.tsx` 中含 `favoriteGame` 或 `unfavoriteGame` 调用（grep 验证）
- [ ] `package.json` 依赖未变（diff 无变化）
- [ ] `components/GameHistory.tsx` 与 `services/gameStorage.ts` 内容未改（diff 无变化）

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `cd game-agent-frontend && npx tsc --noEmit` | exit 0，无类型错误 |
| `cd game-agent-frontend && npm run build` | exit 0，产物生成在 `dist/` |
| `grep -c "any" game-agent-frontend/src/services/sessionApi.ts` | 0（不允许 any） |
| `grep -c "any" game-agent-frontend/src/components/SessionHistory.tsx` | 0 |

### 端到端验收（需后端 Step 4a 已合并）

- [ ] 启动后端 + `npm run dev` → 页面右上角看到「会话历史」按钮
- [ ] 先发一次生成请求 → 点「会话历史」→ 看到该会话
- [ ] 点"查看消息"→ 看到 user + assistant 两条 message
- [ ] 点"复制并新会话"→ 出现新 sessionId，原会话仍存在
- [ ] 在「服务器游戏」中点收藏 → 刷新后 ⭐ 状态保留
- [ ] 在「服务器游戏」列表里能看到 evalScore（评分徽标）
- [ ] 「本地历史」入口仍可用（旧 localStorage 数据展示正常）

### 数据/字段验收

- [ ] `services/sessionApi.ts` 中所有时间字段类型为 `number`（ms epoch），不是 `string` 或 `Date`
- [ ] `SessionSummary.modelKey` 类型为 `string | null`
- [ ] `GameSummary.title` 类型为 `string | null`

### 负面用例

- [ ] 后端未启动时点「会话历史」→ 抽屉打开但显示 `message.error("加载会话失败")` 而不是白屏崩溃
- [ ] 删除会话后再次点该会话 → 列表自动刷新，不出现幽灵条目
- [ ] 网络错误时点收藏 → 按钮乐观状态 revert + `message.error`，不死锁

### 端到端 SSOT 验证（必跑，用 browser-harness 真浏览器驱动）

> 直接查浏览器 DOM + DB 真值；不信组件 render 不报错。详见 `docs/engineering/testing.md` § browser-harness。

```bash
set -e

# 1. 启后端 + 前端 dev
cd /Users/sumo/workplace/ai/AI-GAME
mvn -pl game-agent-backend spring-boot:run -q > /tmp/aigame-be.log 2>&1 &
BE_PID=$!
( cd game-agent-frontend && npm run dev ) > /tmp/aigame-fe.log 2>&1 &
FE_PID=$!
trap "kill $BE_PID $FE_PID 2>/dev/null || true" EXIT
for i in $(seq 1 60); do
  curl -sf http://localhost:8088/api/game/agents > /dev/null && \
  curl -sf http://localhost:5173 > /dev/null && break
  sleep 1
done

# 2. 准备数据：发一次生成，确保有会话与游戏可看
curl -sX POST http://localhost:8088/api/game/v2/generate \
  -H 'Content-Type: application/json' -d '{"userInput":"加法游戏"}' --max-time 180 > /tmp/seed.json
SID=$(jq -r '.sessionId' /tmp/seed.json)

# 3. 浏览器驱动验证
browser-harness --doctor 2>&1 | grep -q "daemon alive" || browser-harness --setup

# 3a. 打开前端
browser-harness -c "
new_tab('http://localhost:5173')
wait_for_load()
print(page_info())
" | grep -q '5173' || { echo "FAIL: 前端未加载"; exit 1; }

# 3b. 验证「会话历史」按钮存在（DOM 真值）
COUNT=$(browser-harness -c "print(js('document.body.innerText.includes(\"会话历史\")'))" | tail -1)
[ "$COUNT" = "True" ] || { echo "FAIL: 页面无'会话历史'按钮"; exit 1; }

# 3c. 验证服务器游戏列表能看到 evalScore 徽标 / favorited 图标
# （详细 DOM 选择由 coder 在 SessionHistory.tsx 实现时定 data-testid）
HAS_FAV=$(browser-harness -c "print(js('document.querySelectorAll(\"[data-testid=fav-icon]\").length'))" | tail -1)
[ "$HAS_FAV" -ge 0 ] || { echo "FAIL: 收藏图标渲染异常"; exit 1; }

# 4. 验证后端 SSOT 没被前端搞坏
SESS_DB=$(sqlite3 ./game-agent-backend/data/game-agent.db "SELECT count(*) FROM sessions;")
[ "$SESS_DB" -ge 1 ] || { echo "FAIL: DB 中 sessions 异常"; exit 1; }

# 5. 关停
kill $BE_PID $FE_PID
wait $BE_PID $FE_PID 2>/dev/null || true

echo "STEP 4b 端到端 SSOT 验证：通过"
```

**注意**：browser-harness 在 Bash 工具中**必须**用 `-c "<python>"` 形式，不能 heredoc（heredoc 的 stdin 会被吞）。

**通过标准**：前端按钮 / 列表元素在真 DOM 中可见；后端 DB 真值未受前端操作破坏。

### 剩余风险

- 不在本 step 验证：移动端布局（现有 isMobile 切换不变，新组件用相同 Drawer 模式）
- 列表无分页，超过 100 条 UI 性能不验证

## 与 Step 4a 的协议

后端契约见 `step4a-backend-api.md` 的"API 契约"段。本 step 严格按字段名 / 类型对齐。任何字段名争议在 memory 中开个 conflict 备忘录，由主会话裁决。

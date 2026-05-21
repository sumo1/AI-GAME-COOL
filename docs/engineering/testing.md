# AI-GAME 测试规范

> **核心原则**：测试先行写进【验收契约】、不信中间结果、所有断言查最原始 SSOT。
> 这条规则覆盖整个项目的所有任务，是 task-designer / coder / evaluator / ci-pre-checker 的共同硬约束。

---

## 1. 三条铁律 + 一条节奏

### 1.1 测试先行（test-first）

每个子任务的【验收契约】**必须**在 plan 阶段就把端到端测试用例写完整：
- 端点 / 命令 / 期望输出 / 期望 SSOT 状态全部写死
- 不允许"等实现完后再补测试"
- 验收契约写不出可执行测试 → 任务设计有问题，task-designer 重新拆

### 1.4 验证节奏：子任务级**轻验**，任务收口**重验**（2026-05-21 新增）

端到端验证（启服务 + 真 LLM + 全链路 SSOT 断言）单次成本极高（5-10 分钟 + 几千 token），不能每个 step 都跑。规则：

| 阶段 | 谁验 | 验什么 | 不验什么 |
|------|------|--------|---------|
| **子任务级**（每个 Step 内） | coder 自验 + 独立 evaluator subagent | 单测 / 结构 grep / 字段比对 / 边界检查 | 真 LLM 调用、真启服务 + 全链路（除非该 step 不依赖 LLM 的低成本 SSOT 断言，如 schema 验证） |
| **任务收口**（最后一步 / Step 5）| 主会话亲自跑（不分给 subagent） | 一次完整端到端：清环境 → 启服务 → 真 LLM 调用 → 各 step 核心 SSOT 断言串起来 | — |

**为什么这样改**：
- 子任务级跑端到端：每个 evaluator 烧 5-10 分钟 + 几千 token，4 个 step 并行 = 20-40 分钟 + 万级 token，**绝大多数时间在等 LLM**
- 收益却低：99% 的端到端断言已被单测 + 结构 grep 覆盖
- 任务收口跑一次：覆盖跨 step 联动（4a 端点 + 4b UI + DB）的真实信号

**反模式**（不要做）：
- 在每个 step plan 的【验收契约】里写"端到端 SSOT 验证（必跑）" → 改成可选 / 任务收口必跑
- 给 subagent 派活时让它"启服务 + 跑真 LLM" → 主会话自己跑更直接

**例外**：
- step 本身就是构建端到端基础设施（如 Step 1 schema + DB），可保留低成本端到端断言（启动 + sqlite3 查 schema，无 LLM 调用）
- 任务收口阶段，端到端断言是**强制**的

### 1.2 不信中间结果

以下都**不构成"通过"的证据**：
- LLM 在文本中说"我已完成"
- coder 报告"测试通过"
- 单元测试自己 mock 自己（mock 完 service 又测 service 本身）
- AgentLoop 内置的 `evalScore ≥ 80` 自评（这是评估器对生成 HTML 的评分，不是任务完成的证据）
- IDE 显示无报错

### 1.3 查最原始 SSOT

每条断言必须穿透抽象层、直接看世界。SSOT 优先级：

| 验证目标 | SSOT（直接查） | ❌ 不算 SSOT（抽象层） |
|---------|----------------|----------------------|
| DB 数据是否写入 | `sqlite3 ... "SELECT count(*) FROM ..."` | service 方法返回的 entity / mock 仓库 |
| API 响应正确 | `curl ... \| jq` 真 HTTP 调用 | controller 单测 + MockMvc 断言 |
| 前端 UI 状态 | browser-harness/Playwright 真浏览器 + 查 DOM | React 组件单测 |
| 文件创建 | `ls -la` / `stat` / `sha256sum` | 函数返回值 |
| 进程启动 | `lsof -i:port` / `curl http://...` | 启动日志中的"Started" |
| Skill 加载 | `curl /api/skills` 列接口 + jq 验证名字 | 启动日志中的"加载 Skill"行 |
| 游戏生成 | DB 中 `game_runs` 行数 + 提取 html 验证含 `<!DOCTYPE html>` | AgentLoopResult.success() 返回值 |
| LLM 是否被调到 | `messages` 表中 assistant role 内容长度 > 0 | Spring AI 内部计数器 |

---

## 2. 验收契约的端到端测试模板

每个子任务 plan 的【验收契约】段必须包含以下**至少一节**："命令验收" + "数据/字段验收" + "负面用例"。新增一节"端到端 SSOT 验证"作为最高优先级。

### 端到端 SSOT 验证段（必填）

```markdown
### 端到端 SSOT 验证

**前置**：清干净环境（`rm -f ./game-agent-backend/data/test-*.db`），后端启在 8088。

**步骤**：
1. 启服务：`mvn -pl game-agent-backend spring-boot:run` 后台运行
2. 等就绪：`while ! curl -sf http://localhost:8088/api/game/agents > /dev/null; do sleep 1; done`（最多 60s）
3. **触发完整链路**：发真请求
   ```bash
   RESP=$(curl -sX POST http://localhost:8088/api/game/v2/generate \
     -H 'Content-Type: application/json' \
     -d '{"userInput":"做一个10以内加法游戏"}')
   SESSION_ID=$(echo "$RESP" | jq -r '.sessionId')
   ```
4. **查 SSOT 断言**（每条都要跑通）：
   ```bash
   # 断言 1：DB 中创建了 session
   sqlite3 ./game-agent-backend/data/game-agent.db \
     "SELECT count(*) FROM sessions WHERE id='$SESSION_ID';" \
     | grep -q '^1$' || { echo "FAIL: session 未写入"; exit 1; }

   # 断言 2：DB 中至少 2 条 message
   COUNT=$(sqlite3 ./game-agent-backend/data/game-agent.db \
     "SELECT count(*) FROM messages WHERE session_id='$SESSION_ID';")
   [ "$COUNT" -ge 2 ] || { echo "FAIL: messages 数 $COUNT < 2"; exit 1; }

   # 断言 3：DB 中有 game_run 且 html 非空
   HTML_LEN=$(sqlite3 ./game-agent-backend/data/game-agent.db \
     "SELECT length(html) FROM game_runs WHERE session_id='$SESSION_ID';")
   [ "$HTML_LEN" -gt 100 ] || { echo "FAIL: html 长度 $HTML_LEN"; exit 1; }

   # 断言 4：HTML 含完整文档头
   sqlite3 ./game-agent-backend/data/game-agent.db \
     "SELECT html FROM game_runs WHERE session_id='$SESSION_ID' LIMIT 1;" \
     | grep -q '<!DOCTYPE html>' || { echo "FAIL: HTML 不完整"; exit 1; }

   # 断言 5：API 列表能查到这个 session
   curl -s "http://localhost:8088/api/sessions?limit=10" \
     | jq -e ".data[] | select(.id == \"$SESSION_ID\")" > /dev/null \
     || { echo "FAIL: API 列表查不到"; exit 1; }
   ```
5. 关停：`lsof -t -i:8088 | xargs -r kill`

**通过标准**：5 条断言全过 + 关停成功。任何一条 FAIL 即整体不通过。
```

### 反模式（禁止出现在验收契约里）

```markdown
❌ "确保代码正确"            ← 不可执行
❌ "符合最佳实践"            ← 不可执行
❌ "测试覆盖率 > 80%"         ← 不查 SSOT
❌ "AgentLoopResult.success() 返回 true"   ← 信中间结果
❌ "log.info 打印了 '加载 Skill'"           ← 信中间日志
❌ "前端组件 render 不报错"                 ← 不验证用户感知
```

---

## 3. 工具速查

| 验证类型 | 命令骨架 |
|---------|---------|
| 启服务 | `mvn -pl game-agent-backend spring-boot:run &` 然后 `sleep` 或 poll |
| 等就绪 | `while ! curl -sf http://localhost:8088/...; do sleep 1; done` |
| 关服务 | `lsof -t -i:8088 \| xargs -r kill`（macOS）|
| 查 DB | `sqlite3 ./game-agent-backend/data/<file>.db "SELECT ..."` |
| 真请求 | `curl -sX POST <url> -H 'Content-Type: application/json' -d '<json>'` |
| 解析 JSON | `... \| jq -r '.field'` 或 `... \| jq -e '<filter>'`（-e 让 false/null 也返回非 0） |
| 端口探活 | `lsof -i:8088` 或 `nc -z localhost 8088 && echo ok` |
| 浏览器驱动 | `browser-harness -c "<python>"`（注意：Bash 工具中不能用 heredoc，用 `-c`）|
| 文件存在 | `[ -f path ]` / `stat path` |
| 文件指纹 | `shasum -a 256 path` |

### browser-harness 用法（端到端 UI 验证）

```bash
# 打开新 tab 跑前端
browser-harness -c "
new_tab('http://localhost:5173')
wait_for_load()
print(page_info())
"

# 模拟点击「会话历史」按钮（基于截图 + 坐标）
browser-harness -c "
capture_screenshot('/tmp/before.png')
# 读截图找按钮坐标后:
click_at_xy(x, y)
wait_for_load()
capture_screenshot('/tmp/after.png')
"

# 查 DOM 状态（验证元素存在）
browser-harness -c "
result = js('document.querySelectorAll(\".session-row\").length')
print(result)
"
```

**第一次连接**先跑 `browser-harness --doctor` 看 daemon 状态，没起就 `browser-harness --setup`。

---

## 4. 测试隔离

### DB 隔离

测试用单独的 SQLite 文件，不污染开发库：

```bash
# 单元/集成测试用
export AGENT_DB_URL="jdbc:sqlite:./game-agent-backend/data/test-game-agent.db"
# 运行前清干净
rm -f ./game-agent-backend/data/test-*.db*
```

### 端口隔离

- 开发服务：8088（后端）+ 5173（前端）
- 测试时若占用：测试脚本里先 `lsof -t -i:8088 | xargs -r kill` 再启

### Playwright 隔离

后端 GameEvaluator 用 Playwright 跑游戏评分，端到端测试也可能用 browser-harness——两者是**不同进程的不同 Chrome 实例**，互不干扰。

---

## 5. 与 agents 体系的接口

### task-designer

每个 plan 的【验收契约】必须含"端到端 SSOT 验证"段（见上）。写不出来 → 任务拆解失败，重写。

### coder

本地自验除了 `mvn test` 还要跑端到端 SSOT 验证段——**自己跑通**才能交。

### evaluator

复跑 coder 的端到端命令，断言查 DB / 文件 / DOM 的真值，与 coder 报告对比。**默认假设 coder 报告可能撒谎**（不是恶意，可能是 LLM 自欺）。

### ci-pre-checker

每次 git-push 前必须跑一遍当前任务的端到端 SSOT 验证（针对涉及代码变更的 step）。失败即拦。

### dreamer

测试中发现的"看似通过实际没测到"的反模式 → 上浮到 `docs/knowledge/pitfalls/`。

---

## 6. 已知限制

- LLM 调用受网络 + DashScope quota 限制，端到端测试可能因外部原因失败 → 区分"环境失败"（重试）vs"逻辑失败"（拦下）
- Playwright 首次跑会下载 ~120MB Chromium，CI 环境需预热
- SQLite WAL 文件在测试结束后需 `*.db-wal *.db-shm` 一起清理

参见 [[testing-principle-ssot-truth]] 记忆条目和 `docs/knowledge/principles/agent-system-philosophy.md`。

# Step 5：清理与文档

## 背景

四个 step 的代码改完后，需要：把工程规范、CLAUDE.md、README 同步到新现状；跑一次 doc-refresher；总结/清理 memory；让 verify 流程跑通端到端。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `docs/engineering/conventions.md`（新增"数据库"章节，更新章节 §11 待补章节）
  - `CLAUDE.md`（顶层）：在"内容分层"或新增"数据持久化"段落，提一句"游戏与会话存于 SQLite"
  - `game-agent-backend/CLAUDE.md`：补"基础设施 §infra/db"包说明 + DB 命令速查
  - `README.md`：在"配置说明"段落补 `AGENT_DB_URL` 默认值与"如何重置 DB"
  - `.gitignore`（如 Step 1 没加，本 step 补）
  - `docs/task/260521-game-storage-db/memory/SUMMARY.md`（dreamer 整理后产出，本 step 由人/主会话决定是否触发）
  - `docs/task/260521-game-storage-db/progress.md`（更新所有步骤勾选状态、决策记录）

- **不可改文件**：
  - 任何 `.java` / `.ts` / `.tsx`（Step 1-4 已完工，本 step 不改代码）
  - `application.yml`（Step 1 已配置）

- **不可新增的抽象**：
  - 不新建额外的文档目录
  - 不写"未来计划文档"——RAG/质量提升是另一个任务

### 产出清单

#### 1. `docs/engineering/conventions.md` 更新

新增章节 `## 12. 数据持久化`：
- SQLite + Spring JDBC 选型理由（一句话）
- `data/game-agent.db` 文件路径与备份策略
- 三表（sessions / messages / game_runs）字段速查
- "新增表的步骤"：改 `schema.sql` → 新建 Entity → 新建 Repository → 加测试
- WAL 模式与并发写约束（`synchronized` + HikariCP 单连接）

更新 `## 11. 待补章节`，把"11.x 数据持久化指南"标记为已完成（迁到 §12 / 划线）。

#### 2. `CLAUDE.md`（顶层）

在「内容分层」表格下方新增一段：
```markdown
## 数据持久化

- 游戏与会话存储在 SQLite（`./data/game-agent.db`），由 `infra/db/*` 模块管理
- 老的 `saved-games/` 文件目录已废弃但保留兼容（@Deprecated）
- 详见 `docs/engineering/conventions.md § 12`
```

#### 3. `game-agent-backend/CLAUDE.md`

在 Package Structure 段添加：
```
├── infra/
│   ├── db/             # SQLite 持久化（Step 260521 引入）
│   │   ├── SessionEntity / SessionRepository
│   │   ├── MessageEntity / MessageRepository
│   │   ├── GameRunEntity / GameRunRepository
│   │   └── DataSourceConfig
```

加 DB 命令速查段：
```bash
sqlite3 ./data/game-agent.db ".tables"
sqlite3 ./data/game-agent.db "SELECT count(*) FROM sessions;"
rm -f ./data/game-agent.db   # 重置（应用重启时会按 schema.sql 重建）
```

#### 4. `README.md`

在"环境配置"或"运行"段落加：
- 新增环境变量说明：`AGENT_DB_URL`（默认 `jdbc:sqlite:./data/game-agent.db`）
- 一节"重置数据"：删 `data/game-agent.db` + 重启

#### 5. `.gitignore`

确保以下条目存在（已有则跳过）：
```
data/
*.db
*.db-journal
*.db-shm
*.db-wal
```

#### 6. `progress.md` 更新

把 Step 1-5 的 `[ ]` 改为 `[x]`，决策记录中加最终 commit hash 列。

#### 7. Step 3 越界改动的事后登记

Step 3 改了 `application.yml` 但不在 Step 3 plan 的"可改文件"清单中（合理的必要超出，见 progress.md 决策表）。本 step 必须补做：
- 把 `AI_MODEL` 默认值（`qwen-plus → qwen3.6-plus`）同步到 `docs/engineering/conventions.md §8.2 关键变量`
- 把 `max-tokens` 改动（`4000 → 16000`）记入 conventions §12（数据持久化章节末尾的"LLM 配置"小节）或新建 §13
- 把 memory 文档 `2026-05-21-llm-max-tokens-tool-args-truncation.md` 上浮到 `docs/knowledge/pitfalls/llm-tool-args-truncation.md`（按 dreamer 流程脱敏 + 加双向链接）

### 文档检查触发

完成本 step 文档改动后：
1. 调用 `doc-refresher` agent，检查 4 个 step 期间所有代码改动 vs 现有文档的一致性
2. 调用 `dreamer` agent（仅当 `memory/` 已有 ≥ 5 条 .md），整理出 `SUMMARY.md`
3. 跑一次 `verify` skill（如 Claude Code 已配置），端到端验证生成→存储→列表→收藏→克隆全链路工作

### 约束（已冻结的边界）

- 不在本 step 重构代码
- 不在本 step 修改 schema 或 Repository 行为
- doc-refresher 报告中的"严重过时 / 误导性"项必须先修才结束本 step；轻微过时项可留作下一任务

### 复用的现有模式

- conventions.md 现有 11 章风格统一，新增 §12 沿用相同风格
- README.md 现有"环境配置"段格式参考

### 依赖的前置子任务

- Step 1 / 2 / 3 / 4a / 4b 全部 evaluator 通过

## 【验收契约（Evaluator 输入）】

### 代码结构验证

- [ ] `docs/engineering/conventions.md` 含新章节 `## 12. 数据持久化`
- [ ] `CLAUDE.md`（顶层）含"数据持久化"段
- [ ] `game-agent-backend/CLAUDE.md` 的 Package Structure 段含 `infra/db/`
- [ ] `README.md` 含 `AGENT_DB_URL` 字样
- [ ] `.gitignore` 含 `data/` 或 `*.db` 模式
- [ ] `progress.md` 5 个步骤全部 `[x]`

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `mvn -pl game-agent-backend -am compile` | exit 0（不应被本 step 影响） |
| `mvn -pl game-agent-backend test` | 所有测试通过（不应被本 step 影响） |
| `cd game-agent-frontend && npx tsc --noEmit` | exit 0 |
| `cd game-agent-frontend && npm run build` | exit 0 |
| 启动应用 + 跑端到端：generate → list sessions → list games → favorite → clone | 五步全通过 |
| 触发 `doc-refresher` 检查本任务期间所有代码改动 | 报告等级 ≤ "轻微过时" |

### 数据/字段验收

- [ ] 端到端跑完后 SQLite 中 `sessions / messages / game_runs` 三表都有数据
- [ ] 收藏一个 game_run 后 `SELECT favorited FROM game_runs WHERE id=?` 返回 1
- [ ] 克隆 session 后新 session 的 message_count = 原 session 的 message_count，game_count = 0

### 负面用例

- [ ] doc-refresher 检测到的"严重过时"项已修复（重新跑后报告清白）
- [ ] 删 `data/game-agent.db` 重启应用 → 自动重建，无报错

### 端到端 SSOT 验证（必跑，全链路回归）

> 跑一次完整链路：生成 → 列表 → 收藏 → 克隆 → 删除，确保 1-4 步未被本 step 破坏。

```bash
set -e
DB=./game-agent-backend/data/game-agent.db

# 复用 step4a 的 10 条断言全套
bash docs/task/260521-game-storage-db/plan/step4a-e2e.sh 2>&1 | tee /tmp/regression.log
grep -q "10 条断言全部通过" /tmp/regression.log || { echo "FAIL: Step4a 回归失败"; exit 1; }

# 加跑 doc-refresher
# (由 ci-pre-checker 触发，本脚本仅断言 conventions.md 已更新)
grep -q '## 12. 数据持久化' docs/engineering/conventions.md || { echo "FAIL: conventions §12 缺失"; exit 1; }
grep -q 'AGENT_DB_URL' README.md || { echo "FAIL: README 未更新"; exit 1; }
grep -q 'data/' .gitignore || grep -q '*.db' .gitignore || { echo "FAIL: .gitignore 未更新"; exit 1; }

echo "STEP 5 端到端 SSOT 验证：通过"
```

> **注意**：脚本中引用的 `step4a-e2e.sh` 由 coder 在 Step 4a 抽出为可复用脚本（plan 中"端到端 SSOT 验证"段落的内容拷贝即可）。这是为了避免内联巨长 bash。

### 剩余风险

- 老 `saved-games/` 目录的清理由用户手动决定（本任务不删）
- 后续任务（质量提升 / RAG few-shot）依赖本任务的 favorited 字段

## 后续任务（不在本任务范围）

- 任务：游戏生成质量提升（基于本任务沉淀的历史数据做评分基线）
- 任务：RAG few-shot 复用（让 LLM 检索 favorited=1 的样本作为生成参考）
- 任务：多轮对话上下文（同 sessionId 二次请求接续上次状态）
- 任务：清理 `saved-games/` 文件 + 删除 `GameStorageService` `@Deprecated` 类

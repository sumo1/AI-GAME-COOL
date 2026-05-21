# 260521-game-storage-db — 游戏存储与会话持久化（DB 化）

## 目标

把"聊天会话 / LLM 消息 / 生成游戏"三类状态从文件系统迁到 SQLite，让前端能查历史会话、重开会话、收藏优秀游戏；为后续"质量提升"任务铺好数据底座。

## 范围（本任务做什么、不做什么）

✅ 本任务做：
- 引入 SQLite + Spring JDBC，建 `sessions / messages / game_runs` 三表
- AgentLoop 入口/出口写入会话与游戏运行记录
- API 暴露：会话列表 / 会话消息 / 收藏 / 基于历史会话开新会话（"复制"）
- 前端「服务器游戏」抽屉改读 `game_runs`；新增「会话历史」抽屉读 `sessions`
- 老 `GameStorageService`（文件系统）标记 `@Deprecated` 但保留（兼容期）

❌ 本任务不做（后续任务）：
- 老 `saved-games/` 文件不导入
- 不接入 RAG / few-shot 复用机制
- 不动 AgentLoop 迭代逻辑、不动 Skill、不动评估器
- 不做多轮对话上下文（同 sessionId 的二次请求暂时还是无状态生成）
- 不做权限/认证（单用户场景）

## 步骤

1. [x] **Step 1：基础设施** — JDBC + SQLite 依赖、`schema.sql`、DataSource 配置（端到端 7/7 通过 @ 2026-05-21，commit `5d1f27a`）
2. [x] **Step 2：数据访问层** — 三个 Repository（手写 SQL + RowMapper）（mvn test 7/7 + 端到端 SSOT 通过 @ 2026-05-21，commit `cc242a1`）
3. [x] **Step 3：服务编排** — SessionService + GameChatController 改造 + 老服务 @Deprecated（mvn test 12/12 + 9/9 端到端 SSOT 通过 @ 2026-05-21）
4. [ ] **Step 4a（并行）：后端 API** — 新增 `/api/sessions/*` 端点；`/api/game/storage/list` 改读 game_runs
5. [ ] **Step 4b（并行）：前端 UI** — `ServerGameHistory` 改读新接口、新增 `SessionHistory` 抽屉、收藏按钮
6. [ ] **Step 5：清理与文档** — `conventions.md` 同步、`doc-refresher` 验证、verify 流程跑一次

## 决策记录

| 决策 | 日期 | 说明 |
|------|------|------|
| 方案 A：Spring JDBC + SQLite | 2026-05-21 | 拒绝 JPA/Flyway，理由：单人项目零运维优先；JPA 在 SQLite 上 dialect 坑多 |
| 老 saved-games 不导入 | 2026-05-21 | 从现在开始存 DB；老目录保留只读，不写迁移器 |
| 三表设计：sessions / messages / game_runs | 2026-05-21 | 仅"聊天 + 交付结果"层级，不存 WorkingMemory 迭代快照 |
| 收藏机制：`game_runs.favorited BOOLEAN` | 2026-05-21 | 后续 RAG few-shot 任务从此字段筛选样本 |
| Step 4 内部前后端可并行 | 2026-05-21 | 接口契约在 Step 4a plan 中冻结；4b 按契约消费 |
| Step 3 改 application.yml model + max-tokens | 2026-05-21 | `qwen-plus → qwen3.6-plus`、`max-tokens 4000 → 16000`。Step 3 plan 未列 application.yml 为"可改文件"——此为合理超出（必要的 LLM 配置修复，详见 memory/2026-05-21-llm-max-tokens-tool-args-truncation.md）。Step 5 需把 `AI_MODEL` 默认值同步到 `conventions.md §8.2`，把 max-tokens memory 上浮到 `docs/knowledge/pitfalls/` |

## 不变的边界（已冻结，不许在本任务里动）

- `AgentLoop.MAX_ITERATIONS / QUALITY_GATE_SCORE / MAX_LLM_RETRIES` 常量
- `AgentLoop.run()` 的迭代主循环逻辑
- `WorkingMemory.toContextXml()` 输出格式
- `AgentPrompts.SYSTEM_PROMPT`
- `@Tool` 方法签名与描述
- `SkillLoader` / `SkillDefinition` / `GameEvaluator` / `ProbeReport`

## 风险登记

- **R1**：SQLite 并发写 + Playwright 长任务可能出现 SQLITE_BUSY → 缓解：WAL 模式 + 写入在 service 层串行化（sessionId 锁粒度）
- **R2**：HTML 大字段写入 → 缓解：service 层 8MB 截断 + log.warn
- **R3**：老存储兼容期回退路径 → 缓解：老 `GameStorageService` 保留全部接口语义，只标 `@Deprecated`，不删任何方法

## 涉及人/责任

- task-designer：完成本目录与 plan
- coder：按 plan/{step}.md 的实现契约施工
- evaluator：按 plan/{step}.md 的验收契约复跑命令验收
- code-reviewer：提交前按 task-code-reviewer/code-review.md 审查
- doc-refresher：Step 5 触发文档新鲜度检查
- dreamer：阶段收尾后整理 memory

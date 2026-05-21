# 任务专项审查规则：260521-game-storage-db

> 本任务期间 code-reviewer 在工程标准之上额外检查的"已冻结边界"。
> 任务收尾后这些规则归档，不进入工程长期审查（除非 dreamer 上浮）。

## 适用范围

- 改动涉及 `infra/db/*` / `infra/storage/*` / `api/SessionController.java` / `api/GameStorageController.java` / `api/GameChatController.java`
- 改动涉及 `services/sessionApi.ts` / `components/SessionHistory.tsx` / `components/ServerGameHistory.tsx`
- 改动涉及 `schema.sql` / `application.yml` 中的 `spring.datasource` 段

不在以上范围的改动按 `docs/review/code-check.md` 工程标准审查即可。

## 已冻结的边界（命中即"高风险"）

### 后端

1. **`AgentLoop.run()` 内部不能动**——本任务只在 Controller 层接 SessionService。看到 PR 中 `AgentLoop.java` / `WorkingMemory.java` / `AgentPrompts.java` 出现非 import-only 的 diff → 拦
2. **`@Tool` 方法签名不能改**——任何 `@Tool` 方法签名 / `@ToolParam` 描述变化 → 拦
3. **老 `GameStorageService` 方法实现不能改**——只允许加 `@Deprecated` 注解和 javadoc。看到方法体 diff → 拦
4. **`GameStorageController` 的非 `listGames` 方法不能动**（兼容期）→ 拦
5. **schema.sql 必须幂等**：所有 CREATE TABLE / INDEX 必须带 `IF NOT EXISTS`；不允许 DROP / ALTER → 拦（这是首版 schema，没有迁移工具）
6. **时间字段必须 INTEGER**：所有新表的时间列必须是 `INTEGER`（毫秒 epoch），不允许 `DATETIME` / `TIMESTAMP` → 拦
7. **HikariCP 连接池上限不能改 > 1**：SQLite 单写者，改大会引入并发 bug → 拦
8. **WAL 模式不能关**：`PRAGMA journal_mode=WAL` 必须执行 → 拦
9. **`Repository.insert/update/delete` 必须 `synchronized`**：不允许通过"有 Hikari 单连接就够了"为理由去掉锁 → 警告并要求理由

### 前端

1. **不允许新增 npm 依赖**：`package.json` 的 dependencies/devDependencies 必须保持原样 → 拦
2. **`api.ts` 不能改**：V2 generate 路径不动 → 拦
3. **`GameHistory.tsx`、`gameStorage.ts` 不动**：本地历史保留 → 拦
4. **`any` 零容忍**：新建文件中 `any` 必须有 `// eslint-disable` 级别的明确说明（且要拦下来要求改成具体类型）

### 跨层

1. **API 字段命名一致性**：后端 JSON key 必须 camelCase（`messageCount` 而非 `message_count`），与现有 `/api/game/v2/generate` 风格一致 → 否则拦
2. **DB 列必须 snake_case**（SQL 习惯），但 Java Entity 字段必须 camelCase（Java 习惯），RowMapper 中显式转换 → 命中"列名 = 字段名"形式 → 拦
3. **错误响应统一**：所有新增端点的错误响应必须 `{success:false, error:"<msg>"}`，与现有 `GameStorageController` 一致 → 否则拦

## 触发"中风险"的反模式

- Repository 层抛 `RuntimeException` 而不是让 `DataAccessException` 自然冒泡（吞错风险）
- Controller 层在 `Mono.fromCallable` 外做 IO（破坏响应式）
- 测试用 `@MockBean` 替代真实 SQLite（违背"先跑再说"原则——验收要复跑真实命令）
- 控制器响应里把 `null` 字段写成空字符串（应保持 `null`，前端区分）
- 前端 `useEffect` 内 fetch 没 cancel token / abort signal（小项目不强制，但提示）

## 触发"低风险"的提醒

- `LocalDateTime` 与 `Instant` 混用（推荐统一 `Instant`）
- 测试方法名不是 `<动作>_<期望>` 格式
- SQL 字符串常量缩进风格不一致

## 审查输出模板（在工程标准基础上额外段）

```text
【任务专项检查】
- 已冻结边界违反：N 处
  - {文件}:{行}: {具体问题} — 命中规则 {N}
- 反模式：N 处
- 提醒：N 处

【与 plan 契约对齐】
- coder 的改动是否在 plan 声明的 "可改文件" 范围内？✅/❌
- 是否触碰 plan 声明的 "不可改文件"？{若 ❌ 列出}
- 是否引入 plan 声明的 "不可新增的抽象"？{若 ❌ 列出}
```

# SQLite PRAGMA 是连接级，必须每个连接重设

> 时间：2026-05-21
> 上下文：Step 1 端到端 SSOT 验证
> 来源：实施踩坑——断言 4 (foreign_keys=1) 在 sqlite3 CLI 查到 0

## 结论

SQLite 的 `PRAGMA foreign_keys` 与 `PRAGMA journal_mode` 行为**完全不同**：

| PRAGMA | 作用域 | 持久化 |
|--------|-------|--------|
| `journal_mode=WAL` | **数据库级**（写入 DB 文件元信息） | ✅ 持久 |
| `foreign_keys=ON` | **连接级**（每连接独立） | ❌ 默认每个新连接都是 OFF |

所以：在 `@PostConstruct` / `ApplicationReadyEvent` 跑一次 `PRAGMA foreign_keys=ON` **只对当前那一个连接生效**。HikariCP 后续创建/回收连接时，新连接的 FK 仍然关闭。`sqlite3` CLI 工具是新连接，查 `PRAGMA foreign_keys` 自然是 0——**这不是 bug，是设计**。

## 证据

- 应用启动日志：`SQLite foreign_keys = 1`（应用拿到的 JdbcTemplate 那一个连接的状态）
- `sqlite3 ./data/game-agent.db "PRAGMA foreign_keys;"` 输出 `0`（CLI 新连接默认状态）
- 验证：在 sqlite3 CLI 里 `PRAGMA foreign_keys=ON; PRAGMA foreign_keys;` 才会输出 1

## 解法

HikariCP 提供 `connection-init-sql`：每次创建新连接时执行该 SQL：

```yaml
spring:
  datasource:
    hikari:
      connection-init-sql: PRAGMA foreign_keys = ON
```

`journal_mode=WAL` 不需要也放进去（库级一次设置永久生效），但放进去也无害。

## 被否决的方案

### 在 jdbc URL 加 `?foreign_keys=on`

- sqlite-jdbc 3.45 支持 URL 参数（如 `jdbc:sqlite:./data/game-agent.db?foreign_keys=on`）
- 否决理由：和 Spring Boot 自动配置 `spring.datasource.url` 冲突；URL 参数语法易写错；`connection-init-sql` 更显式、更可读

### 给每个 Repository 方法加 `PRAGMA foreign_keys=ON`

- 否决理由：噪音爆炸；并不能保证后续手写脚本/管理工具的连接

## 影响范围

- application.yml `spring.datasource.hikari.connection-init-sql` 已添加
- 端到端 SSOT 验证脚本中**断言 4** 应改用"在已开 FK 的连接里测插入孤儿数据应失败"——而不是查 `PRAGMA foreign_keys`，因为 sqlite3 CLI 默认是关的。这条是验收契约本身的认知错误，需要更新 plan/step1。

## 跨任务普适性

⚠️ 这条**几乎确定要上浮到 docs/knowledge/pitfalls/**：所有"PRAGMA 是不是连接级"是 SQLite 项目的通用陷阱，下次任何 SQLite 任务都会遇到。dreamer 整理时上浮。

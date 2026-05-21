# SQLite PRAGMA 是连接级，必须每个连接重设

> 由 dreamer 从 task 260521-game-storage-db / memory/2026-05-21-sqlite-pragma-per-connection.md 上浮。
> 上浮日期：2026-05-21

## 一句话结论

SQLite 的 `PRAGMA foreign_keys` 与 `PRAGMA journal_mode` 行为**完全不同**：

| PRAGMA | 作用域 | 持久化 |
|--------|-------|--------|
| `journal_mode=WAL` | **数据库级**（写入 DB 文件元信息） | ✅ 一次设置永久生效 |
| `foreign_keys=ON` | **连接级**（每连接独立） | ❌ 默认每个新连接都是 OFF |

## 影响

任何用 SQLite + 连接池（HikariCP 等）的项目都会踩：

- 在 `@PostConstruct` / `ApplicationReadyEvent` 跑一次 `PRAGMA foreign_keys=ON` **只对当前那一个连接生效**
- HikariCP 后续创建/回收连接时，新连接的 FK 仍然关闭
- 用 `sqlite3` CLI 工具去验证 DB 时，CLI 是新连接，默认 FK 关闭——**看上去 FK 没生效，其实是查的连接没开**

## 解法

HikariCP 提供 `connection-init-sql`：每次创建新连接时执行：

```yaml
spring:
  datasource:
    hikari:
      connection-init-sql: PRAGMA foreign_keys = ON
```

`journal_mode=WAL` 不需要也放进去（库级一次设置永久生效）。

## 验证方式

不要查 `PRAGMA foreign_keys` 是否返回 1（连接相关）；改成**插入孤儿行验证 FK 真实生效**：

```sql
PRAGMA foreign_keys=ON;
INSERT INTO messages(id, session_id, role, content, created_at)
VALUES('test-orphan', 'no-such-session', 'user', 'x', 0);
-- 应当报：FOREIGN KEY constraint failed
```

如果**报错**才是 FK 真生效。如果插成功，说明这个连接的 FK 没开。

## 被否决的方案

### 在 jdbc URL 加 `?foreign_keys=on`
sqlite-jdbc 3.45+ 支持 URL 参数（`jdbc:sqlite:./data/app.db?foreign_keys=on`），但和 Spring Boot 自动配置 `spring.datasource.url` 冲突；URL 参数语法易写错。`connection-init-sql` 更显式、更可读。

### 给每个 Repository 方法加 `PRAGMA foreign_keys=ON`
噪音爆炸；并不能保证后续手写脚本/管理工具的连接。

## 来源

- 任务 `260521-game-storage-db` Step 1 端到端验证时发现
- 当时验收契约写错了"断言 4：sqlite3 CLI 查 PRAGMA foreign_keys = 1"——CLI 默认 0，断言永远失败
- 改用"插孤儿数据应失败"作为 FK 真生效的判定依据

## 相关

- `docs/engineering/conventions.md § 12.4` PRAGMA 是连接级
- 原始 task memory: `docs/task/260521-game-storage-db/memory/2026-05-21-sqlite-pragma-per-connection.md`

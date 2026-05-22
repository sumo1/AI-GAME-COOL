# AI-GAME 工程规范

> 本文档为本仓库代码层面的长期约定，**与代码同等重要**——它表达了系统"为什么这样写"。
> 修改时遵循 `agents/doc-refresher/doc-refresher.md` 的 SSOT 一致性原则：代码变了文档必须跟。

---

## 1. 仓库分层

```
AI-GAME/
├── game-agent-backend/       # Spring Boot 后端
├── game-agent-frontend/      # React + Vite 前端
├── agents/                   # Agent 流程文档（SSOT）
├── .claude/                  # Claude Code 平台配置（薄引用 → agents/）
├── docs/                     # 工程文档
│   ├── engineering/          # 工程规范（本文件所在）
│   ├── review/               # 审查标准
│   ├── knowledge/            # 跨任务知识沉淀
│   └── task/                 # 任务专项文档
└── start.sh / quick-start.sh / configure.sh   # 启动脚本
```

工程文档是**业务知识 SSOT**，与代码同 repo、同版本、同 review。禁止把核心业务知识只放在 Confluence / Notion / 私人笔记里。

---

## 2. 后端：包结构与职责边界

V2 架构下的包结构（V1 在 `legacy/` 包，@Deprecated）：

```
com.sumo.agent/
├── api/              # REST 控制器，做 DTO 包装、不写业务规则
├── agent/
│   ├── loop/         # AgentLoop / WorkingMemory / AgentPrompts —— 编排核心
│   ├── tools/        # @Tool Bean —— Spring AI Function Calling 入口
│   │   ├── skill/    #   listSkills / loadSkill
│   │   ├── generation/ # saveGame
│   │   └── evaluation/ # evaluateGame
│   ├── skill/        # SkillLoader / SkillDefinition —— SKILL.md 加载
│   └── evaluation/   # GameEvaluator / ProbeReport —— Playwright 评估
├── infra/
│   ├── model/        # ChatModelRegistry —— 多模型路由
│   ├── config/       # Jackson / RestClient 等基础配置
│   └── storage/      # GameStorageService —— 游戏存档
├── knowledge/        # VectorStore 实现（RAG）
└── legacy/           # V1 遗留，禁止新增内容（@Deprecated）
```

### 分层规则

| 层 | 该做 | 不该做 |
|----|------|--------|
| `api/` | 接 HTTP / 包 ResponseEntity / 调 Service 或 AgentLoop | 不写业务逻辑、不直接访问 RAG / Tool Context |
| `agent/loop/` | 编排迭代、维护 WorkingMemory、组合 Tool 给 ChatClient | 不直接生成 HTML（让 LLM 生成）、不直接跑 Playwright（走 GameEvaluationTool） |
| `agent/tools/` | 单一职责的 `@Tool` 方法，状态走 ToolContext | 不在 Tool 里再调 LLM、不互相 `@Autowired` 拿状态 |
| `agent/skill/` | 加载 SKILL.md、暴露 SkillDefinition | 不解释 / 不执行 SKILL.md 的内容（那是 LLM 的活） |
| `agent/evaluation/` | Playwright 渲染 + Probe 收割 + 评分计算 | 不做 LLM 调用、不修复 HTML |
| `infra/` | 基础设施，无业务规则 | 不依赖 `agent/` 包 |
| `legacy/` | 兼容保留 | 禁止新增、禁止从 V2 引用 |

---

## 3. Tool 设计规范（Spring AI Function Calling）

`@Tool` 方法是 LLM 看到的工具入口，命名和描述直接影响 LLM 决策质量。

### 3.1 命名

- 方法名用 **驼峰式动词短语**：`listSkills`、`loadSkill`、`saveGame`、`evaluateGame`
- 类名 = 方法功能 + Tool 后缀：`SkillListTool` / `GameSaveTool` / `GameEvaluationTool`
- 一个 `@Tool` 方法 = 一个独立的 Bean（避免 God Tool）

### 3.2 注解

```java
@Tool(description = "<动作 + 输入 + 输出 + 何时调用>，越具体 LLM 决策越准")
public String someAction(
    @ToolParam(description = "<参数语义和取值范围>", required = false) String param) {
    ...
}
```

- `description` 必填，写给 LLM 看，**用句号而不是冒号开头**
- `@ToolParam` 必须写 `description`；可选参数显式标 `required = false`
- 返回类型 `String`，内容是给 LLM 读的 Markdown / 结构化文本，不是给前端的 JSON

### 3.3 状态管理

- Tool 是 Spring Singleton，**禁止用字段保存 per-request 状态**
- 共享状态走 `ToolContext`（ThreadLocal 隔离）：
  - `toolContext.getWorkingMemory()` 读写当前迭代状态
  - `toolContext.getActiveSkill()` / `setActiveSkill()` 共享激活的 Skill
  - `toolContext.incrementAndGetFixCount()` 修复计数器
- AgentLoop 入口 `init()`、出口 `clear()`，禁止跳过

### 3.4 错误处理

- 输入校验失败 → 返回 `"错误：<原因>"`，不抛异常（LLM 看不懂栈）
- 内部异常 → `log.error()` + 返回包含 `[<错误类型>]` 标签的字符串（参考 `ErrorClassifier`）
- 超时类操作必须有降级路径（参考 `GameEvaluationTool.buildDegradedEvalReport`）

---

## 4. Skill 规范（AgentSkills.io）

SKILL.md 文件是给 **LLM 阅读理解** 的操作手册，不是给框架解析的配置。

### 4.1 目录结构

```
src/main/resources/skills/<skill-name>/
├── SKILL.md
└── assets/
    └── template.html        # 可选 HTML 参考模板
```

`<skill-name>` 必须小写字母 + 数字 + 连字符，如 `math-adventure`。

### 4.2 frontmatter（机器读）

```yaml
---
name: <kebab-case 标识>
description: <一句话：做什么 + 何时使用，给 LLM 用作发现>
metadata:
  ageGroup: "4-8"        # 可选，会被 listSkills 摘要展示
  gameType: quiz         # 可选
  tags: [关键词1, 关键词2] # 可选，参与关键词过滤
---
```

- `name` 和 `description` **必填**，且是 AgentSkills.io 规范的 required 字段
- 其他字段全部进 `metadata`，禁止往顶层加自定义字段
- `description` 一定要包含"何时使用"信号，否则 LLM 不会主动加载

### 4.3 body（LLM 读）

固定四段（`SkillLoader` 不解析这四段，但 LLM 会按这个结构理解）：

```markdown
# <游戏中文名>

## 何时使用
<触发关键词、年龄段、典型场景>

## 生成步骤
1. ...
2. ...

## 评估重点
- <这个游戏类型必须满足的可观察特征>

## 常见问题
- **<问题描述>** → <修复方向>
```

### 4.4 渐进式披露

- **发现阶段**（`listSkills`）：只暴露 frontmatter 摘要，节省 token
- **激活阶段**（`loadSkill`）：返回完整 body + template.html，写入 `ToolContext.activeSkill`
- 禁止在 prompt 里一次性塞所有 SKILL.md 全文

### 4.5 加新 Skill 的检查

- [ ] 目录名 = frontmatter 的 `name`
- [ ] frontmatter 有 `name + description`
- [ ] body 含 `何时使用 / 生成步骤 / 评估重点 / 常见问题` 四段
- [ ] 启动日志能看到 `加载 Skill: <name>`
- [ ] `listSkills(filter)` 用关键词能命中

**原则**：能用新增 SKILL.md 解决的需求绝不写 Java。

---

## 5. AgentLoop 与 WorkingMemory 边界

### 5.1 已冻结的常量

修改这些值需走 task-designer 评估，不许 coder 顺手调：

| 常量 | 当前值 | 含义 |
|------|--------|------|
| `AgentLoop.MAX_ITERATIONS` | 5 | 最大迭代次数 |
| `AgentLoop.QUALITY_GATE_SCORE` | 80 | 通过分数线 |
| `AgentLoop.MAX_LLM_RETRIES` | 2 | LLM 调用重试次数 |
| `AgentLoop.RETRY_BASE_DELAY_MS` | 2000 | 退避基线 |
| `WorkingMemory.HTML_SUMMARY_THRESHOLD` | 8000 | 上下文 HTML 摘要化阈值 |
| `GameEvaluationTool.EVALUATE_TIMEOUT_MS` | 30000 | Playwright 评估超时 |

### 5.2 WorkingMemory.toContextXml() 输出格式

格式被 system prompt 解析，结构变化会影响所有迭代行为。修改需同步：
- `AgentPrompts.SYSTEM_PROMPT` 中相关引用
- `docs/knowledge/principles/`（如有）中关于 working memory 的描述

### 5.3 SYSTEM_PROMPT

`AgentPrompts.SYSTEM_PROMPT` 决定编排器 LLM 的全部行为，改动属于高风险。修改前先看：
- 任务专项审查规则中是否冻结
- 是否影响所有 Skill 的预期行为

---

## 6. 评估系统规范

### 6.1 GameEvaluator 与 SKILL.md 的分工

| 评估维度 | 实现方式 | 位置 |
|---------|---------|------|
| 通用、可代码判定（白屏、JS 错误数、布局越界、点击响应） | Java 代码 | `GameEvaluator.computeScores` |
| 领域特定、模糊判断（数学题答案对不对、英语单词拼写正确） | LLM 看 SKILL.md 的"评估重点" | SKILL.md body |

**禁止**把领域逻辑硬编码到 `GameEvaluator`，否则每加一种游戏要改 Java。

### 6.2 ProbeReport 字段不轻易动

`ProbeReport` 字段被 `game-probe.js`、`GameEvaluator`、`GameEvaluationTool` 同时依赖，新增字段需：
1. 改 `game-probe.js` 采集
2. 改 `ProbeReport` Java 类
3. 改 `GameEvaluator.computeScores` 评分逻辑（可选）
4. 改 `GameEvaluationTool.buildEvalReportText` 展示

四处必须同步，缺一即错。

### 6.3 EvaluationCheck（保留但当前未挂载）

`EvaluationCheck` 函数式接口提供静态工厂方法（`htmlMustContain` / `hasFeedback` 等），用于将来扩展通用代码级检查。新增工厂方法时遵循"无副作用、纯检查"原则。

---

## 7. 错误处理

### 7.1 后端

- 用 `@Slf4j` 提供的 `log`，禁止 `System.out.println`
- 业务预期错误（用户输入不合法）→ `log.warn` + 返回友好信息
- 真实错误（外部依赖挂了 / 编程错误）→ `log.error` + 上抛或转译
- API 层捕获兜底，返回 `ResponseEntity.status(...)` 包装的统一格式：
  ```json
  {"success": false, "error": "<msg>", "message": "<user-facing>"}
  ```
- 禁止 `catch (Exception e) {}` 静默吞错
- 禁止把内部异常栈直接 expose 给前端

### 7.2 前端

- API 错误统一在 `services/api.ts` 抛 `Error(message)`
- UI 用 AntD `message` / `notification` 提示，禁止 `alert`
- 未知错误显示通用友好文案，不暴露后端 stack

---

## 8. 配置与环境

### 8.1 入口

| 文件 | 用途 |
|------|------|
| `application.yml` | Spring Boot 主配置（端口、Jackson、Spring AI、Agent、日志） |
| `application-custom.yml` | 本地覆盖（不入 git） |
| `.env` | 环境变量（`ALIYUN_API_KEY`、`AGENT_RAG_TYPE` 等） |
| `start.sh` / `quick-start.sh` | 启动脚本 |
| `configure.sh` | 交互式环境配置 |

### 8.2 关键变量

| 变量 | 默认值 | 含义 |
|------|--------|------|
| `ALIYUN_API_KEY` | 无（必填） | DashScope API Key |
| `AGENT_RAG_TYPE` | `memory` | RAG 后端：`memory` / `elasticsearch` / `embedded` |
| `AGENT_RAG_ENABLED` | `true` | 是否启用 RAG |
| `AI_MODEL` | `qwen3.6-plus` | 默认模型（任务 260521 改自 `qwen-plus`，详见 §13） |
| `SERVER_PORT` | `8088` | 后端端口 |
| `AGENT_DB_URL` | `jdbc:sqlite:./data/game-agent.db` | SQLite 持久化文件路径（任务 260521） |

新增配置项必须同步：
1. `application.yml` 默认值
2. `.env` 示例（如适用）
3. `README.md` 配置说明
4. `configure.sh` 提示文本

---

## 9. 编码与字符集

- 所有文件 **UTF-8** 编码（中文日志/注释允许且常见）
- JVM 显式启动参数：`-Dfile.encoding=UTF-8`（已在脚本中）
- Maven 编译已设置 `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>`

---

## 10. 命令速查

| 场景 | 命令 |
|------|------|
| 后端编译 | `mvn -pl game-agent-backend -am compile` |
| 后端测试 | `mvn -pl game-agent-backend test` |
| 后端启动 | `cd game-agent-backend && mvn spring-boot:run` |
| 前端类型检查 | `cd game-agent-frontend && npx tsc --noEmit` |
| 前端构建 | `cd game-agent-frontend && npm run build` |
| 前端启动 | `cd game-agent-frontend && npm run dev` |
| 全栈交互启动 | `./start.sh` |
| 全栈快速启动 | `./quick-start.sh`（内存 RAG） |

---

## 11. 测试与验收

详见 **`docs/engineering/testing.md`**——三条铁律（测试先行 / 不信中间结果 / 查最原始 SSOT），覆盖 task-designer / coder / evaluator / ci-pre-checker 的共同硬约束。

## 12. 数据持久化

任务 260521-game-storage-db 引入。选型理由：单人项目零运维优先，SQLite 单文件嵌入，零依赖；将来切 Postgres 用 Spring JDBC 迁移成本不高于 JPA。

### 12.1 三表速查

| 表 | 字段 | 用途 |
|----|------|------|
| `sessions` | id / title / model_key / created_at / updated_at / message_count / game_count | 一次会话上下文 |
| `messages` | id / session_id / role / content / iterations / eval_score / created_at | user / assistant / system 消息 |
| `game_runs` | id / session_id / message_id / title / html / eval_score / iterations / favorited / created_at | 一次成功生成的 HTML 游戏 |

时间字段统一 `INTEGER`（毫秒 epoch），不用 `DATETIME`；`favorited` 用 `INTEGER` 0/1（Repository 内部转 boolean）。

### 12.2 文件路径与备份

- DB 文件 `./data/game-agent.db`（cwd = `game-agent-backend/`，所以实际是 `game-agent-backend/data/game-agent.db`）
- WAL 模式产物：`*.db-wal`、`*.db-shm`
- 重置：`rm -f ./game-agent-backend/data/game-agent.db*`，启动时 schema.sql 自动重建
- 备份：`cp ./game-agent-backend/data/game-agent.db <backup-path>` 单文件即可
- `.gitignore` 已忽略 `data/`、`*.db`、`*.db-{journal,shm,wal}`

### 12.3 并发约束（重要）

- HikariCP `maximum-pool-size: 1`：SQLite 不允许多写者
- WAL 模式：读写并发可（写写仍互斥）
- Service 层（如 `SessionService`）写方法 `synchronized` 兜底
- Repository 写方法（insert / update / delete）一律 `synchronized`

### 12.4 PRAGMA 是连接级（隐式坑）

`PRAGMA foreign_keys = ON` **每个连接**独立设置，HikariCP 新连接默认是 OFF。  
解法：`spring.datasource.hikari.connection-init-sql: PRAGMA foreign_keys = ON`，每次拿连接执行。  
直接用 `sqlite3` CLI 查 DB 时默认 FK off——验证 FK CASCADE 时**必须**先 `PRAGMA foreign_keys=ON;` 再插孤儿数据看是否报错。

详见 `docs/knowledge/pitfalls/sqlite-pragma-per-connection.md`（待 260521 任务收口时上浮）。

### 12.5 新增表的步骤

1. 改 `src/main/resources/schema.sql`（必须 `IF NOT EXISTS` 幂等）
2. 新建 `XxxEntity.java`（POJO + `@Data`，时间用 `Instant`）
3. 新建 `XxxRepository.java`（构造器注入 `JdbcTemplate`，写方法 `synchronized`，SQL 字符串常量化）
4. 加 `RepositorySmokeTest` 用例（真启 Spring + 真 SQLite，不 mock）
5. 上层用 `@Service` 编排，不直接暴露 `JdbcTemplate`

### 12.6 list 与 detail 的字段分离

`game_runs.html` 是大字段（10KB-100KB+）。Repository 设计：
- `listBySession / listRecent / listFavorites` 等列表接口 SQL **不 SELECT html**，RowMapper 不读 html，返回 entity 的 html 字段是 null
- 详情用 `findHtmlById` 专门只查 id+html
- 调用方注意：`findHtmlById` 返回的 entity 仅 id+html 有效，其它字段是默认值

## 13. LLM 配置

### 13.1 默认模型与 max_tokens

```yaml
spring.ai.openai.chat.options:
  model: ${AI_MODEL:qwen3.6-plus}
  temperature: 0.7
  max-tokens: 16000
```

`max-tokens` 历史值是 4000，任务 260521 改到 16000——原因：DashScope OpenAI 兼容模式下 `max_tokens` **覆盖 tool_call.arguments 字符串**。当 LLM 调 `saveGame(htmlCode="<完整 HTML>")` 时，整段 HTML 序列化为 JSON 字符串后算入 `max_tokens`；超出会被静默截断。

报错信号高度误导（容易误判为 API 兼容性问题）：
- Spring AI Jackson 端：`UnexpectedEndOfInputException: was expecting closing quote for a string value`
- DashScope 服务端二级错误：`InvalidParameter: function.arguments parameter must be in JSON format`

详见 `docs/knowledge/pitfalls/llm-tool-args-truncation.md`（待 260521 任务收口时上浮）。

### 13.2 多模型路由

`infra/model/ChatModelRegistry` 提供 key 路由（`dashscope` / `kimi-k2` / `qwen3-coder-plus` / `deepseek`）。前端通过 `options.model` 字段选择，未指定则走 `@Primary` 的 DashScope。

注意：百炼 free tier 配额有限——切模型不一定能解决 `function.arguments` 问题；扩 `max-tokens` 才是治本。

## 14. 待补章节

- [ ] 14.1 RAG 与 VectorStore 实现选择指南
- [ ] 14.2 ChatModelRegistry 扩展规范（如何加新模型）
- [ ] 14.3 前端组件分层（pages / components / services）
- [ ] 14.4 Probe 脚本扩展指南
- [ ] 14.5 日志格式与可观察性（traceId、迭代上下文）
- [x] 14.6 游戏可玩性自动验证 — 见 `docs/engineering/testing.md §1.5`（任务 260521-playable-snake-evolution 完成）

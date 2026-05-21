# 任务初始决策

> 时间：2026-05-21
> 上下文：task-designer 启动阶段
> 来源：与主会话讨论 + 现有代码探索

## 结论

本任务（DB 化）落地 4 个核心决策：

1. **存储层优先**：质量提升、RAG 复用是后续任务，本任务只做基础设施 + 写入路径 + 复用入口
2. **Spring JDBC + SQLite**（方案 A），不用 JPA/Flyway/MyBatis
3. **不导入 saved-games/ 老文件**：从现在开始存 DB；老文件目录保留只读
4. **三表设计 sessions / messages / game_runs**，仅记录"聊天 + 交付结果"，不存迭代快照

## 证据

### 现状探索发现

- 当前 `pom.xml` 无任何 DB 依赖（grep `spring-boot-starter-data\|jpa\|jdbc\|postgres\|mysql\|sqlite` 无命中）
- `GameChatController` 接收 `sessionId` 但**根本没持久化**——只在控制器内 `UUID.randomUUID()` 生成后扔回响应。前端 localStorage 里那个 sessionId 后端无视
- `WorkingMemory` 完全活在 `AgentLoop.run()` 一次内
- `GameStorageService` 把游戏存成 `saved-games/<id>_<title>.html` + `.json`，并发写不安全、查询只能 listdir
- RAG `VectorStore` 三套实现都已存在但只服务于 Skill 知识库，未挂到游戏样本

### 选型理由（Spring JDBC vs JPA）

- 项目当前"零运维"风格：内存 RAG + 文件存储；JPA + Flyway 反而引入新概念负担
- JPA 在 SQLite 上 dialect 坑多（`hibernate-community-dialects` 必须显式引入），调试成本高
- "复用"需求（先能查 + 复制）不需要 ORM 关系导航，原生 SQL 反而更直接
- 将来切 Postgres 时，Spring JDBC + DAO 的迁移代价 ≈ JPA 的迁移代价，没明显劣势

## 被否决的方案（重要）

### 方案 B：JPA + Flyway

- 否决理由：抽象成本超过"DB 化"任务本身的价值，且 SQLite + JPA 历史负面案例多
- 但保留为"将来切 Postgres 后可选升级路径"

### 方案 C：先优化质量、后做 DB

- 否决理由：质量提升的反馈信号本身需要历史数据（评分基线、生成稳定性），DB 是前置依赖
- 用户原话："好的内容可以进行复用"——没有 DB，复用机制无从谈起

### MySQL / PostgreSQL（生产级方案）

- 否决理由：当前阶段属于个人/原型项目，零运维优先；引入 Docker Compose 中的 DB 服务会让 quick-start.sh 不再 quick
- 保留为"将来部署到生产时"再切

### 一次性导入老 saved-games/

- 否决理由：老数据没有 sessionId、没有 evalScore（评分系统是 V2 才有），强行导入会造成 game_runs 表里一半"幽灵记录"
- 用户决策：从零开始，老文件保留只读

## 影响范围

- 任务结构：5 个 step，前 4 个串行，Step 4 内部前后端可并行（plan 文件 4a / 4b 已写，文件范围不重叠）
- 不变的边界：AgentLoop 核心逻辑、@Tool 签名、Skill 系统、评估器全部不动
- 后续任务依赖本任务沉淀：favorited 字段是 RAG few-shot 的过滤入口；evalScore 是质量评估基线

## 仍需后续观察的不确定项

- SQLite + Playwright 长任务的并发写表现（缓解：WAL + synchronized 兜底，但实测前不敢拍胸脯）
- 单条 game_runs.html 字段的合理上限（当前缓解：service 层 8MB 截断）
- 同一 sessionId 二次请求的 UX（本任务不做多轮上下文，但前端 clone 入口已为后续铺路）

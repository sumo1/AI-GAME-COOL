# AI-GAME-COOL 升级 - 实现文档

> 本文档由 Implementer Agent 维护。
> 记录每个任务的技术实现细节、代码变更、遇到的问题和解决方案。

## 工作守则

1. 每次执行任务前，先读 PLAN.md 确认当前任务和优先级
2. 实现完成后，在此文档记录变更内容
3. 提交代码后，在 REVIEW.md 登记待 review 项
4. 遇到阻塞问题，在 PLAN.md 的"开放问题"中记录

---

## 实现记录

### [已完成] Phase 1.1 - AgentLoop 核心类

**目标**：设计并实现 AgentLoop 核心类，支持多轮 Think-Act-Observe 迭代

**实现方案**：

使用 Spring AI 的 `ChatClient` + `FunctionCallbackWrapper` 实现 Function Calling 驱动的 Agent Loop。每次请求创建新的 `WorkingMemory` 实例（无状态设计），通过 `ChatClient.builder(chatModel).defaultFunctions(callbacks)` 将工具注册到 LLM 调用中，Spring AI 自动处理 tool_calls 的内部循环。

**核心设计**：
- `AgentLoop` 是 `@Service` 单例，但每次 `run()` 调用创建新的 `WorkingMemory`
- 工具通过 `FunctionCallbackWrapper<String, String>` 包装，inputType = String.class 让 Spring AI 将 LLM 的 JSON arguments 作为原始字符串传入
- 工具执行的副作用（如 generate_game 产出的 HTML）通过闭包捕获写入 WorkingMemory
- 外层迭代循环（最多 5 轮）由 AgentLoop 控制，每轮更新系统提示词中的 WorkingMemory 上下文
- 质量门禁：evalScore >= 80 或无评估（Phase 1 暂无 evaluate_game）时视为达标

**文件变更**：
- 新增 `v2/loop/AgentLoop.java` — 核心迭代循环（@Service）
- 新增 `v2/loop/WorkingMemory.java` — 工作记忆状态（game_version / eval_score / issue_count / iteration）
- 新增 `v2/loop/AgentLoopResult.java` — 循环结果 record

---

### [已完成] Phase 1.2 - Tool 协议和注册机制

**目标**：定义工具接口、描述、结果类型，以及通过 Spring @Component 自动发现的注册中心

**实现方案**：

- `GameTool` 接口定义两个方法：`getProfile()`（工具描述）和 `execute(String input)`（执行）
- `ToolProfile` record 包含 name / description / parametersSchema（JSON Schema 格式）
- `ToolResult` record 包含 success / data / error
- `ToolRegistry` 通过 `@Autowired List<GameTool>` 自动收集所有 GameTool 组件

**文件变更**：
- 新增 `v2/tool/GameTool.java` — 工具接口
- 新增 `v2/tool/ToolProfile.java` — 工具描述 record
- 新增 `v2/tool/ToolResult.java` — 执行结果 record
- 新增 `v2/tool/ToolRegistry.java` — 注册中心（@Component，@PostConstruct 自动发现）

---

### [已完成] Phase 1.3 - generate_game 工具

**目标**：从 UniversalGameAgent 抽取游戏生成逻辑，实现为独立工具

**实现方案**：

- 输入 JSON: `{"design": "游戏设计描述", "skill_template": "可选的参考模板"}`
- 内部调用 `ChatModelRouter.get(null)` 获取默认模型，构建 System + User Prompt 后调用 LLM
- 复用 UniversalGameAgent 的 HTML 清洗逻辑（移除 markdown 代码块、补全 DOCTYPE/charset 等）
- 如果提供了 skill_template，会截断到 3000 字符后注入系统提示词作为参考

**文件变更**：
- 新增 `v2/tools/GenerateGameTool.java` — @Component 实现 GameTool 接口

---

### [已完成] Phase 1.4 - list_skills 和 load_skill 工具 + Skill 系统

**目标**：实现 Skill 加载系统，将 MathGameAgent 的模板迁移为 YAML Skill 文件

**实现方案**：

Skill 系统：
- `SkillDefinition` POJO 映射 YAML 结构（name / displayName / description / ageGroup / template / promptHint / evaluationCriteria）
- `SkillLoader` 使用 SnakeYAML 从 `classpath:skills/*.yaml` 加载所有 Skill 文件
- 支持按关键词过滤（匹配 name / description / tags）

工具实现：
- `ListSkillsTool`: 输入 `{"filter": "数学"}`，返回匹配 Skill 的摘要列表 JSON
- `LoadSkillTool`: 输入 `{"skill_name": "math_adventure"}`，返回完整 Skill 内容（Markdown 格式，含模板 HTML、生成提示、评估标准）

Skill 迁移：
- 从 `MathGameAgent` 提取完整的数学游戏 HTML 模板
- 创建 `math_adventure.yaml`，包含完整可运行的 HTML 模板、生成提示词和评估标准
- 模板改进：随机生成题目（JS 端动态生成）、鼓励性反馈、更好的响应式布局

**文件变更**：
- 新增 `v2/skill/SkillDefinition.java` — YAML 映射 POJO
- 新增 `v2/skill/SkillLoader.java` — Skill 加载器（@Component）
- 新增 `v2/tools/ListSkillsTool.java` — list_skills 工具
- 新增 `v2/tools/LoadSkillTool.java` — load_skill 工具
- 新增 `src/main/resources/skills/math_adventure.yaml` — 数学冒险 Skill

---

### [已完成] Phase 1.5 - 集成到 Controller

**目标**：新增 `/api/game/v2/generate` 端点走 AgentLoop 路径，保留旧端点不动

**实现方案**：

- 在 `GameChatController` 中注入 `AgentLoop`
- 新增 `@PostMapping("/v2/generate")` 方法
- 复用已有的 `GameRequest` / `GameResponse` 数据类
- 从 options 中提取 model key 传给 AgentLoop
- 响应结构与 v1 兼容（gameData.html / agentName / agentSource 等字段保持一致）

**文件变更**：
- 修改 `controller/GameChatController.java` — 新增 import、注入 AgentLoop、新增 v2 endpoint

---

## 已知待验证事项

1. **Spring AI API 兼容性**：`FunctionCallbackWrapper` 和 `ChatClient.builder().defaultFunctions()` 的 API 需要在有 JDK 环境时验证编译。Spring AI 1.0.0 的函数调用 API 在不同子版本可能有差异。
2. **SnakeYAML SkillDefinition 映射**：YAML 字段名使用了 camelCase（如 `displayName`），需确认 SnakeYAML 的 `Constructor` 能正确映射。如果不行，需改为 snake_case 并在 POJO 中加 `@JsonProperty`。
3. **DashScope Function Calling 支持**：qwen-plus 模型需要确认是否支持 function calling / tool use。如不支持，需切换到支持 FC 的模型（如 qwen-max）。

---

### [已完成] Phase 5 - 工程结构重构（领域驱动包结构）

**目标**：将 v2/ 无语义包名 + v1 散落代码重构为领域驱动的清晰包结构

**新包结构**：

```
com.sumo.agent/
├── Application.java
├── api/                          # REST 端点
│   ├── GameChatController.java
│   └── GameStorageController.java
├── infra/                        # 基础设施
│   ├── model/                    # LLM 模型配置
│   │   ├── ChatModelRegistry.java  (原 ChatModelRouter)
│   │   ├── DashScopeConfig.java
│   │   ├── OpenAIConfig.java
│   │   ├── DeepseekDashScopeConfig.java
│   │   ├── KimiDashScopeConfig.java
│   │   └── Qwen3CoderPlusConfig.java
│   ├── config/                   # 应用配置
│   │   ├── JacksonConfig.java
│   │   └── RestClientConfig.java
│   └── storage/                  # 存储
│       ├── GameStorageService.java
│       └── SavedGame.java
├── knowledge/                    # RAG 知识层
│   ├── VectorStore.java
│   ├── GameKnowledgeRAG.java
│   ├── InMemoryVectorStore.java
│   ├── ElasticsearchVectorStore.java
│   └── EmbeddedVectorStore.java
├── agent/                        # Agent 核心域
│   ├── loop/                     # 执行引擎
│   │   ├── AgentLoop.java
│   │   ├── AgentLoopResult.java
│   │   └── WorkingMemory.java
│   ├── tools/                    # 工具层（拆分自 GameTools）
│   │   ├── ToolContext.java
│   │   ├── skill/
│   │   │   ├── SkillListTool.java
│   │   │   └── SkillLoadTool.java
│   │   ├── generation/
│   │   │   ├── GameGenerationTool.java
│   │   │   ├── GameFixTool.java
│   │   │   ├── HtmlCleaner.java
│   │   │   └── ErrorClassifier.java
│   │   └── evaluation/
│   │       └── GameEvaluationTool.java
│   ├── skill/                    # Skill 管理
│   │   ├── SkillDefinition.java
│   │   └── SkillLoader.java
│   └── evaluation/               # 游戏评估
│       ├── GameEvaluator.java
│       └── ProbeReport.java
└── legacy/                       # v1 遗留（@Deprecated）
    ├── core/
    ├── analyzer/
    ├── games/
    └── impl/
```

**实现方案**：

分 5 步迁移，每步 `mvn compile` 通过后再进入下一步：

1. **P5.1 基础设施迁移**：config/ → infra/model/ + infra/config/；service/+model/ → infra/storage/；ChatModelRouter 重命名为 ChatModelRegistry
2. **P5.2 知识层+API层**：rag/ → knowledge/；controller/ → api/
3. **P5.3 v2 核心迁移**：v2/ → agent/（loop/skill/evaluation/tools 四个子包）
4. **P5.4 拆分 GameTools**：原 GameTools.java（5 个 @Tool 方法的大类）拆分为 5 个独立 @Component：
   - `SkillListTool` / `SkillLoadTool` — Skill 查询和加载
   - `GameGenerationTool` / `GameFixTool` — 游戏生成和修复
   - `GameEvaluationTool` — Playwright 评估
   - `ToolContext` — 共享 WorkingMemory 的桥梁（@Component 单例）
   - `HtmlCleaner` / `ErrorClassifier` — 抽取的工具类
   - AgentLoop 改为 `.tools(skillListTool, skillLoadTool, gameGenerationTool, gameFixTool, gameEvaluationTool)`
5. **P5.5 v1 归档**：core/analyzer/games/impl/ → legacy/，所有类标记 @Deprecated

**关键技术点**：

- Spring AI `tools(Object... toolObjects)` 支持传入多个对象，自动扫描所有 @Tool 方法
- ToolContext 作为 @Component 单例，AgentLoop 每次 run() 调用 `toolContext.init(memory)` 设置当前 WorkingMemory
- 向后兼容：/api/game/generate（v1）和 /api/game/v2/generate（v2）两个端点都保留
- `computeScores` 方法可见性从 package-private 提升为 public（跨包测试需要）

**验证结果**：

- `mvn compile` ✅ 通过
- `mvn test` ✅ 全部 11 个测试通过（AgentLoopIntegrationTest）

# Step 1：Skill Index 主路径优化

## 背景

当前 `AgentLoop.tryPreloadSkill` 用 Java 侧关键词 map 预加载，命中后写 `WorkingMemory.preloadedSkill`；同时 `SkillListTool.listSkills` 暴露给 LLM，让模型自己决定是否调用。两条路线并存但**互不感知**：LLM 看不到 Skill Index 全貌，只能从 `<suggested_skill>` 字段（一个名字）和工具调用结果（懒加载）拼凑。

本步骤把 Skill Index 作为**确定性运行时事实**显式注入 system prompt，避免模型忘调 `listSkills`；`loadSkill` 按需加载完整 SKILL.md 不变；`listSkills` 保留作 fallback/debug 不删除。

参考 memory：`memory/2026-05-24-skill-index-routing.md`

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/AgentLoop.java`
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/ContextRenderer.java`
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/WorkingMemory.java`
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/skill/SkillLoader.java`（如需暴露 listAllMeta() 方法）
  - 对应单元测试

- **不可改文件**：
  - `AgentPrompts.SYSTEM_PROMPT`（增量信息走 ContextRenderer 而非主提示词）
  - `agent/tools/skill/SkillListTool.java`（保留兼容）
  - `agent/tools/skill/SkillLoadTool.java`（保留兼容）
  - `SkillDefinition.java`
  - 前端 / API / pom.xml

- **不可新增的抽象**：
  - 不引入 SkillRouter / SkillIndexBuilder 单独类（直接在 AgentLoop / ContextRenderer 内实现）
  - 不引入向量检索（top-K 走纯关键词或留待后续任务）
  - 不删除 `SkillListTool`

### 产出清单

1. **SkillLoader 暴露 meta 列表**
   - 新增 public 方法：`List<SkillMeta> listAllMeta()` 返回所有 Skill 的 `name + description + metadata` 摘要（不含 body，省 token）
   - `SkillMeta` 是 record 或简单 POJO（字段：`String name`, `String description`, `Map<String, Object> metadata`）
   - 现有 `getSkill(String)` / `listAll()` 保持

2. **WorkingMemory 加 skillIndex 字段**
   - 新字段 `List<SkillMeta> skillIndex`（默认空 List，永不为 null）
   - 手写 getter/setter，沿用现有风格
   - `incrementGameVersion()` 等其它方法不动

3. **AgentLoop.run() 启动时填充**
   - `tryPreloadSkill(userInput, memory)` 之后立即：`memory.setSkillIndex(skillLoader.listAllMeta())`
   - 不改主循环结构 / MAX_ITERATIONS / QUALITY_GATE_SCORE

4. **ContextRenderer 渲染 `<skill_index>` 块**
   - 在 `<game_state>` 内、`</game_state>` 闭合**之前**追加（位置在 `<suggested_skill>` 之后即可）
   - 仅当 `skillIndex` 非空时输出
   - 格式：
     ```xml
     <skill_index>
       <skill name="snake-adventure">生成贪吃蛇互动游戏，支持键盘控制、计分、碰撞检测...</skill>
       <skill name="math-adventure">生成 4-8 岁儿童的数学加减法互动游戏...</skill>
       ...
     </skill_index>
     ```
   - **关键**：description 截断到 120 字符避免 prompt 膨胀
   - **字节级相等基线**：当 skillIndex 为空 List 时不输出此块（保 ContextRendererTest 用例 #8 不破）

5. **更新 SkillListTool 的 `@Tool description`**
   - 把语义从「列出 Skill 让你选」改为「列出 Skill 详情；通常你已经能从 system prompt 的 `<skill_index>` 看到全部摘要，本工具仅在需要 metadata 全字段或调试时使用」
   - 不改方法签名 / 返回类型

6. **单元测试 `SkillIndexInjectionTest`**（位于 `agent/skill/` 或 `agent/loop/`）
   - `SkillLoader.listAllMeta()` 返回非空 List 且每条含 name/description
   - `ContextRenderer.render(memory)` 当 `skillIndex` 设置后输出含 `<skill_index>` 与各 `<skill name="...">`
   - description 超过 120 字符时被截断
   - skillIndex 为空时不输出 `<skill_index>` 块（字节级相等保护）

7. **ContextRendererTest 增量**
   - 保留所有 13 个原用例（含字节级相等用例 #8、Step 2 用例 #9-#10、Step 3 用例 #11-#13）
   - 新增 1 用例：手动 `memory.setSkillIndex(List.of(meta1, meta2))` 后 render 含 `<skill_index>` 块

### 约束（已冻结的边界）

- `AgentPrompts.SYSTEM_PROMPT` 不动
- `AgentLoop.run(String userInput, String modelKey)` 签名不动
- `MAX_ITERATIONS = 5` / `QUALITY_GATE_SCORE = 80`
- `SkillListTool` / `SkillLoadTool` `@Tool` 方法签名不动
- 不持久化 skillIndex
- 不引入 Skill 排序 / top-K 召回（留 Step 5 候选查询任务再考虑）

### 复用的现有模式

- `SkillLoader` 在 startup 时已加载所有 Skill（`@PostConstruct` 扫描 `resources/skills/`）
- `WorkingMemory` 字段添加 + 手写 getter/setter 风格
- `ContextRenderer` 守卫条件 + 字节级相等基线

### 依赖的前置子任务

依赖 `260521-agent-harness` Step 1（ContextRenderer 已存在）。

## 【验收契约（Evaluator 输入）】

### 代码结构验证

- [ ] `SkillLoader.listAllMeta()` 存在，返回 `List<SkillMeta>`
- [ ] `WorkingMemory.skillIndex` 字段 + getter/setter 存在
- [ ] `AgentLoop.run` 启动时调 `memory.setSkillIndex(skillLoader.listAllMeta())`
- [ ] `ContextRenderer.render` 输出含 `<skill_index>` 当 skillIndex 非空
- [ ] `SkillListTool` `@Tool description` 已更新（含「通常你已经能从 system prompt 看到摘要」字样）
- [ ] `AgentPrompts.SYSTEM_PROMPT` 未改
- [ ] `MAX_ITERATIONS / QUALITY_GATE_SCORE` 未改

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `cd game-agent-backend && mvn test -Dtest=ContextRendererTest` | exit 0；14+ 用例全过 |
| `cd game-agent-backend && mvn test -Dtest=SkillIndexInjectionTest` | exit 0 |
| `cd game-agent-backend && mvn compile` | exit 0 |
| `cd game-agent-backend && mvn test` | 0 现有测试退化 |

### 端到端 SSOT 验证

1. 启动 backend
2. POST `/api/game/v2/generate` body `{"userInput":"做个简单的贪吃蛇","options":{"model":"qwen3.6-max-preview"}}`
3. 后端 DEBUG 日志能看到 system prompt 含 `<skill_index>` 块（含 snake-adventure 等 6+ 个 skill 摘要）
4. 返回成功生成，`agentName=AgentLoop v2`，前端能正常渲染

### 负面用例

- [ ] `SkillLoader` 加载 0 个 Skill（mock 空目录）时 `listAllMeta()` 返回空 List 不抛 NPE
- [ ] `ContextRenderer.render` 在 `skillIndex == null` 时不抛 NPE，不输出 `<skill_index>`
- [ ] description 含特殊字符（`<` / `>` / `&`）时不破坏 XML（可用 escape 或简单替换）

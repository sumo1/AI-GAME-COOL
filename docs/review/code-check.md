# AI-GAME 工程审查标准

> 工程维度的持续审查规则，不绑定任何具体任务。
> 适用于本仓库所有代码和文档改动。
>
> **任务专项审查**见 `docs/task/` 下对应 `task-code-reviewer/code-review.md`，由 code-reviewer agent 自动匹配。

---

## 1. 审查者角色

站在**独立视角**审查每一轮改动：

1. 改动是否符合工程规范（`docs/engineering/conventions.md`）
2. 是否引入了不必要的设计负担
3. 是否破坏了已有的模块边界和对外契约
4. 是否存在会随规模放大的风险（如 Skill 数量增多后的加载性能、迭代历史增多后的 token 爆炸）

---

## 2. 审查维度（按优先级）

### 2.1 风险分级

| 等级 | 标准 |
|------|------|
| **高** | API 契约破坏、`@Tool` 签名变更、SKILL.md frontmatter 字段废除、AgentLoop 核心常量改动、ToolContext ThreadLocal 泄漏、Playwright 资源未释放、密钥/Token 泄漏 |
| **中** | 错误处理不完整、日志级别滥用、Tool 职责混淆（在 GameSaveTool 里调 LLM 之类）、Skill 加载失败被静默吞掉、ProbeReport 字段不一致 |
| **低** | 命名、注释、日志格式、轻微文件组织 |

### 2.2 职责边界

- `api/`：只做 DTO 包装 + 调用 `AgentLoop` 或 `GameStorageService`
- `agent/loop/`：编排迭代和工作记忆，不直接生成 HTML（让 LLM 生成）
- `agent/tools/`：单一 `@Tool` 职责，状态走 `ToolContext`，**禁止**在 Tool 内调 LLM
- `agent/skill/`：加载 SKILL.md，不解释/不执行其内容
- `agent/evaluation/`：Playwright + Probe + 评分，不调 LLM、不修复 HTML
- `infra/`：基础设施，不依赖 `agent/` 包
- `legacy/`：兼容保留，禁止新增

### 2.3 设计哲学（V2）

- 能用 SKILL.md 解决就**不要写 Java**
- 通用判断放 `GameEvaluator` / `EvaluationCheck`，领域判断放 SKILL.md "评估重点"
- 渐进式披露：listSkills 只给摘要，loadSkill 才给全文
- LLM 负责理解，代码只做代码该做的事

### 2.4 Java 工程实践

- 构造器注入或 `@Autowired` 字段注入，**禁止**新增全局静态可变状态
- `@Slf4j` 提供的 `log`，**禁止** `System.out.println`
- 异常翻译：内部异常不直接抛到 API 层，`ErrorClassifier` 模式分类后转友好信息
- 资源管理：Playwright `try-with-resources`，临时文件 `finally` 删除
- 不做 drive-by refactor；契约外的"顺手优化"一律拒绝

### 2.5 TS 工程实践

- 显式类型 / 显式 schema，避免 `as any`
- `services/api.ts` 是后端契约的单点适配层，UI 组件不直接拼端点
- 错误用 `try/catch` 显式处理，不用 `alert`
- 禁止预创建空壳模块、禁止魔法字符串
- import 顺序：Node 内置 → 第三方 → 项目内部

### 2.6 资源 / 安全

- 密钥从环境变量读，不硬编码
- 临时文件落 `Files.createTempFile`，路径不拼用户输入
- HTML 注入到 Playwright 前已通过 `HtmlCleaner.clean`，不再额外信任
- Probe 注入位置（`<head>` 内）不能改，否则游戏 HTML 加载顺序受影响

### 2.7 SKILL.md 规范

- frontmatter 只能含 `name + description + metadata`
- body 必须有 `何时使用 / 生成步骤 / 评估重点 / 常见问题` 四段
- `name` = 目录名 = kebab-case
- 触发关键词应当出现在 `description` 和 "何时使用" 中（让 LLM 主动加载）

---

## 3. 固定 Checklist

### 边界

- [ ] 是否新增了静态全局状态？
- [ ] Tool 是否互相 `@Autowired` 拿状态（应走 ToolContext）？
- [ ] 是否在 `legacy/` 之外引用了 V1 的类？
- [ ] 是否引入了 `agent/` → `legacy/` 的反向依赖？

### 分层

- [ ] api/ 只做 DTO 包装？
- [ ] Tool 职责单一？没有"大杂烩 Tool"？
- [ ] Skill 是否绕过 SkillLoader 直接被某处实例化？

### 错误处理

- [ ] `catch (Exception e)` 是否真处理了，不是吞掉？
- [ ] LLM 调用是否走 `AgentLoop.callLlmWithRetry` 路径，不是裸调？
- [ ] Playwright 评估是否有超时降级（`buildDegradedEvalReport`）？

### 资源

- [ ] Playwright Browser / Context / Page 已 `close()` 或 try-with-resources？
- [ ] 临时文件已 `Files.deleteIfExists`？
- [ ] ToolContext `clear()` 在 `finally` 中调用？

### Tool / Skill

- [ ] 新增 `@Tool` 是否在 `AgentLoop.callLlmWithRetry` 的 `chatClient.tools(...)` 中注册？
- [ ] 新增 SKILL.md 加载日志是否出现？
- [ ] SKILL.md 的 `name` 与目录名一致？

### 评估系统

- [ ] 改动 ProbeReport 字段时，`game-probe.js` / `GameEvaluator.computeScores` / `GameEvaluationTool` 是否同步？
- [ ] 评分阈值（QUALITY_GATE_SCORE / 各维度满分）是否被擅自调整？
- [ ] 新增维度是否影响总分计算（当前是 5 维 × 20 分 = 100）？

### 配置

- [ ] 新增 env / yml 配置是否同步到 README.md / configure.sh / `.env` 示例？
- [ ] 默认值是否能让 `quick-start.sh` 直接跑通（不依赖外部服务）？

---

## 4. 输出格式

```text
【核心判断】
✅ 值得继续 / ⚠️ 需要先修正 / ❌ 存在严重工程问题

【风险等级】
- 高风险：
- 中风险：
- 低风险：

【审查发现】
1. [严重级别] [文件/模块] [问题]
2. ...

【边界与分层】
- 职责边界：
- 目录结构：
- Tool / Skill 规范：
- 评估系统一致性：

【建议】
1. 先修什么
2. 后修什么
3. 哪些不用过度设计
```

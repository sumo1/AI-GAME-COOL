# 260522-evaluator-oracle-shared-core — 评分器与 oracle 抽公共底层

## 目标

把 GameEvaluator（Java + Playwright，给 LLM 看的内部评分）与 oracle（bash + browser-harness，给用户/SKILL 演进看的外部判定）的**重复实现**抽成一份共享 JS 核心 `shared/playability/`，让两端都基于同一份"判定基础设施"。

抽完后两个评估器**各自做自己擅长的事**：
- GameEvaluator 在共享底层之上，加多维评分（给 LLM 渐进式反馈）
- oracle 在共享底层之上，加 PASS/FAIL 判定（给人/SKILL 演进做决策）

## 范围（本任务做什么、不做什么）

✅ 做：
- 新建 `shared/playability/` 顶层独立目录，含三个 JS 文件 + README
- 改造 `agent/evaluation/GameEvaluator.java` 引用共享库
- 改造 `scripts/lib/oracle-driver.py` 引用共享库
- 交叉验证：同一个 HTML 输入下，GameEvaluator 与 oracle 的方向一致（评分高对应 PASS，评分低对应 FAIL）
- 文档：`shared/playability/README.md` + 在 `docs/engineering/conventions.md` 加章节 + 在 `docs/engineering/testing.md §1.5` 末尾加共享库说明

❌ 不做（独立后续任务）：
- 修复 GameEvaluator 的"教育匹配度永远 15 分"问题（需要 SKILL.md frontmatter 加 evaluation_signals 字段，独立 task）
- 给 GameEvaluator 加键盘探索（虽然抽底层后这件事变容易，但独立判断要不要做）
- 替换老 game-probe.js 的全部职能（保留作 v1 兼容）
- 给 oracle 增加点击/拖拽类游戏支持
- 抽 npm 包发布

## 步骤

1. [x] **Step 1：抽 shared/playability/ 共享库** — `playability-probe.js`（138 行，window.__PLAYABILITY__ 暴露 collect / getErrors / computeWhitelist / hasNewKeyword + 防重复注入 + JS 错误 hook）+ `playability-driver.js`（96 行，window.__PLAYABILITY_DRIVER__ 暴露 findStartButton / clickByJS）+ `README.md`（接入契约 + Java/Python 示例 + 注入时机说明）。`node --check` 双绿；行为冒烟 10/10 验收（API keys / collect 返回 5 字段 / computeWhitelist null 安全 / hasNewKeyword 关键词识别 / 防重复注入 / 空 DOM null 不抛）。@ 2026-05-27
2. [ ] **Step 2：GameEvaluator 改造** — 用 `page.addInitScript` 注入共享 probe；`simulateInteractions` 增加键盘探索分支调用共享 driver
3. [ ] **Step 3：oracle 改造** — `oracle-driver.py` 把内联 JS 替换为读 shared/playability/ 文件 + 注入
4. [ ] **Step 4：交叉验证** — 4 个 fixture（snake-v0 / sample-r1-s2 / keytest / dead-page）跑两个评估器，方向一致即过
5. [ ] **Step 5：文档收尾 + push** — README + conventions/testing 段落

## 决策记录

| 决策 | 日期 | 说明 |
|------|------|------|
| 共享层用 JavaScript（非 Python/Java） | 2026-05-22 | 在浏览器内执行最自然；Java/Python 各自的胶水层薄、易维护 |
| 路径选 `shared/playability/` 顶层独立目录 | 2026-05-22 | 跨边界资源，不属于 backend 或 scripts；将来可发布 npm 包 |
| 本任务不动教育评分（仍 = 15） | 2026-05-22 | 教育评分修复需要 SKILL.md frontmatter 加 evaluation_signals 字段，是 SKILL 系统改造，不是评估器改造 |
| 一锁定到位（非加期能能子并行） | 2026-05-22 | 用户拍板。改造期间评估器不可用是可接受的（任务期内 V2 不应被生产使用） |
| 共享库严禁引用项目代码 | 2026-05-22 | 必须是纯 JS、零项目依赖。否则就不是真"共享"，是埋了耦合 |

## 验证节奏

按 `docs/engineering/testing.md §1.4`：

- Step 1：coder 自验跑通"shared 库能在浏览器加载 + 信号采集 OK"
- Step 2 / 3：coder 自验跑各自 mvn test / oracle self-test
- **Step 4 是任务收口端到端**——4 fixture 双评估器交叉对照
- Step 5：纯文档同步

## 不变的边界

- `agent/loop/*` / `agent/tools/*` / `agent/skill/*` —— 不动
- 任何 SKILL.md —— 不动
- `pom.xml` / `package.json` / `application.yml` —— 不引入新依赖
- AgentLoop.MAX_ITERATIONS / QUALITY_GATE_SCORE 等常量 —— 不动
- 五维评分公式（满分 100、各维 20）—— 不动
- oracle 退出码语义（0=PASS, 1=FAIL, 2=tooling error）—— 不动
- 老 `game-probe.js` —— 保留作向后兼容备份；本任务不删

## 风险登记

- **R1：Java 侧 page.addInitScript 的脚本加载顺序**——需要在 navigate 之前注册，否则 LLM 业务 JS 先跑、probe hook 没装上
- **R2：oracle bash 拼接共享 JS 字符串**——shell 转义可能出问题，需用 base64 中转或读文件后 `safe_js("(function(){" + js + "})()", ...)` 包装
- **R3：双评估器一致性**——交叉验证 4 个 fixture 时如发现不一致，要分清是"共享层 bug"还是"上层评分逻辑差异"。memory 必须记录每个不一致案例
- **R4：browser-harness js() 调用大字符串**——共享 JS 可能 5KB+，超过 helpers 单次调用上限风险。退路：一次性 `addInitScript` 注入而非每次 collect 重新发

## 涉及人/责任

- 主会话：管进度 + 监督 subagent + Step 4 任务收口亲自跑
- subagent (coder)：Step 1 / 2 / 3 实现
- subagent (code-reviewer)：Step 4 后审查
- doc-refresher：Step 5 触发

## 后续任务（不在本任务范围）

- `260522-evaluator-skill-signals`：SKILL.md 加 evaluation_signals → 修教育评分常数化问题
- `260522-oracle-extend-click-drag`：oracle 扩展点击 / 拖拽类游戏支持
- `260522-evaluator-keyboard-explore`：GameEvaluator 加键盘探索（共享底层完成后这件事变容易）

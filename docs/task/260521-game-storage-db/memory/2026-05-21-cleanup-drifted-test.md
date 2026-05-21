# 删除 V2 重构后失效的测试类

> 时间：2026-05-21
> 上下文：Step 1 端到端验证发现 `mvn spring-boot:run` 触发 test-compile 失败
> 来源：实施踩坑

## 结论

删除 `game-agent-backend/src/test/java/com/sumo/agent/agent/AgentLoopIntegrationTest.java`（含其空父目录）。

## 证据

该测试类引用以下符号：
- `com.sumo.agent.agent.skill.Skill` 接口（已删）
- `com.sumo.agent.agent.skill.DefaultSkill` 类（已删）
- `SkillDefinition.getEvaluationCriteria()` / `setGameType()` 方法（已删）
- `EvaluationCheck` 静态变量引用（不存在的 API 形式）
- `GameEvaluator.deriveChecksForGameType(String)` 方法（已删）

这些符号的删除发生在 `c5e0cda refactor(P8): Skill 回归知识包` 与 `b9f156 refactor: 对齐 AgentSkills.io 规范` 两次重构。当时 src 编译过了但 test 没跟上，导致 `mvn test-compile` / `mvn spring-boot:run`（在 Spring Boot 4 lifecycle 下会触发 test-compile）一起报错。

## 被否决的方案

### 标 @Disabled 保留文件

- 否决理由：`@Disabled` 不影响**编译**——文件还是会让 `mvn test-compile` 失败。要解决必须把所有引用废弃符号的代码也注释掉，工作量大且无价值（这些测试本就过期了）

### 修复测试以适配新 API

- 否决理由：测试覆盖应该跟着新 API 重新设计，而不是照原结构补。当前任务范围是"DB 化"，重写 AgentLoop 集成测试是另一个独立工作

## 影响范围

- `mvn test` 现在能干净通过（0 测试 / 0 失败）
- `mvn spring-boot:run` 不再需要 `-Dmaven.test.skip=true` workaround
- Step 1 plan 的端到端脚本已同步移除该参数
- Step 2 RepositorySmokeTest 上线后将是当前 reactor 中第一个真测试用例

## 后续任务（不在本任务范围）

- 待开任务："恢复 AgentLoop 端到端集成测试"——基于新 V2 API（Tool / Skill / WorkingMemory / GameEvaluator）重写覆盖

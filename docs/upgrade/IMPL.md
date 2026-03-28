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

### [待开始] Phase 1.1 - AgentLoop 核心类设计

**目标**：设计并实现 AgentLoop 核心类，替代当前的 GameGeneratorAgent 编排逻辑

**参考**：Agent Harness 的 `AgentLoop` 类（TypeScript）

**适配要点**：
- Agent Harness 用 TypeScript + async/await，需转换为 Java + Spring AI 的 ChatClient
- Agent Harness 用 LLM Function Calling 原生协议，需确认 Spring AI 的支持程度
- Agent Harness 有 12 个 DI port，我们先精简到核心需要的

**预计文件变更**：
- 新增：`core/AgentLoop.java` — 核心迭代循环
- 新增：`core/Tool.java` — 工具接口
- 新增：`core/ToolRegistry.java` — 工具注册中心（替代 agentRegistry）
- 新增：`core/ToolResult.java` — 工具执行结果
- 修改：`controller/GameChatController.java` — 接入新 AgentLoop

**状态**：等待 ADR-001 确认后开始

---

*（后续任务实现时在此追加记录）*

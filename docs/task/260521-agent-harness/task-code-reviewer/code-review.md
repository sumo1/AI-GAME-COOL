# Agent Harness 改造专项审查规则

## 审查目标

确认本任务没有把一个轻量教育游戏 Agent 改成过度平台化 runtime，同时确保状态、观察、上下文和控制策略真正成为一等数据。

## 必查项

### 1. 数据结构

- [ ] 运行事实先进入 Java 结构，再由 renderer 输出给 LLM
- [ ] `WorkingMemory` 没有继续膨胀成“什么都管”的类
- [ ] 新增状态字段有明确来源、更新点和渲染策略
- [ ] 没有为了 prompt 方便而引入松散 `Map<String, Object>` 满天飞

### 2. 上下文渲染

- [ ] `ContextRenderer` 负责上下文输出，业务状态对象不直接拼 prompt
- [ ] 渲染内容可裁剪，不把完整 trace / full ProbeReport 默认塞给 LLM
- [ ] 旧 XML 标签兼容性没有被破坏

### 3. 工具与观察

- [ ] 保留 Spring AI 原生 Function Calling
- [ ] 未引入 YAML/XML 自定义工具协议
- [ ] `ProbeReport` 的高信号字段被结构化保留
- [ ] 工具失败/降级路径有明确失败语义，不吞异常

### 4. 控制权

- [ ] 最大轮次和质量门禁仍由 Java 外层控制
- [ ] LLM 不能单方面绕过 `QUALITY_GATE_SCORE`
- [ ] 控制信号初期保守，不突然改变旧行为
- [ ] 连续无进展/重复问题只作为信号或明确策略，不靠 prompt 暗示

### 5. 兼容性

- [ ] `AgentLoop.run()` 签名不变
- [ ] `AgentLoopResult` 语义不变
- [ ] `@Tool` 方法签名不变
- [ ] 现有 Skill 文件不需要批量修改
- [ ] 前端不需要为本任务同步改造

## 红线

- 把 `yuntoo-smartcode` 的 SubAgent 调度、YAML/XML 协议、动态场景注册整套搬进来
- 为了“更像 harness”新增大而空的 Planner/Executor/Runtime 抽象
- 让 prompt 成为唯一事实源
- 把完整 HTML、完整 ProbeReport、完整 trace 每轮无脑塞进上下文
- 改质量阈值、最大轮次、工具签名来掩盖架构问题

## 品味判断

好改造应该让主循环更扁、更清楚：

```text
state -> render -> llm -> tool -> observation -> reduce -> control
```

如果改完之后需要更多解释才能看懂，说明抽象错了。


# memory — Agent Harness 改造决策备忘录

本目录记录实施过程中形成的任务级决策。每条记录只写真实发生的架构判断，不写流水账。

## 文件命名

```text
YYYY-MM-DD-{short-topic}.md
```

## 单条记录格式

```markdown
# {标题}

## 背景

为什么出现这个决策点。

## 决策

最终选择什么。

## 理由

为什么这么选，尤其要写清楚放弃了什么。

## 影响

影响哪些代码、文档、测试和后续任务。
```

## 已冻结的初始判断

- 本任务做领域型 harness，不做通用平台型 harness。
- `yuntoo-smartcode` 是参考样本，不是迁移目标。
- Prompt 只是状态投影，不是系统事实源。
- 原生 Spring AI Function Calling 保留，不退回 YAML/XML 自定义协议。


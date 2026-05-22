# Memory 格式约定

按天累积的决策备忘录，dreamer 整理时按格式解析。

## 文件命名

```
YYYY-MM-DD-{slug}.md
```

## 单条结构

```markdown
# 一句话标题

> 时间：YYYY-MM-DD
> 上下文：Step / 子任务 / Java 还是 oracle 侧
> 来源：讨论 / 实施踩坑 / 双评估器不一致

## 结论

## 证据

## 被否决的方案

## 影响范围
```

## 该写、不该写

✅ 该写：
- 双评估器对同一 fixture 评价不一致的案例 + 根因
- 共享 JS 注入时机踩坑（Playwright addInitScript / browser-harness js()）
- shared/playability 内部 API 契约的取舍

❌ 不该写：
- 流水账
- 已经在 conventions / testing 里规定的事

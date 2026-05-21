# Memory 格式约定

> 任务实施过程中按天累积的决策备忘录。dreamer 整理时按这个格式解析。

## 文件命名

```
YYYY-MM-DD-{slug}.md
```

slug 用 kebab-case，如 `2026-05-21-canvas-hash-strategy.md`。

## 单条 memory 的最小结构

```markdown
# {一句话标题}

> 时间：YYYY-MM-DD
> 上下文：{Step / 子任务}
> 来源：{讨论 / 实施踩坑 / oracle 假阴假阳}

## 结论

## 证据

## 被否决的方案

## 影响范围
```

## 该写、不该写

✅ 该写：oracle 假阴/假阳的真实案例 / canvas hash 与 DOM 数字的取舍 / browser-harness 的 quirk

❌ 不该写：流水账 / 已经在 conventions.md 的事

## dreamer 触发

- 任务收尾
- memory ≥ 10 条
- 用户显式要求

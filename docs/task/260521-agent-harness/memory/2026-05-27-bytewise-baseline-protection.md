# 字节级相等基线：ContextRenderer 渐进改造的核心保护

> 日期：2026-05-27
> 关联：plan/step1-state-context.md、step2-evaluation-observation.md、step3-control-trace.md

## 决策

`WorkingMemory.toContextXml()` 与 `new ContextRenderer().render(memory)` 在**默认状态**下必须**字节级相等**。

由 `ContextRendererTest` 用例 #8 强断言保护：
```java
assertEquals(memory.toContextXml(), new ContextRenderer().render(memory));
```

## 为什么这条断言这么重要

harness 改造分三 Step 渐进推进，每一 Step 都给 ContextRenderer 输出**追加**新的 XML 块（`<evaluation_observation>` / `<control_signals>` / `<run_trace_summary>`）。如果不强制保留"默认状态字节相等"基线：

- 改一处 XML 缩进就可能让 LLM prompt 漂移而不自知
- 后续 Step 加块时，`memory.toContextXml()` 这个公开 API 的语义会"暗中"变化
- LLM 行为漂移**不会被任何测试捕获**，因为评估器看不到 prompt 字符串

## 实现策略

每个新 XML 块都用条件守卫包起来，默认不输出：

| 块 | 守卫条件 |
|---|---|
| `<evaluation_observation>` | `memory.getLastEvaluationObservation() != null` |
| `<control_signals>` | `hasAnyTrueSignal(controlSignals)` —— 全 false 时不输出 |
| `<run_trace_summary>` | `runTrace.getEntries().isEmpty() == false` |

这样默认状态（new WorkingMemory()）下三个守卫都为 false，输出 = Step 1 输出。

## 适用范围

未来任何对 `ContextRenderer` 的改动都必须保持这条基线，除非显式废弃用例 #8（需要任务级决策，不是 coder 个人判断）。

## 反模式

- 把守卫去掉「让默认输出更结构化」 → ✗ 破坏字节级相等
- 在新块里追加默认值（如 `<run_trace_summary>(empty)</run_trace_summary>`）→ ✗ 同上
- 改 XML 缩进或换行符以"统一风格" → ✗ 同上

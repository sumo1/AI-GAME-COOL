# messages.content 存 raw LLM 错误响应导致客户端 JSON 解析挂

> 时间：2026-05-21
> 上下文：Step 5 轻量端到端验证
> 来源：实施踩坑（DashScope free tier 耗尽，多次失败请求触发）

## 结论

`SessionService.safeError(result)` 把 `AgentLoopResult.error()` 的**原始字符串**（含 LLM API 返回的 raw HTTP body，里面有 `\"`、`{`、`\\` 等转义字符）整段塞进 `messages.content`。

之后通过 `/api/sessions/{id}/messages` 端点返回时，Spring Jackson 给整个响应再做一层 JSON 转义——导致原本的 `\"use free tier only\"` 在响应里变成 `\\\"use free tier only\\\"`，**Python 的严格 JSON parser 解析失败**（虽然 javascript 端能处理）。

## 证据

DB 中 assistant message content 实际内容：
```
[失败] AgentLoop 执行失败: LLM 调用失败（不可恢复）: 403 - {"error":{"message":"The free tier of the model has been exhausted. If you wish to continue access the model on a paid basis, please disable the \"use free tier only\" mode in the management console.",...
```

API 响应里这段被序列化成：
```
"content": "[失败] AgentLoop 执行失败: LLM 调用失败（不可恢复）: 403 - {\"error\":{\"message\":\"The free tier of the model has been exhausted. If you wish to continue access the model on a paid basis, please disable the \\\"use free tier only\\\" mode in the management console.",
```

`\\\"` 在 strict JSON 里不合法（应该是 `\\"` 或 `\"`，但出现了三反斜+引号 = 内层错误转义），导致 Python json 解析器报 `Expecting ',' delimiter`。JavaScript 浏览器端宽容、能解析。

## 解法（不在本任务做）

应该在 `SessionService.safeError` 里**清洗**原始错误：
1. 截断（>500 字符的错误明显异常）
2. 提取关键信号（`status code` + 第一行错误描述）
3. 不要把 LLM API 的 raw response 整段塞进 message.content

或者：`messages.content` 在写入时就不该含未经处理的外部 API raw body。

## 影响范围

- **本任务功能未坏**：API 响应字段都对（success/count/roles），只是 content 字符串里含转义异常
- 浏览器端正常工作（前端 axios 解析没问题）
- Python / 严格 JSON 解析器（curl + jq、自动化测试脚本）会挂——影响**未来端到端验证**

## 后续任务（不在本任务范围）

待开任务："优化 SessionService 错误信息清洗"
- SessionService.safeError 限制 content 长度 + 提取关键字段
- 测试用例：故意构造长 / 含特殊字符的 error，验证 content 清洁
- 不影响现有功能，是纯防御性改进

## 跨任务普适性

⚠️ 应当上浮到 `docs/knowledge/pitfalls/` 作为"外部 API raw body 不应直接持久化"类原则的具体案例。

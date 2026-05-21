# LLM max_tokens 不够导致 tool_call.arguments JSON 被截断

> ⬆️ 已上浮到 [docs/knowledge/pitfalls/llm-tool-args-truncation.md](../../../knowledge/pitfalls/llm-tool-args-truncation.md)（dreamer @ 2026-05-21）

> 时间：2026-05-21
> 上下文：Step 3 端到端验证一直生成失败
> 来源：实施踩坑（误以为是 quota / API 兼容性问题）

## 结论

DashScope OpenAI 兼容模式下，**max_tokens 限制涵盖 tool_call 的 arguments 字段**。当 LLM 调用 `saveGame(htmlCode="<完整 HTML>")` 这类大 string 参数的 tool 时，arguments 序列化后的 JSON 字符串会被算入 max_tokens；超出会被静默截断。

Spring AI 拿到截断后的 arguments JSON，Jackson 解析时抛：

```
tools.jackson.core.exc.UnexpectedEndOfInputException:
  Unexpected end-of-input: was expecting closing quote for a string value
  (through reference chain: java.util.LinkedHashMap["htmlCode"])
```

被 `AgentLoop.callLlmWithRetry` 包成"LLM 调用失败（不可恢复）"，但底层 HTTP 状态码是 200——所以错误堆栈里看到的 "400 InvalidParameter ... function.arguments parameter must be JSON" 是 DashScope 在**第二次往返**（LLM 把不完整 arguments 送回服务器后）报的，**不是首次请求的失败**。**根因仍然是 max_tokens 不够。**

## 证据

- application.yml `spring.ai.openai.chat.options.max-tokens: 4000` 时，4 次模型（qwen-plus / kimi-k2 / qwen3-coder-plus / deepseek）全部失败
- 改成 `max-tokens: 16000` 后 `qwen3.6-plus` 一次成功（HTML 17862 字符 / DB game_run 行 html 长度 15697）
- 错误 stacktrace 在 `MethodToolCallback.extractToolArguments` → `Jackson UntypedObjectDeserializerNR.deserialize` → `_finishString` 出现 EOF
- "InvalidParameter ... function.arguments must be JSON" 是 LLM 把已截断的 arguments 回送给 DashScope 后才报的二级错误

## 解法

```yaml
spring:
  ai:
    openai:
      chat:
        options:
          # 注释说明：4000 不够给 saveGame 的大 htmlCode 参数留空间
          max-tokens: 16000
```

后续若 HTML 超过 16000 token，应当：
1. 在 SKILL.md 中限制 HTML 行数 / 模板复杂度
2. 或拆分多个工具调用（saveHtmlHead / saveHtmlBody / ...）
3. 或换用流式 tool args（Spring AI 当前不一定支持）

## 被否决的方案

### "切换模型"
- 否决理由：4 个模型同样错。问题不在模型，在配额参数

### "切流式响应"
- 否决理由：流式有它自己的兼容性问题；先用最简的方法过 step3

### "改 SKILL.md 让 LLM 输出更短的 HTML"
- 否决理由：治标不治本；这个限制对所有大 tool args 都生效；先解决根本

## 跨任务普适性

⚠️ 这条**应当上浮到 docs/knowledge/pitfalls/**：
- 任何用 Spring AI Function Calling + 大 string 参数的项目都会遇到
- 错误信号高度误导（"InvalidParameter / function.arguments must be JSON"）让人误以为是 API 兼容性问题
- 真凶是 max_tokens 把 arguments 流截断
- dreamer 整理时上浮（与 [[2026-05-21-sqlite-pragma-per-connection.md]] 一起作为 "外部依赖隐式约束" 类条目）

## 影响范围

- application.yml `max-tokens: 4000 → 16000`
- 默认模型从 `qwen-plus` 改为 `qwen3.6-plus`（用户提供，百炼上可用）
- Step 3 的 9 条端到端断言全部通过
- 这条修复同时解决了 Step 5 plan 中提到的"LLM 配额恢复后再补完整端到端"——已不再需要那一步

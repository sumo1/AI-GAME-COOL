# LLM max_tokens 不够导致 tool_call.arguments JSON 被截断

> 由 dreamer 从 task 260521-game-storage-db / memory/2026-05-21-llm-max-tokens-tool-args-truncation.md 上浮。
> 上浮日期：2026-05-21

## 一句话结论

DashScope OpenAI 兼容模式（其它兼容 OpenAI Function Calling 协议的 LLM 服务也极可能）下，**`max_tokens` 限制涵盖 tool_call 的 arguments 字段**。当 LLM 调用一个含**大 string 参数**的 tool（如 `saveGame(htmlCode="<完整 HTML>")`）时，arguments 序列化后的 JSON 字符串会被算入 `max_tokens`；超出会被静默截断。

下游错误信号高度误导，容易误判。

## 误导信号链

报错**层级 1**：Spring AI Jackson 解析 tool_call.arguments 时
```
tools.jackson.core.exc.UnexpectedEndOfInputException:
  Unexpected end-of-input: was expecting closing quote for a string value
  (through reference chain: java.util.LinkedHashMap["htmlCode"])
```
被 `AgentLoop.callLlmWithRetry` 包成 "LLM 调用失败（不可恢复）"。

报错**层级 2**（如果在某些路径下走第二次往返）：DashScope 服务端
```
400 InternalError.Algo.InvalidParameter:
  The "function.arguments" parameter of the code model must be in JSON format
```

误判路径：看到层级 2 的"InvalidParameter / function.arguments must be JSON"，会误以为是 API 兼容性问题、模型不支持 function calling、或参数格式错——实际**根因是 max_tokens 不够**。

## 解法

调高 `max-tokens`：

```yaml
spring.ai.openai.chat.options:
  max-tokens: 16000   # 4000 不够给 saveGame 大 string 参数
```

如果 HTML 真的会超过 16000 token：
1. **Skill 改造**：在 `SKILL.md` 里限制 HTML 行数 / 模板复杂度
2. **拆 tool**：把 `saveGame(html)` 拆成 `saveHead / saveBody / saveScript` 多次调用（每次 args 短）
3. **流式 tool args**：Spring AI 当前版本支持有限，不推荐先用

## 被否决的方案

### "切换模型"
DashScope 上 4 个模型（qwen-plus / kimi-k2 / qwen3-coder-plus / deepseek）都报同一个错——问题不在模型。

### "切流式响应"
有自己的兼容性问题；先用最简的扩 max_tokens 解决根本。

### "改 SKILL.md 让 LLM 输出更短的 HTML"
治标不治本；这个限制对所有大 tool args 都生效。

## 验证 max_tokens 是否真的吃紧

跑一次大参数 tool 调用，看 `usage.completion_tokens` 是否接近 `max_tokens` 上限。如果接近就要扩。

## 来源

- 任务 `260521-game-storage-db` Step 3 端到端阶段全部生成请求失败
- 4 个模型轮换均报同一错（误以为是 quota 问题）
- 调高 max-tokens 后单次成功，HTML 长度 ~15-17k 字符

## 相关

- `docs/engineering/conventions.md § 13.1` LLM 配置
- 原始 task memory: `docs/task/260521-game-storage-db/memory/2026-05-21-llm-max-tokens-tool-args-truncation.md`

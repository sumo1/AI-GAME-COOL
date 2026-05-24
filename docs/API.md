# AI-GAME-COOL v2 API 文档

> 基于 AgentLoop 的儿童教育游戏生成 API

## 基础信息

- **Base URL**: `http://localhost:8080/api/game`
- **Content-Type**: `application/json`
- **编码**: UTF-8

---

## 端点列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/game/v2/generate` | **v2 游戏生成**（AgentLoop 多轮迭代）；`options.model="mock-fixture"` 时旁路 LLM 直接返回 fixture |
| POST | `/api/game/generate` | v1 游戏生成（传统 Agent） |
| GET | `/api/game/generate/stream` | SSE 流式生成（v1） |
| GET | `/api/game/agents` | 获取已注册 Agent 列表 |
| POST | `/api/game/storage/save` | 用户主动保存游戏（写入 `game_runs`，session=`manual-saves`） |
| GET | `/api/game/storage/list` | 列出保存的游戏（不含 html，多 `sessionId` 字段） |
| GET | `/api/game/storage/{gameId}` | 获取游戏详情（含 html） |
| DELETE | `/api/game/storage/{gameId}` | 删除单个游戏 |
| DELETE | `/api/game/storage/batch` | 批量删除 |
| GET | `/api/game/storage/stats` | 总数 + 总 HTML 字节数 |

---

## POST /api/game/v2/generate

基于 AgentLoop 的智能游戏生成。自动查找 Skill 模板、生成 HTML5 游戏、评估质量、迭代修复，直到评分达标（≥80）或达到最大迭代次数（5）。

### 请求

```json
{
  "userInput": "帮我生成一个适合6岁小朋友的数学加减法游戏",
  "sessionId": "可选，不传则自动生成 UUID",
  "options": {
    "model": "可选的模型路由 key"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userInput` | string | ✅ | 用户的自然语言游戏需求描述 |
| `sessionId` | string | ❌ | 会话 ID，用于关联请求 |
| `options` | object | ❌ | 扩展选项 |
| `options.model` | string | ❌ | 指定 AI 模型 key（可选 `qwen3.6-max-preview` / `qwen3.7-max` / `kimi-k2.6` / `MiniMax-M2.5` / `deepseek-v4-pro`，或 `mock-fixture` 旁路 LLM 返回 classpath fixture，详见 `docs/engineering/conventions.md §13.2`） |

### 响应

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "success": true,
  "message": "生成了一个数学冒险游戏，适合4-8岁...",
  "gameData": {
    "html": "<!DOCTYPE html>...",
    "type": "agent_loop",
    "generatedByLLM": true,
    "gameData": {
      "title": "AI 生成的游戏",
      "description": "用户输入的原始描述",
      "generated": true,
      "iterations": 2,
      "evalScore": 85
    }
  },
  "agentName": "AgentLoop v2",
  "agentSource": "llm",
  "generatedByLLM": true,
  "error": null
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 是否成功 |
| `gameData.html` | string | 完整的 HTML5 游戏代码 |
| `gameData.gameData.iterations` | int | 迭代次数 |
| `gameData.gameData.evalScore` | int | 最终评估得分（0-100） |
| `message` | string | LLM 的总结反馈 |
| `error` | string | 失败时的错误信息 |

### 失败响应

```json
{
  "sessionId": "...",
  "success": false,
  "message": "游戏生成失败: AgentLoop 执行失败: LLM 调用失败",
  "error": "AgentLoop 执行失败: LLM 调用失败...",
  "gameData": null
}
```

---

## POST /api/game/generate

v1 传统 Agent 生成（基于 GameGeneratorAgent 意图分析 + 模板选择）。

### 请求

```json
{
  "userInput": "我想要一个记忆翻牌游戏",
  "sessionId": "可选",
  "options": {
    "gameType": "MEMORY",
    "difficulty": "EASY"
  }
}
```

### 响应

结构同 v2，但 `agentSource` 可能是 `"system"`（模板生成）或 `"llm"`（AI 生成）。

---

## GET /api/game/generate/stream

SSE 流式生成，返回逐步进度事件。

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userInput` | string | ✅ | 游戏需求描述 |
| `sessionId` | string | ❌ | 会话 ID |

### SSE 事件格式

```
data: {"sessionId":"...","type":"analyzing","message":"正在分析您的需求..."}
data: {"sessionId":"...","type":"configuring","message":"正在配置游戏参数..."}
data: {"sessionId":"...","type":"generating","message":"正在生成游戏内容..."}
data: {"sessionId":"...","type":"rendering","message":"正在渲染游戏界面..."}
data: {"sessionId":"...","type":"completed","message":"游戏生成完成！","data":{"html":"..."}}
```

---

## GET /api/game/agents

获取系统中已注册的 Agent 列表。

### 响应

```json
{
  "agents": [
    {
      "name": "数学游戏Agent",
      "description": "生成数学类教育游戏"
    }
  ],
  "total": 5
}
```

---

## AgentLoop 内部工具链

v2 API 内部通过 Spring AI Function Calling 调用以下工具：

| 工具 | 说明 |
|------|------|
| `listSkills(filter)` | 列出可用的游戏 Skill 模板 |
| `loadSkill(skillName)` | 加载指定 Skill 的完整定义 |
| `generateGame(gameDesign)` | 根据设计方案生成 HTML5 游戏 |
| `evaluateGame(htmlCode)` | Playwright headless 评估游戏质量 |
| `fixGame(issueDescription)` | 根据问题列表修复游戏 |

### 可用 Skill 列表

| Skill 名称 | 显示名 | 适合年龄 | 说明 |
|------------|--------|---------|------|
| `math_adventure` | 数学冒险 | 4-8岁 | 10以内加减法互动游戏 |
| `memory_master` | 记忆大师 | 4-10岁 | 卡片配对翻牌记忆游戏 |
| `english_explorer` | 英语探险家 | 5-9岁 | 英语词汇学习游戏 |
| `traffic_safety` | 交通安全小卫士 | 5-9岁 | 交通安全教育游戏 |
| `shape_colors` | 形状颜色大冒险 | 4-6岁 | 图形识别与颜色分类 |
| `logic_puzzle` | 逻辑推理大师 | 8-12岁 | 逻辑推理游戏 |

---

## 错误码说明

| 错误类型 | 说明 |
|---------|------|
| 网络超时 | LLM API 调用超时，会自动重试 2 次 |
| 网络连接异常 | 无法连接到 LLM 服务 |
| API 限流 | 请求频率过高，429 |
| 认证失败 | API Key 无效或过期 |
| 服务端错误 | LLM 服务 5xx |
| 业务逻辑错误 | 其他错误 |

---

*最后更新：2026-03-29*

# Step 4：运行时写入 Evidence

## 背景

Step 3 建好了表，本步骤把 AgentLoop 跑完一次后的事实写进去。**关键约束**：不改 `AgentLoop.run()` 签名、不改任何 `@Tool` 方法签名；写入逻辑放在外层（`SessionService` 层 / 或新建 `EvidenceService`），由 controller 调用。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `game-agent-backend/src/main/java/com/sumo/agent/infra/storage/SessionService.java`（新增 `recordEvidence` 或扩展 `recordRun`）
  - `game-agent-backend/src/main/java/com/sumo/agent/infra/storage/EvidenceMapper.java`（**新建**，纯静态方法把 EvaluationObservation/RunTrace 转 JSON 字符串）
  - `game-agent-backend/src/main/java/com/sumo/agent/api/GameChatController.java`（在 `recordRun` 之后调用 `recordEvidence`）
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/AgentLoop.java`（**仅暴露** `WorkingMemory` 给外层，**不改** `run()` 签名）
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/loop/AgentLoopResult.java`（新增字段，**保持向后兼容**：`AgentLoopResult.success(html, msg, iter, score)` 工厂仍可用）
  - 对应单元/集成测试

- **不可改文件**：
  - `AgentLoop.run(String, String)` 方法签名
  - 任何 `@Tool` 方法签名
  - `agent/evaluation/*` 与 `agent/loop/Working*/Context*/Control*/RunTrace*`（Step 1-3 已冻结）
  - 前端 / API 响应结构（GameResponse 不动）
  - `SkillLoader.java`（Step 1 已冻结）

- **不可新增的抽象**：
  - 不引入新的 Repository（用 Step 3 的两个）
  - 不引入异步写入 / 消息队列
  - 不引入完整 event sourcing

### 产出清单

#### 1. `AgentLoopResult` 暴露 evidence payload

新增字段（保持 record 不变；如已是 record，新建一个 `AgentLoopResult.WithEvidence` 包装；**或**直接给 record 加可选字段并提供向后兼容静态工厂）：

```java
public record AgentLoopResult(
    boolean success,
    String gameHtml,
    String llmMessage,
    int iterations,
    int evalScore,
    String error,
    EvaluationObservation lastEvaluationObservation,  // 可空
    RunTrace runTrace,                                // 可空
    String activeSkillName,                           // 可空
    String errorType                                  // 可空，失败时 ErrorClassifier 分类
) {
    // 保留旧 success/failure 工厂保持向后兼容（其它字段填 null）
    public static AgentLoopResult success(String html, String msg, int iter, int score) {
        return new AgentLoopResult(true, html, msg, iter, score, null, null, null, null, null);
    }
    public static AgentLoopResult failure(String error, int iter) {
        return new AgentLoopResult(false, null, null, iter, 0, error, null, null, null, null);
    }
    // 新工厂
    public static AgentLoopResult successWithEvidence(...) {...}
    public static AgentLoopResult failureWithEvidence(...) {...}
}
```

`AgentLoop.run()` 末尾用新工厂返回（含 memory.lastEvaluationObservation / runTrace / activeSkillName）。

#### 2. `EvidenceMapper`（纯静态）

```java
public final class EvidenceMapper {
    private EvidenceMapper() {}
    public static String toScoresJson(EvaluationObservation obs);
    public static String toProbeSummaryJson(EvaluationObservation obs);
    public static String toClassifiedIssuesJson(EvaluationObservation obs);
    public static String toIterTracesJson(RunTrace trace);  // 不含 issuesSnapshot 大字段
}
```

用 Jackson `ObjectMapper`（已是项目依赖）。`null` 输入 → 返回 `"{}"` 或 `"[]"`，不抛 NPE。

#### 3. `SessionService.recordEvidence`

```java
public synchronized String recordEvidence(
    String sessionId,
    String gameRunId,           // 可空
    String modelKey,
    AgentLoopResult result
) {
    GameRunEvaluationEntity e = new GameRunEvaluationEntity();
    e.setSessionId(sessionId);
    e.setGameRunId(gameRunId);
    e.setSkillName(result.activeSkillName());
    e.setModelKey(modelKey);
    e.setSuccess(result.success() ? 1 : 0);
    e.setErrorType(result.errorType());
    e.setTotalScore(result.evalScore());
    EvaluationObservation obs = result.lastEvaluationObservation();
    e.setDegraded(obs != null && obs.isDegraded() ? 1 : 0);
    e.setDegradedReason(obs != null ? obs.getDegradedReason() : null);
    e.setIterationCount(result.iterations());
    if (result.runTrace() != null && result.runTrace().last() != null) {
        e.setFinalIterationSummary(result.runTrace().last().getSummary());
    }
    e.setScoresJson(EvidenceMapper.toScoresJson(obs));
    e.setProbeSummaryJson(EvidenceMapper.toProbeSummaryJson(obs));
    e.setClassifiedIssuesJson(EvidenceMapper.toClassifiedIssuesJson(obs));
    e.setIterTracesJson(EvidenceMapper.toIterTracesJson(result.runTrace()));
    return evaluationRepository.insert(e);
}
```

注入 `GameRunEvaluationRepository`。

#### 4. `GameChatController.generateGameV2` 调用链

在 `recordRun(...)` 之后追加：

```java
try {
    String gameRunId = recordResult != null ? recordResult.gameRunId() : null;
    sessionService.recordEvidence(finalSessionId, gameRunId, finalModelKey, result);
} catch (Exception e) {
    log.error("写入 evidence 失败（不影响响应）: {}", e.getMessage(), e);
}
```

容错：写库失败不影响 response（沿用 `recordRun` 的容错风格）。

#### 5. `AgentLoop.run()` 末尾改用新工厂

把现有 `AgentLoopResult.success(...)` 调用替换为带 evidence 的工厂；`failure(...)` 同理。**不改 run() 签名**。

`activeSkillName` 来源：
- `toolContext.getActiveSkill() != null ? toolContext.getActiveSkill().getName() : memory.getPreloadedSkill()`

`errorType`：用 `ErrorClassifier.classify(exception)` 在 catch 块里赋值。

#### 6. 集成测试

新建 `EvidenceWriteEndToEndTest`（@SpringBootTest）：

- mock `ChatModel` 返回固定响应
- 让 `GameEvaluationTool` 的成功路径或降级路径写入 `EvaluationObservation`
- 调 `agentLoop.run("...", null)` 并 `sessionService.recordEvidence(...)`
- 用 `evaluationRepository.findById(...)` 直查 DB，断言：
  - `success / degraded / total_score / iteration_count` 字段一致
  - `scores_json / probe_summary_json / classified_issues_json / iter_traces_json` 是合法 JSON 且关键字段可解出

### 约束（已冻结的边界）

- `AgentLoop.run(String, String)` 方法签名不动
- `AgentLoopResult.success/failure` 旧工厂保留可用（防止 controller / 调用方爆炸）
- 写 evidence 失败不影响 user response
- 不改前端 / API 响应结构
- 不引入异步 / 消息队列

### 复用的现有模式

- `SessionService.recordRun` 容错路径
- `Jackson ObjectMapper` 序列化
- Repository `synchronized` insert
- `ErrorClassifier` 异常分类

### 依赖的前置子任务

- Step 3 表存在
- harness Step 2-3 的 `EvaluationObservation` / `RunTrace` 在 `WorkingMemory` 上
- `260521-game-storage-db` 的 `SessionService` / `recordRun` 流程

## 【验收契约（Evaluator 输入）】

### 代码结构验证

- [ ] `AgentLoopResult` 含新字段且旧 `success/failure` 工厂仍可调用
- [ ] `EvidenceMapper` 是 final + 私有构造器 + 纯静态方法
- [ ] `SessionService.recordEvidence` 存在，写库失败 catch 不抛
- [ ] `GameChatController.generateGameV2` 在 `recordRun` 之后调用 `recordEvidence`
- [ ] `AgentLoop.run()` 签名未改

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `cd game-agent-backend && mvn compile` | exit 0 |
| `cd game-agent-backend && mvn test -Dtest=EvidenceWriteEndToEndTest` | exit 0 |
| `cd game-agent-backend && mvn test` | 0 退化 |

### 端到端 SSOT 验证

1. 启动 backend
2. POST `/api/game/v2/generate` 用 `mock-fixture` 模型避免 LLM 配额
3. 直查 `game_run_evaluations` 应有 1 条新记录，字段对照 sessions/messages/game_runs 一致
4. 失败场景：传一个会让 AgentLoop 抛异常的输入（用一个 mock failing model），仍应有 evidence 写入，`success=0`、`game_run_id=NULL`、`error_type` 非空

### 数据/字段验收

- [ ] `scores_json` 是合法 JSON
- [ ] `iter_traces_json` 是数组，长度 = `iteration_count`
- [ ] `degraded=1` 对应 `degraded_reason` 非空
- [ ] `success=0` 对应 `error_type` 非空，`game_run_id` 为 NULL

### 负面用例

- [ ] `EvaluationObservation == null`（极端：评估工具完全没跑）→ scores_json="{}"，probe_summary_json="{}"
- [ ] `RunTrace == null`（理论上不可能；防御性）→ iter_traces_json="[]"
- [ ] DB 写失败时 user response 仍 200

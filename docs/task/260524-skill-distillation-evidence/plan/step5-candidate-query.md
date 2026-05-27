# Step 5：候选样本查询

## 背景

Step 4 让 evidence 落库；本步骤提供**最小查询能力**：能按 Skill / 分数 / 失败类型 / issue 类别筛出蒸馏候选。**只读 API + CLI 脚本**，不做 UI。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `game-agent-backend/src/main/java/com/sumo/agent/api/EvidenceController.java`（新建）
  - `game-agent-backend/src/main/java/com/sumo/agent/infra/db/GameRunEvaluationRepository.java`（增加查询方法，**不改**已有方法签名）
  - `game-agent-backend/src/main/java/com/sumo/agent/infra/db/SkillDistillationCandidateRepository.java`（增加查询 / 更新方法）
  - `game-agent-backend/src/main/java/com/sumo/agent/infra/storage/EvidenceQueryService.java`（新建，编排层）
  - `scripts/distillation-candidates.sh`（新建，CLI 脚本调 API + 输出 markdown 报告）
  - 对应单元/集成测试

- **不可改文件**：
  - `schema.sql`（Step 3 已冻结，不动）
  - `agent/loop/*` / `agent/evaluation/*`
  - 前端文件
  - `application.yml`

- **不可新增的抽象**：
  - 不引入分页插件（Spring Data 等）
  - 不引入 GraphQL
  - 不引入向量检索（关键词 / SQL LIKE 即可）

### 产出清单

#### 1. Repository 查询方法增量

`GameRunEvaluationRepository`：

- `List<GameRunEvaluationEntity> listBySkillAndScore(String skillName, int minScore, int maxScore, int limit)`
- `List<GameRunEvaluationEntity> listByIssueCategory(String category, String severity, int limit)` — 用 SQL `LIKE '%"category":"<value>"%'` 匹配 `classified_issues_json`（性能不敏感场景，本地 SQLite < 万行）
- `int countByConditions(String skillName, Integer minScore, Integer maxScore, Boolean onlyDegraded, Boolean onlySuccess)` — 全字段可空，用于 stats

`SkillDistillationCandidateRepository`：

- `List<SkillDistillationCandidateEntity> listByStatus(String status, int limit)`
- `int upsertFromEvaluation(String evaluationId, String skillName, String status, String note)` — 若已存在按 evaluationId 则更新 status/note + updatedAt，否则插入新记录

#### 2. `EvidenceQueryService`

```java
@Service
public class EvidenceQueryService {
    public List<EvidenceSummary> findCandidates(String skill, int minScore, int maxScore, int limit);
    public EvidenceDetail findDetail(String evaluationId);
    public Map<String, Object> stats();  // 总数/失败率/降级率/各 skill 平均分
    public String promoteToCandidate(String evaluationId, String note);  // raw → candidate
    public String accept(String candidateId, String note);                // candidate → accepted
    public String reject(String candidateId, String note);                // candidate → rejected
}
```

`EvidenceSummary` / `EvidenceDetail` 是简单 DTO，前者不含 `*_json` 大字段。

#### 3. `EvidenceController` REST 端点

```
GET    /api/evidence/candidates?skill=xxx&minScore=0&maxScore=100&limit=20  → 列表
GET    /api/evidence/{evaluationId}                                          → 详情（含解析后的 scores/probeSummary/issues/iterTraces）
GET    /api/evidence/stats                                                   → 总览
POST   /api/evidence/{evaluationId}/promote   body {"note": "..."}           → 'raw' → 'candidate'
POST   /api/evidence/candidates/{id}/accept   body {"note": "..."}           → 'candidate' → 'accepted'
POST   /api/evidence/candidates/{id}/reject   body {"note": "..."}           → 'candidate' → 'rejected'
```

`@CrossOrigin(origins = "*")` 沿用现有风格。

#### 4. `scripts/distillation-candidates.sh`

bash 脚本，调上面 4-5 个端点拉数据，输出 markdown 报告到 stdout：

```
# Distillation Candidates Report — 2026-XX-XX

## 总览
- 总评估数: N
- 失败率: X%
- 降级率: Y%
- 各 Skill 平均分: ...

## 失败样本（success=0）
1. [evaluation_id] skill=snake-adventure model=qwen3.6-max-preview error_type=timeout
   迭代 5 轮 / 最终评分 0 / 最后一轮: "..."

## 低分样本（score < 60）
...

## 蒸馏候选（status='candidate'）
...
```

参考 `scripts/playability-oracle.sh` 风格：`set -euo pipefail`、curl + jq 解析。

#### 5. 测试

- `EvidenceQueryServiceTest`（@SpringBootTest）覆盖 findCandidates / stats / promote/accept/reject 状态机推进
- 状态机负面用例：already 'accepted' 的 candidate 再调 accept 应不报错但 updated_at 刷新（幂等）；'rejected' → accept 也允许（人工反悔场景）

### 约束（已冻结的边界）

- 不改 schema
- 不改 Step 4 的 evidence 写入路径
- 不引入 ORM / GraphQL / 分页插件
- API 端点放在新 controller `EvidenceController`，不污染 `GameChatController` / `GameStorageController`
- CLI 脚本不引入新依赖（仅 curl + jq）

### 复用的现有模式

- `GameStorageController` 的 `@RestController` + `ResponseEntity` 风格
- `Map<String, Object>` 简单响应
- `scripts/playability-oracle.sh` 的 bash 风格

### 依赖的前置子任务

Step 3-4 已落地。

## 【验收契约（Evaluator 输入）】

### 代码结构验证

- [ ] `EvidenceController` 含 6 个端点
- [ ] `EvidenceQueryService` 含 7 个方法
- [ ] Repository 增量方法不破坏旧方法签名
- [ ] `scripts/distillation-candidates.sh` `chmod +x` 可执行

### 命令验收

| 命令 | 通过标准 |
|------|---------|
| `cd game-agent-backend && mvn test -Dtest=EvidenceQueryServiceTest` | exit 0 |
| `cd game-agent-backend && mvn compile` | exit 0 |
| `bash scripts/distillation-candidates.sh` | exit 0，stdout 是有效 markdown |

### 端到端 SSOT 验证

1. 启动 backend，先用 mock-fixture 跑 3 次生成（让 evidence 表有数据）
2. `curl http://localhost:8088/api/evidence/stats` → 返回总数 ≥ 3
3. `curl http://localhost:8088/api/evidence/candidates?limit=10` → 返回数组
4. 取一个 evaluationId，`POST /promote` → DB 中应新增 candidate 记录 status='raw'（首次写）或 'candidate'（再 promote 时迁移）
5. CLI 脚本输出与上面 API 数据一致

### 数据/字段验收

- [ ] `EvidenceSummary` 不含 `*_json` 大字段
- [ ] `EvidenceDetail` 把 `scores_json` 等解析为对象返回前端
- [ ] 状态机：raw → candidate → accepted/rejected；不允许 raw 直接到 accepted

### 负面用例

- [ ] 不存在的 evaluationId promote → 404
- [ ] 已 accepted 的 candidate 再 accept → 200 + updated_at 刷新（幂等）
- [ ] CLI 脚本在 backend 不存在时优雅报错（curl 失败）

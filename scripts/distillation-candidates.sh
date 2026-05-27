#!/usr/bin/env bash
# 蒸馏候选报告 — 调 /api/evidence/* 拉数据，输出 markdown 到 stdout
#
# 用法：
#   bash scripts/distillation-candidates.sh
#   AGENT_BASE_URL=http://localhost:8088 LIMIT=20 bash scripts/distillation-candidates.sh
#
# 退出码：
#   0  - 成功
#   1  - backend 不可达
set -euo pipefail

BASE_URL="${AGENT_BASE_URL:-http://localhost:8088}"
LIMIT="${LIMIT:-20}"

# 检查 backend 可达（沿用 playability-oracle.sh 的 noproxy + 超时风格）
if ! curl -sf -m 5 --noproxy '*' "$BASE_URL/api/game/agents" > /dev/null; then
  echo "backend 不可达: $BASE_URL" >&2
  exit 1
fi

echo "# Distillation Candidates Report — $(date '+%Y-%m-%d %H:%M:%S')"
echo

# 1. 总览
echo "## 总览"
STATS=$(curl -sf --noproxy '*' "$BASE_URL/api/evidence/stats")
echo "$STATS" | python3 -c "
import sys, json
r = json.load(sys.stdin)
d = r.get('data', {})
print(f\"- 总评估数: {d.get('totalEvaluations', 0)}\")
print(f\"- 失败数: {d.get('totalFailures', 0)}\")
print(f\"- 降级数: {d.get('totalDegraded', 0)}\")
print(f\"- 候选: {d.get('totalCandidates', 0)} | 已接受: {d.get('totalAccepted', 0)} | 已拒绝: {d.get('totalRejected', 0)}\")
"
echo

# 2. 失败/低分样本
echo "## 候选样本（最多 $LIMIT 条）"
curl -sf --noproxy '*' "$BASE_URL/api/evidence/candidates?limit=$LIMIT" | python3 -c "
import sys, json
r = json.load(sys.stdin)
data = r.get('data', [])
if not data:
    print('（无样本）')
for i, e in enumerate(data, 1):
    eid = (e.get('id') or '')[:8]
    skill = e.get('skillName') or '?'
    model = e.get('modelKey') or '?'
    err = e.get('errorType') or '-'
    print(f\"{i}. id={eid} skill={skill} model={model} error_type={err}\")
    iters = e.get('iterationCount', 0)
    score = e.get('totalScore', 0)
    summary = e.get('finalIterationSummary') or '-'
    print(f\"   迭代 {iters} 轮 / 评分 {score} / 最后一轮: {summary}\")
"
echo

echo "## 操作提示"
echo "- 详情：\`GET ${BASE_URL}/api/evidence/{evaluationId}\`"
echo "- 推进 candidate：\`POST ${BASE_URL}/api/evidence/{evaluationId}/promote\`"
echo "- 状态机：\`POST ${BASE_URL}/api/evidence/candidates/{id}/accept\` 或 \`/reject\`"

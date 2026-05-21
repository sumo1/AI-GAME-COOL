# Step 6：多采样验证 SKILL 演进有效（任务收口端到端）

## 背景

**这一步是本任务的收口端到端**——证明新建的 snake-adventure SKILL.md 真的让 LLM 第一次生成就能玩。LLM 是非确定性的，所以必须用多次采样，统计通过率。

通过率 ≥ 2/3（即 9 次中 ≥ 6 次 oracle PASS）→ SKILL 演进有效，任务交付。

## 【实现契约（主会话执行 — 不分给 subagent）】

### 范围

- **可改文件**：
  - `scripts/snake-skill-multisample.sh`（新建，多采样脚本）
  - `docs/task/260521-playable-snake-evolution/memory/2026-05-21-multisample-results.md`（新建，结果记录）

- **不可改文件**：
  - `resources/skills/snake-adventure/SKILL.md`（Step 5 产出，本 step 跑验证不调）
  - oracle 工具（Step 1 产出）
  - 其它 SKILL.md

### 产出清单

#### `scripts/snake-skill-multisample.sh`

```bash
#!/usr/bin/env bash
set -e

# 多采样验证 snake-adventure SKILL 演进有效
# 用法：./scripts/snake-skill-multisample.sh [round-count] [samples-per-round]
# 默认：3 round × 3 samples = 9 次 LLM

ROUND="${1:-3}"
SAMPLES="${2:-3}"

OUT_DIR=/tmp/snake-multisample/run-$(date +%s)
mkdir -p "$OUT_DIR"
SUMMARY="$OUT_DIR/summary.md"

echo "# Snake Multi-sample Verification" > "$SUMMARY"
echo "" >> "$SUMMARY"
echo "Rounds: $ROUND, Samples per round: $SAMPLES, Total: $((ROUND * SAMPLES))" >> "$SUMMARY"
echo "" >> "$SUMMARY"

# 启 backend（不动开发库，用临时 DB）
TEST_DB=/tmp/snake-multisample-$(date +%s).db
lsof -t -i:8088 2>/dev/null | xargs -r kill -9 2>/dev/null
sleep 1
set -a; source /Users/sumo/workplace/ai/AI-GAME/.env; set +a
AGENT_DB_URL="jdbc:sqlite:$TEST_DB" \
  ( cd game-agent-backend && nohup mvn spring-boot:run -q ) > "$OUT_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
trap "kill $BACKEND_PID 2>/dev/null; rm -f ${TEST_DB}*" EXIT

for i in $(seq 1 90); do
  curl -sf http://localhost:8088/api/game/agents > /dev/null && break
  sleep 1
done

# 跑多采样
TOTAL_PASS=0
TOTAL=$((ROUND * SAMPLES))

for r in $(seq 1 $ROUND); do
  echo "## Round $r" >> "$SUMMARY"
  for s in $(seq 1 $SAMPLES); do
    LABEL="r${r}-s${s}"
    GAME_HTML="$OUT_DIR/snake-$LABEL.html"
    
    echo "  Generating $LABEL..."
    RESP=$(curl -sX POST http://localhost:8088/api/game/v2/generate \
      -H 'Content-Type: application/json' \
      -d '{"userInput":"做一个贪吃蛇游戏"}' \
      --max-time 480) || RESP='{"success":false,"error":"curl failed"}'
    
    # 用 python 取 html（避开 jq 控制字符问题）
    python3 -c "
import json, sys
try:
    d = json.loads('''$RESP''')
    if d.get('success'):
        html = d.get('gameData', {}).get('html', '')
        with open('$GAME_HTML', 'w') as f:
            f.write(html)
        sys.exit(0)
except Exception as e:
    pass
sys.exit(1)
" || { echo "    LLM fail" >> "$SUMMARY"; continue; }
    
    # 跑 oracle
    ./scripts/playability-oracle.sh "$GAME_HTML" > "$OUT_DIR/oracle-$LABEL.txt" 2>&1
    CODE=$?
    if [ $CODE -eq 0 ]; then
      echo "  - $LABEL: PASS" >> "$SUMMARY"
      TOTAL_PASS=$((TOTAL_PASS + 1))
    else
      echo "  - $LABEL: FAIL (exit $CODE)" >> "$SUMMARY"
    fi
  done
  
  # 早停优化：第一轮如果通过率 100%，可以提前结束（已经稳定）
  if [ $r -eq 1 ] && [ $TOTAL_PASS -eq $SAMPLES ]; then
    echo "  → First round 100% PASS, early stop ✓" >> "$SUMMARY"
    break
  fi
done

# 最终判定
echo "" >> "$SUMMARY"
echo "## Result" >> "$SUMMARY"
echo "" >> "$SUMMARY"
ACTUAL_RUNS=$(grep -cE "^  - " "$SUMMARY")
echo "Pass rate: $TOTAL_PASS / $ACTUAL_RUNS" >> "$SUMMARY"

THRESHOLD=$((ACTUAL_RUNS * 2 / 3))
if [ $TOTAL_PASS -ge $THRESHOLD ]; then
  echo "✅ SKILL 演进有效（≥ 2/3 通过率）" >> "$SUMMARY"
  cat "$SUMMARY"
  exit 0
else
  echo "❌ SKILL 演进未达标（< 2/3 通过率）需回 Step 4 调试" >> "$SUMMARY"
  cat "$SUMMARY"
  exit 1
fi
```

#### `memory/2026-05-21-multisample-results.md`

记录每轮结果 + 失败案例分析：

```markdown
# Multi-sample 验证结果

> 时间：YYYY-MM-DD
> SKILL 版本：commit <hash>
> 总采样数：N

## 结果

通过率：X / N（X% 通过）

| Round | Sample | Verdict | 失败原因（如有）|
|-------|--------|---------|----------------|
| 1     | 1      | PASS    | -              |
| 1     | 2      | FAIL    | 按键不响应     |
| ...   |        |         |                |

## 失败案例分析

### r1-s2（FAIL）

**LLM 生成的 HTML 摘要**：<前 200 字符>
**oracle 诊断包**：<run-dir 路径>
**根因推测**：...
**对 SKILL 的启示**：是否需要在 SKILL.md 加新的"常见问题"？

### ...

## 收敛性判断

- 通过率 ≥ 2/3：✅ SKILL 演进收敛
- 通过率 < 2/3：返回 Step 4，针对失败案例补 SKILL.md 改动后重跑
```

### 多轮迭代逻辑（关键）

如果第 1 轮 3 次采样**通过率 < 2/3**：
1. 看失败 case 的诊断包，推理新的 SKILL.md 改动
2. 修改 `resources/skills/snake-adventure/SKILL.md`
3. 跑第 2 轮 3 次
4. 如果第 2 轮通过率 ≥ 2/3，收敛 ✅
5. 否则同样推理 + 改 SKILL → 跑第 3 轮
6. 第 3 轮仍未达标 → 标 ⚠️ "本次未收敛"，提交当前进展 + 上报，**不硬循环**

**早停优化**：第 1 轮 100% PASS → 提前停（已经稳定，不浪费 6 次 LLM）。

### 约束

- **不动 oracle**（已在 Step 1-3 锁定）
- **不动 fixture**（snake-v0 / snake-fixed 不动）
- **每次跑用临时 DB**（`AGENT_DB_URL=jdbc:sqlite:/tmp/...`）不污染开发库
- **退出码就是任务收口信号**：0=演进有效，1=未达标
- **总 budget 9 次 LLM**：超过 budget 不再扩容（不是技术限制，是质量信号——演进难度过高）

### 复用

- backend 启动 / curl 调 LLM：参考之前任务的端到端脚本
- python3 解析响应：参考 Step 5 的"绕开 jq 严格解析"模式
- oracle 调用：Step 1 产出

### 依赖

- Step 5 完成（snake-adventure SKILL 就绪）

## 【验收契约（任务收口）】

### 命令验收

```bash
chmod +x scripts/snake-skill-multisample.sh
./scripts/snake-skill-multisample.sh

# 期望：exit 0 表示通过率 ≥ 2/3
```

### 数据验收

```bash
# /tmp/snake-multisample/run-{ts}/ 含 summary.md
ls /tmp/snake-multisample/run-*/summary.md | head -1

# summary.md 含通过率
grep "Pass rate:" /tmp/snake-multisample/run-*/summary.md | tail -1

# memory 记录已写
[ -f docs/task/260521-playable-snake-evolution/memory/2026-05-21-multisample-results.md ] || exit 1
```

### 关键人工检视

- 抽查通过的样本：HTML 真的能玩吗（不是 oracle 假阳性）？
- 失败样本：诊断包是否对症？

### 任务收口判定

通过率 ≥ 2/3 + summary.md 完整 + memory 记录 = ✅ 任务交付。

进 Step 7 写文档 + push。

## 退路（未达标时）

如果 3 轮都没过 2/3：

1. 标 ⚠️ "本次演进未达预期"
2. 把当前 SKILL.md（不论能不能用）保留——它至少有部分改进
3. memory 写明：哪些改动没起作用、为什么；下次改进的方向
4. **仍然进 Step 7 写文档**——本任务交付的 SKILL 演进 SOP 在"未达标的失败案例"上同样有价值，不是只有成功才能写
5. 把"snake SKILL 收敛失败"作为未来任务的入口

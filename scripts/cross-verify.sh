#!/usr/bin/env bash
# 交叉验证：4 fixture 同时跑 GameEvaluator + oracle，看方向是否一致
# 用法：./scripts/cross-verify.sh
# 任务 260522-evaluator-oracle-shared-core Step 4

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

OUT_DIR="/tmp/cross-verify/run-$(date +%s)"
mkdir -p "$OUT_DIR"
SUMMARY="$OUT_DIR/summary.md"

# fixture | 期望 evaluator score | 期望 oracle | 用例描述
FIXTURES=(
  "test/fixtures/playability/dead-page.html|<30|FAIL|无 JS 死页面"
  "test/fixtures/playability/keytest.html|30-60|PASS|最小键盘响应"
  "test/fixtures/playability/snake-v0.html|60-100|PASS|真 LLM 贪吃蛇"
  "test/fixtures/playability/snake-fixed.html|60-100|PASS|经过调试的贪吃蛇"
)

# 先编译（保证 GameEvaluatorMain 类存在）
echo "[cross-verify] 编译 backend..."
( cd game-agent-backend && mvn compile -q ) || { echo "❌ 编译失败"; exit 2; }

# 准备 classpath（通过 mvn 拉所有依赖到一个 cp 文件）
CP_FILE="$OUT_DIR/classpath.txt"
( cd game-agent-backend && mvn -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE" ) || { echo "❌ classpath 拉取失败"; exit 2; }
CP="game-agent-backend/target/classes:$(cat "$CP_FILE")"

echo "# Cross-verify Results — $(date '+%Y-%m-%d %H:%M:%S')" > "$SUMMARY"
echo "" >> "$SUMMARY"
echo "| fixture | evaluator score | oracle | 期望 oracle | 方向一致 |" >> "$SUMMARY"
echo "|---|---|---|---|---|" >> "$SUMMARY"

OK=0
TOTAL=0

for entry in "${FIXTURES[@]}"; do
  IFS='|' read -r fixture expect_score expect_oracle desc <<< "$entry"

  if [ ! -f "$fixture" ]; then
    echo "⚠️  跳过缺失 fixture: $fixture"
    continue
  fi

  TOTAL=$((TOTAL + 1))
  name=$(basename "$fixture")
  echo
  echo "=== Case: $desc ($name) ==="

  # 1) 跑 GameEvaluator
  EV_LOG="$OUT_DIR/evaluator-$name.log"
  java -cp "$CP" \
    com.sumo.agent.agent.evaluation.GameEvaluatorMain "$fixture" \
    > "$EV_LOG" 2>&1
  EV_CODE=$?
  if [ $EV_CODE -eq 0 ]; then
    EV_SCORE=$(grep "^totalScore=" "$EV_LOG" | head -1 | awk -F= '{print $2}')
    EV_SCORE=${EV_SCORE:-N/A}
    echo "  evaluator totalScore=$EV_SCORE"
  else
    EV_SCORE="ERR(exit=$EV_CODE)"
    echo "  ⚠️  evaluator 失败 (exit $EV_CODE)，详见 $EV_LOG"
  fi

  # 2) 跑 oracle
  ./scripts/playability-oracle.sh "$fixture" > "$OUT_DIR/oracle-$name.log" 2>&1
  O_CODE=$?
  if [ $O_CODE -eq 0 ]; then O_RES=PASS; elif [ $O_CODE -eq 1 ]; then O_RES=FAIL; else O_RES="TOOLING(exit=$O_CODE)"; fi
  echo "  oracle: $O_RES"

  # 3) 判方向一致性
  CONSISTENT=NO
  if [ "$expect_oracle" = "PASS" ] && [ "$O_RES" = "PASS" ]; then CONSISTENT=YES; fi
  if [ "$expect_oracle" = "FAIL" ] && [ "$O_RES" = "FAIL" ]; then CONSISTENT=YES; fi

  if [ "$CONSISTENT" = "YES" ]; then OK=$((OK + 1)); fi
  echo "| $name | $EV_SCORE | $O_RES | $expect_oracle | $CONSISTENT |" >> "$SUMMARY"
done

echo "" >> "$SUMMARY"
echo "## 总结：$OK / $TOTAL 方向一致" >> "$SUMMARY"
echo "" >> "$SUMMARY"
echo "运行目录：$OUT_DIR" >> "$SUMMARY"

echo
echo "==================================================="
cat "$SUMMARY"
echo "==================================================="

if [ $OK -eq $TOTAL ] && [ $TOTAL -gt 0 ]; then
  exit 0
else
  exit 1
fi

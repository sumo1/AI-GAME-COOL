#!/usr/bin/env bash
# 多采样验证 snake-adventure SKILL 演进有效（不依赖 backend / DashScope，由调用方提供样本）
#
# 用法（外部传样本目录，oracle 跑统计）：
#   ./scripts/snake-skill-multisample.sh <samples-dir>
#
# samples-dir 中放若干 sample-*.html 文件（建议 sample-r1-s1.html, sample-r1-s2.html, ...）
# 每个文件用同一 SKILL 由 LLM 生成。脚本对每个跑 oracle，统计通过率。
#
# 退出码：
#   0 = 通过率 ≥ 2/3，SKILL 演进有效
#   1 = 通过率 < 2/3
#   2 = 工具错误

set -uo pipefail

ORACLE=./scripts/playability-oracle.sh

if [ $# -ne 1 ]; then
  echo "Usage: $0 <samples-dir>" >&2
  echo "  samples-dir: 目录含若干 sample-*.html" >&2
  exit 2
fi

SAMPLES_DIR="$1"
if [ ! -d "$SAMPLES_DIR" ]; then
  echo "[multisample] tooling error: samples dir not found: $SAMPLES_DIR" >&2
  exit 2
fi

OUT_DIR="/tmp/snake-multisample/run-$(date +%s)"
mkdir -p "$OUT_DIR"
SUMMARY="$OUT_DIR/summary.md"

echo "# Snake Multi-sample Verification" > "$SUMMARY"
echo "" >> "$SUMMARY"
echo "Samples dir: $SAMPLES_DIR" >> "$SUMMARY"
echo "Started: $(date)" >> "$SUMMARY"
echo "" >> "$SUMMARY"

PASS=0
TOTAL=0

for sample in "$SAMPLES_DIR"/sample-*.html; do
  if [ ! -f "$sample" ]; then continue; fi
  TOTAL=$((TOTAL + 1))
  name=$(basename "$sample")

  echo "  -> $name"
  $ORACLE "$sample" > "$OUT_DIR/oracle-$name.txt" 2>&1
  CODE=$?

  if [ $CODE -eq 0 ]; then
    PASS=$((PASS + 1))
    echo "  - $name: PASS" >> "$SUMMARY"
  else
    echo "  - $name: FAIL (exit $CODE)" >> "$SUMMARY"
  fi
done

echo "" >> "$SUMMARY"
echo "## Result" >> "$SUMMARY"
echo "" >> "$SUMMARY"
echo "Pass rate: $PASS / $TOTAL" >> "$SUMMARY"
echo "" >> "$SUMMARY"

if [ $TOTAL -eq 0 ]; then
  echo "❌ no samples found in $SAMPLES_DIR" >> "$SUMMARY"
  cat "$SUMMARY"
  exit 2
fi

THRESHOLD=$((TOTAL * 2 / 3))
[ $THRESHOLD -lt 1 ] && THRESHOLD=1

if [ $PASS -ge $THRESHOLD ]; then
  echo "✅ SKILL 演进有效（≥ 2/3 通过率）" >> "$SUMMARY"
  cat "$SUMMARY"
  exit 0
else
  echo "❌ SKILL 演进未达标（< 2/3 通过率）" >> "$SUMMARY"
  cat "$SUMMARY"
  exit 1
fi

#!/usr/bin/env bash
# 多 fixture 跑 oracle 验证鉴别力
# 用法：./scripts/playability-oracle-self-test.sh
set -uo pipefail   # 不用 -e，要让某条 fail 后继续跑下一条

ORACLE=./scripts/playability-oracle.sh
FIX=./test/fixtures/playability

PASS=0
FAIL=0
WARN=0

run_case() {
  local fixture="$1"
  local expected="$2"   # PASS / FAIL / EITHER
  local desc="$3"

  echo "=== Case: $desc ==="
  $ORACLE "$fixture"
  local code=$?

  case "$expected" in
    PASS)
      if [ $code -eq 0 ]; then
        echo "  ✓ 期望 PASS，oracle 判 PASS"
        PASS=$((PASS+1))
      else
        echo "  ✗ 期望 PASS，oracle 判 FAIL（exit $code）"
        FAIL=$((FAIL+1))
      fi
      ;;
    FAIL)
      if [ $code -eq 1 ]; then
        echo "  ✓ 期望 FAIL，oracle 判 FAIL"
        PASS=$((PASS+1))
      else
        echo "  ✗ 期望 FAIL，oracle 判 PASS（假阳性！）（exit $code）"
        FAIL=$((FAIL+1))
      fi
      ;;
    EITHER)
      if [ $code -eq 0 ]; then
        echo "  ⚠ snake-v0 oracle 判 PASS（v0 真有响应）"
        WARN=$((WARN+1))
      elif [ $code -eq 1 ]; then
        echo "  ⚠ snake-v0 oracle 判 FAIL（v0 不能玩，诊断包待人工检视）"
        WARN=$((WARN+1))
      else
        echo "  ✗ snake-v0 oracle exit $code（工具自身错误）"
        FAIL=$((FAIL+1))
      fi
      ;;
  esac
  echo ""
}

run_case "$FIX/keytest.html"   PASS   "keytest（最小键盘响应）"
run_case "$FIX/dead-page.html" FAIL   "dead-page（静态死页面）"
run_case "$FIX/snake-v0.html"  EITHER "snake-v0（LLM 生成贪吃蛇）"

echo "=================================="
echo "PASS: $PASS / FAIL: $FAIL / WARN: $WARN"
echo "=================================="

# 通过条件：keytest 必须 PASS、dead-page 必须 FAIL、snake-v0 不论
[ $FAIL -eq 0 ] && exit 0 || exit 1

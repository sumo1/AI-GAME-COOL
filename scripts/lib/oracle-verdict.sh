#!/usr/bin/env bash
# oracle-verdict.sh
# 用法：./oracle-verdict.sh <run-dir>
# 读 <run-dir>/result.json，写 <run-dir>/verdict.txt 并以语义化 exit code 退出
#   exit 0 = PASS
#   exit 1 = FAIL
#   exit 2 = tooling error（result.json 缺失等）
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <run-dir>" >&2
  exit 2
fi

RUN_DIR="$1"
RESULT_JSON="$RUN_DIR/result.json"
VERDICT_TXT="$RUN_DIR/verdict.txt"

if [ ! -f "$RESULT_JSON" ]; then
  echo "[verdict] tooling error: result.json missing in $RUN_DIR" >&2
  exit 2
fi

# 用 python3 做判定（jq 表达力不够，避免引入新依赖）
python3 - "$RESULT_JSON" "$VERDICT_TXT" "$RUN_DIR" <<'PY'
import json
import sys

result_path, verdict_path, run_dir = sys.argv[1], sys.argv[2], sys.argv[3]

with open(result_path, "r", encoding="utf-8") as f:
    r = json.load(f)

baseline = r.get("baseline")
final = r.get("final")
auto_paths = set(r.get("auto_changing_paths") or [])
auto_canvases = set(r.get("auto_changing_canvases") or [])
js_errors = r.get("js_errors") or []
driver_errors = r.get("errors") or []
keys_pressed = r.get("keys_pressed") or []
html_path = r.get("html_path", "?")

lines = []
lines.append("===== Playability Oracle Verdict =====")

changes = []
signal_lines = []

if not baseline or not final:
    # 信号收集失败，按 FAIL（但记录原因）
    lines.append("Result: FAIL")
    lines.append("HTML: " + html_path)
    lines.append("Run dir: " + run_dir)
    lines.append("")
    lines.append("Signals after %d keypresses:" % len(keys_pressed))
    lines.append("  - baseline or final signal collection FAILED")
    lines.append("  - JS errors during test: %d" % len(js_errors))
    lines.append("  - Driver errors: %d" % len(driver_errors))
    if driver_errors:
        for e in driver_errors[:10]:
            lines.append("    * " + json.dumps(e, ensure_ascii=False))
    lines.append("")
    lines.append("Verdict reason: FAIL — signal collection failed (driver or page broken)")
    with open(verdict_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    sys.exit(1)

# canvas 对比
base_cnv = {c["idx"]: c["hash"] for c in baseline.get("canvases", [])}
final_cnv = {c["idx"]: c["hash"] for c in final.get("canvases", [])}
canvas_indices = sorted(set(list(base_cnv.keys()) + list(final_cnv.keys())))
for idx in canvas_indices:
    bh = base_cnv.get(idx, "<missing>")
    fh = final_cnv.get(idx, "<missing>")
    auto_marker = " [auto-changing, ignored]" if idx in auto_canvases else ""
    if bh != fh and idx not in auto_canvases:
        signal_lines.append("  - canvas[%d] pixel hash: %s -> %s  [CHANGED]" % (idx, bh, fh))
        changes.append("canvas[%d] hash differs" % idx)
    else:
        status = "unchanged" if bh == fh else "CHANGED" + auto_marker
        signal_lines.append("  - canvas[%d] pixel hash: %s -> %s  [%s]" % (idx, bh, fh, status))

# numeric 对比
base_num = {n["path"]: n["val"] for n in baseline.get("numeric", [])}
final_num = {n["path"]: n["val"] for n in final.get("numeric", [])}
seen_paths = set()
for path, fval in final_num.items():
    seen_paths.add(path)
    bval = base_num.get(path, "<absent>")
    if bval != fval:
        if path in auto_paths:
            signal_lines.append('  - numeric path "%s": "%s" -> "%s"  [auto-changing, ignored]' % (path, bval, fval))
        else:
            signal_lines.append('  - numeric path "%s": "%s" -> "%s"  [CHANGED]' % (path, bval, fval))
            changes.append('numeric path "%s" went %s -> %s' % (path, bval, fval))
# 也展示消失的路径（key-press 后某些数字节点可能被替换）
for path, bval in base_num.items():
    if path in seen_paths:
        continue
    if path in auto_paths:
        signal_lines.append('  - numeric path "%s": "%s" -> <gone>  [auto-changing, ignored]' % (path, bval))
    else:
        signal_lines.append('  - numeric path "%s": "%s" -> <gone>  [CHANGED]' % (path, bval))
        changes.append('numeric path "%s" disappeared (was %s)' % (path, bval))

# bodyText 对比
b_len = baseline.get("bodyTextLen", 0)
f_len = final.get("bodyTextLen", 0)
b_hash = baseline.get("bodyTextHash", "")
f_hash = final.get("bodyTextHash", "")
b_text = baseline.get("bodyText", "") or ""
f_text = final.get("bodyText", "") or ""
diff = f_len - b_len
sign = "+" if diff >= 0 else ""
signal_lines.append("  - bodyText length: %d -> %d chars  [%s%d]" % (b_len, f_len, sign, diff))
signal_lines.append("  - bodyText hash:   %s -> %s  [%s]" % (b_hash, f_hash, "CHANGED" if b_hash != f_hash else "unchanged"))

# 阈值降低到 20，且检查是否含"强信号关键词"（游戏结束 / game over / 分数 / score 等）
GAME_KEYWORDS = ["游戏结束", "game over", "gameover", "失败", "you win",
                 "分数", "得分", "再来", "play again", "restart"]
def text_has_new_keyword(b: str, f: str) -> bool:
    bl = b.lower()
    fl = f.lower()
    for kw in GAME_KEYWORDS:
        if kw in fl and kw not in bl:
            return True
    return False

if abs(diff) > 20:
    changes.append("DOM innerText length changed by %d (>20 threshold)" % diff)
elif text_has_new_keyword(b_text, f_text):
    changes.append("DOM innerText: new game-state keyword appeared (game over / score / win 等)")

signal_lines.append("  - JS errors during test: %d" % len(js_errors))
if js_errors:
    for e in js_errors[:5]:
        signal_lines.append("    * " + json.dumps(e, ensure_ascii=False))

# 判定
if changes:
    verdict = "PASS"
    reason = "PASS - at least one keypress-caused change detected (%d signal%s)" % (len(changes), "" if len(changes) == 1 else "s")
    exit_code = 0
elif js_errors:
    verdict = "FAIL"
    reason = "FAIL - no keypress-caused change AND %d JS error(s) observed" % len(js_errors)
    exit_code = 1
else:
    verdict = "FAIL"
    reason = "FAIL - 30 keypresses caused no detectable change (no canvas / no numeric / no DOM text shift)"
    exit_code = 1

lines.append("Result: " + verdict)
lines.append("HTML: " + html_path)
lines.append("Run dir: " + run_dir)
lines.append("")
lines.append("Signals after %d keypresses:" % len(keys_pressed))
lines.extend(signal_lines)
lines.append("")
if changes:
    lines.append("Detected changes (caused by keypress, not auto-animation):")
    for c in changes:
        lines.append("  - " + c)
else:
    lines.append("Detected changes: <none>")
lines.append("")
lines.append("Verdict reason: " + reason)

with open(verdict_path, "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")

print("[verdict] " + verdict + " - " + reason)
sys.exit(exit_code)
PY

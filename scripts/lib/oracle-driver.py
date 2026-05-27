# oracle-driver.py
# 在 browser-harness -c 上下文中执行。
# helpers (new_tab / wait_for_load / capture_screenshot / press_key / wait / js / ensure_real_tab)
# 已被 browser-harness 预导入。
#
# 通过环境变量接参：
#   ORACLE_HTML_PATH       — 待测 HTML 绝对路径（已拷到 run-dir）
#   ORACLE_BASELINE_PNG    — 基线截图输出路径
#   ORACLE_AFTER_PNG       — 按键后截图输出路径
#   ORACLE_RESULT_JSON     — result.json 输出路径
#
# 输出：写 result.json，包含 baseline / final / auto_changing_paths / keys_pressed / errors
#
# 任务 260522-evaluator-oracle-shared-core Step 3：
# 信号采集 / 错误 hook / 找开始按钮 / 关键词识别全部委托 shared/playability/。
# 老的 COLLECT_JS / FIND_START_BUTTON_JS / INSTALL_ERROR_HOOK_JS 已删，统一走 window.__PLAYABILITY__ / __PLAYABILITY_DRIVER__。

import os
import json
import sys
import traceback

HTML_PATH = os.environ["ORACLE_HTML_PATH"]
BASELINE_PNG = os.environ["ORACLE_BASELINE_PNG"]
AFTER_PNG = os.environ["ORACLE_AFTER_PNG"]
RESULT_JSON = os.environ["ORACLE_RESULT_JSON"]

# 加载共享 JS（项目根的相对路径：scripts/lib/ → ../../shared/playability/）
SHARED_DIR = os.path.abspath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "shared", "playability")
)
with open(os.path.join(SHARED_DIR, "playability-probe.js"), encoding="utf-8") as f:
    PROBE_JS = f.read()
with open(os.path.join(SHARED_DIR, "playability-driver.js"), encoding="utf-8") as f:
    DRIVER_JS = f.read()

errors = []


def parse_signals(raw):
    if not raw:
        return None
    try:
        return json.loads(raw)
    except Exception as e:
        errors.append({"type": "parse_signals", "msg": str(e), "raw_preview": str(raw)[:200]})
        return None


def safe_js(expr, label):
    try:
        return js(expr)  # noqa: F821 — provided by browser-harness
    except Exception as e:
        errors.append({"type": "js_call", "label": label, "msg": str(e)})
        return None


def collect_signals(label):
    """调用共享 probe.collect()，返回解析后的 dict 或 None。"""
    raw = safe_js("JSON.stringify(window.__PLAYABILITY__ ? window.__PLAYABILITY__.collect() : null)", label)
    if not raw or raw == "null":
        return None
    return parse_signals(raw)


def main():
    try:
        # 1) 打开 HTML（用 file://）
        url = "file://" + HTML_PATH
        new_tab(url)  # noqa: F821
        wait_for_load(timeout=15)  # noqa: F821

        # 2) 注入共享 probe + driver（IIFE 内部防重复，多次调用安全）
        safe_js(PROBE_JS, "inject_probe")
        safe_js(DRIVER_JS, "inject_driver")

        # 3) baseline 截图
        try:
            capture_screenshot(BASELINE_PNG)  # noqa: F821
        except Exception as e:
            errors.append({"type": "screenshot", "label": "baseline", "msg": str(e)})

        # 4) Pre-flight：找开始按钮并点（坐标 click + JS click() 兜底）
        start_btn_raw = safe_js(
            "(() => { const b = window.__PLAYABILITY_DRIVER__ ? window.__PLAYABILITY_DRIVER__.findStartButton() : null; return b ? JSON.stringify(b) : null; })()",
            "find_start_button"
        )
        start_btn_clicked = None
        if start_btn_raw and start_btn_raw != "null":
            try:
                btn = json.loads(start_btn_raw)
                click_at_xy(btn["x"], btn["y"])  # noqa: F821
                # JS 兜底
                safe_js("window.__PLAYABILITY_DRIVER__.clickByJS()", "click_start_fallback")
                start_btn_clicked = btn
                wait(0.5)  # noqa: F821
            except Exception as e:
                errors.append({"type": "click_start", "msg": str(e)})

        # 5) 自然变化采样：1 秒内不发任何按键
        nat_baseline = collect_signals("nat_baseline")
        wait(1.0)  # noqa: F821
        nat_after = collect_signals("nat_after")

        auto_changing_paths = []
        auto_changing_canvases = []
        if nat_baseline and nat_after:
            # 复用共享 probe 的 computeWhitelist
            wl_raw = safe_js(
                f"JSON.stringify(window.__PLAYABILITY__.computeWhitelist({json.dumps(nat_baseline)}, {json.dumps(nat_after)}))",
                "whitelist"
            )
            wl = parse_signals(wl_raw)
            if wl:
                auto_changing_paths = wl.get("autoPaths", [])
                auto_changing_canvases = wl.get("autoCanvases", [])

        # 6) 真正 baseline（以自然变化采样后的状态为准）
        baseline = collect_signals("baseline")

        # 7) 驱动：18 次探索按键 + 30 次 ArrowRight（确保蛇横穿棋盘撞墙触发 game over）
        explore = ['ArrowRight', 'ArrowDown', 'd', 's', 'ArrowLeft', 'ArrowUp', 'a', 'w'] * 3  # 24 个
        keys = explore[:18] + ['ArrowRight'] * 30  # 48 个，约 9.6 秒
        for k in keys:
            try:
                press_key(k)  # noqa: F821
                wait(0.2)  # noqa: F821
            except Exception as e:
                errors.append({"type": "press_key", "key": k, "msg": str(e)})

        # 8) Final
        try:
            capture_screenshot(AFTER_PNG)  # noqa: F821
        except Exception as e:
            errors.append({"type": "screenshot", "label": "after", "msg": str(e)})

        final = collect_signals("final")

        # 9) 收集 JS 运行期错误（共享 probe 已 hook）
        js_errors_raw = safe_js("JSON.stringify(window.__PLAYABILITY__ ? window.__PLAYABILITY__.getErrors() : [])", "read_errors")
        js_errors = []
        if js_errors_raw:
            try:
                js_errors = json.loads(js_errors_raw)
            except Exception as e:
                errors.append({"type": "parse_js_errors", "msg": str(e)})

        result = {
            "html_path": HTML_PATH,
            "url": url,
            "baseline": baseline,
            "final": final,
            "nat_baseline": nat_baseline,
            "nat_after": nat_after,
            "auto_changing_paths": auto_changing_paths,
            "auto_changing_canvases": auto_changing_canvases,
            "keys_pressed": keys,
            "start_button_clicked": start_btn_clicked,
            "js_errors": js_errors,
            "errors": errors,
        }

        with open(RESULT_JSON, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)

        print("[driver] result.json written:", RESULT_JSON)
    except Exception:
        # 任何顶层异常也要写最少一份 result.json，避免 verdict 阶段抓瞎
        tb = traceback.format_exc()
        sys.stderr.write(tb)
        try:
            with open(RESULT_JSON, "w", encoding="utf-8") as f:
                json.dump({
                    "html_path": HTML_PATH,
                    "url": "file://" + HTML_PATH,
                    "baseline": None,
                    "final": None,
                    "nat_baseline": None,
                    "nat_after": None,
                    "auto_changing_paths": [],
                    "auto_changing_canvases": [],
                    "keys_pressed": [],
                    "start_button_clicked": None,
                    "js_errors": [],
                    "errors": errors + [{"type": "driver_top_level", "msg": tb}],
                }, f, ensure_ascii=False, indent=2)
        except Exception:
            pass
        # driver 进程不退非 0：让 verdict 阶段照常出 FAIL（写不出 json 才是 tooling error）


main()

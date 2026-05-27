/**
 * Playability Driver — pre-flight 找开始按钮 + JS click 兜底（任务 260522-evaluator-oracle-shared-core）
 *
 * 暴露 window.__PLAYABILITY_DRIVER__：
 *   - findStartButton()   → {x, y, txt} | null      （可见元素中心坐标，给 click_at_xy）
 *   - clickByJS()         → '开始' 等 | null         （直接 element.click() 兜底，绕过坐标命中盲区）
 *
 * 不绑 specific 游戏类型，纯通用键盘游戏 / 点击启动游戏的 pre-flight。
 *
 * @see ../README.md
 */
(function () {
  'use strict';
  if (window.__PLAYABILITY_DRIVER__) return;

  const START_KEYWORDS = [
    '开始', '开始游戏', '点击开始', '开 始',
    'Start', 'Play', 'GO', 'START', 'PLAY',
    '再来一局', '再来一次', '再玩一次', '重新开始',
    'Retry', 'Restart', 'Replay'
  ];

  const SELECTOR_TAGS = ['button', 'a', '[role=button]', 'div', 'span'];

  function findStartButton() {
    const all = [].slice.call(document.querySelectorAll(SELECTOR_TAGS.join(',')));

    // 精确匹配优先
    for (let i = 0; i < START_KEYWORDS.length; i++) {
      const txt = START_KEYWORDS[i];
      const el = findExact(all, txt);
      if (el) {
        const center = visibleCenter(el);
        if (center) return { x: center.x, y: center.y, txt: txt };
      }
    }

    // 包含匹配兜底（短文本，避免误击长段文字）
    for (let i = 0; i < START_KEYWORDS.length; i++) {
      const txt = START_KEYWORDS[i];
      const el = findContains(all, txt);
      if (el) {
        const center = visibleCenter(el);
        if (center) return { x: center.x, y: center.y, txt: txt };
      }
    }

    return null;
  }

  function clickByJS() {
    const all = [].slice.call(document.querySelectorAll(SELECTOR_TAGS.join(',')));
    for (let i = 0; i < START_KEYWORDS.length; i++) {
      const txt = START_KEYWORDS[i];
      const el = findExact(all, txt) || findContains(all, txt);
      if (el && typeof el.click === 'function') {
        el.click();
        return txt;
      }
    }
    return null;
  }

  // ===== 助手 =====

  function findExact(list, txt) {
    for (let i = 0; i < list.length; i++) {
      if ((list[i].textContent || '').trim() === txt) return list[i];
    }
    return null;
  }

  function findContains(list, txt) {
    for (let i = 0; i < list.length; i++) {
      const t = (list[i].textContent || '').trim();
      if (t.length < 30 && t.indexOf(txt) !== -1) return list[i];
    }
    return null;
  }

  function visibleCenter(el) {
    if (!el || typeof el.getBoundingClientRect !== 'function') return null;
    const r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return null;
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
  }

  // ===== 暴露 =====

  window.__PLAYABILITY_DRIVER__ = {
    findStartButton: findStartButton,
    clickByJS: clickByJS,
    startKeywords: START_KEYWORDS.slice(),
    version: '1.0.0'
  };
})();

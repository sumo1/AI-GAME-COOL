/**
 * Game Runtime Probe — 游戏运行时探针
 * 
 * 注入到生成的 HTML 游戏中，自动采集结构化运行数据。
 * Playwright 模拟操作后，通过 page.evaluate(() => window.__GAME_PROBE__) 收割数据。
 * 
 * 采集内容：
 * - 交互事件（click/keydown/touch）
 * - DOM 变化（MutationObserver）
 * - 游戏状态变化（分数、关卡等元素的文本变化）
 * - JS 运行时错误
 * - 元素越界检测
 * - 响应延迟
 */
(function() {
    'use strict';

    const probe = {
        startTime: Date.now(),
        errors: [],
        events: [],
        stateChanges: [],
        outOfBoundsElements: [],
        domMutationsCount: 0,
        stateTransitions: [],
        finalState: {},
        responseLatencies: [],
        consoleWarnings: []
    };

    // === 1. 错误捕获 ===
    window.addEventListener('error', function(e) {
        probe.errors.push({
            msg: e.message || 'Unknown error',
            file: e.filename || '',
            line: e.lineno || 0,
            col: e.colno || 0,
            ts: Date.now() - probe.startTime
        });
    });

    window.addEventListener('unhandledrejection', function(e) {
        probe.errors.push({
            msg: 'Unhandled Promise: ' + (e.reason ? e.reason.message || String(e.reason) : 'unknown'),
            file: '',
            line: 0,
            col: 0,
            ts: Date.now() - probe.startTime
        });
    });

    // === 2. Console 警告捕获 ===
    var origWarn = console.warn;
    console.warn = function() {
        probe.consoleWarnings.push({
            msg: Array.from(arguments).join(' '),
            ts: Date.now() - probe.startTime
        });
        origWarn.apply(console, arguments);
    };

    // === 3. 交互事件追踪 ===
    function trackEvent(type, e) {
        var target = e.target;
        var tagName = target.tagName || '';
        var id = target.id || '';
        var className = (typeof target.className === 'string') ? target.className.split(' ')[0] : '';
        var text = (target.textContent || '').substring(0, 50).trim();
        var selector = tagName.toLowerCase();
        if (id) selector += '#' + id;
        else if (className) selector += '.' + className;

        // 记录点击前的状态快照
        var scoreBefore = getScoreValue();
        var lastEventTs = Date.now();

        probe.events.push({
            type: type,
            target: selector,
            text: text,
            ts: Date.now() - probe.startTime,
            scoreBefore: scoreBefore
        });
    }

    document.addEventListener('click', function(e) { trackEvent('click', e); }, true);
    document.addEventListener('keydown', function(e) {
        probe.events.push({
            type: 'keydown',
            key: e.key,
            ts: Date.now() - probe.startTime
        });
    }, true);

    // === 4. DOM 变化监听 ===
    var observer = new MutationObserver(function(mutations) {
        probe.domMutationsCount += mutations.length;
    });

    // 延迟启动 observer，等页面加载完
    setTimeout(function() {
        observer.observe(document.body, {
            childList: true,
            subtree: true,
            characterData: true,
            attributes: true
        });
    }, 500);

    // === 5. 状态快照（分数/关卡/进度追踪）===
    var lastScore = null;
    var lastStateText = '';

    function getScoreValue() {
        // 尝试多种常见的分数元素选择器
        var selectors = ['#score', '.score', '[data-score]', '#points', '.points'];
        for (var i = 0; i < selectors.length; i++) {
            var el = document.querySelector(selectors[i]);
            if (el) {
                var text = el.textContent.trim();
                var num = parseInt(text.replace(/[^\d]/g, ''), 10);
                if (!isNaN(num)) return num;
            }
        }
        return null;
    }

    function getGameStateText() {
        // 收集关键状态元素的文本
        var stateSelectors = ['#score', '.score', '#current', '.current', '#level', '.level',
                              '#question', '.question', '#feedback', '.feedback', '#result', '.result',
                              '#timer', '.timer', '#lives', '.lives', '#progress', '.progress'];
        var texts = [];
        for (var i = 0; i < stateSelectors.length; i++) {
            var el = document.querySelector(stateSelectors[i]);
            if (el && el.textContent.trim()) {
                texts.push(stateSelectors[i] + '=' + el.textContent.trim().substring(0, 100));
            }
        }
        return texts.join('|');
    }

    // 定期检测状态变化
    setInterval(function() {
        var currentScore = getScoreValue();
        var currentStateText = getGameStateText();

        if (currentScore !== null && currentScore !== lastScore) {
            probe.stateChanges.push({
                type: 'score_change',
                from: lastScore,
                to: currentScore,
                ts: Date.now() - probe.startTime
            });
            lastScore = currentScore;
        }

        if (currentStateText && currentStateText !== lastStateText) {
            // 推断状态转换
            var transition = inferTransition(lastStateText, currentStateText);
            if (transition) {
                probe.stateTransitions.push(transition);
            }
            lastStateText = currentStateText;
        }
    }, 500);

    function inferTransition(oldState, newState) {
        if (!oldState && newState) return 'idle → active';
        if (oldState && !newState) return 'active → ended';

        // 检测常见的状态词
        var oldWords = oldState.toLowerCase();
        var newWords = newState.toLowerCase();

        if (!oldWords.includes('score') && newWords.includes('score')) return 'start → playing';
        if (newWords.includes('result') || newWords.includes('结果') || newWords.includes('完成'))
            return 'playing → finished';
        if (newWords.includes('feedback') || newWords.includes('反馈'))
            return 'answered';

        return null;
    }

    // === 6. 越界检测 ===
    function checkOutOfBounds() {
        var vw = window.innerWidth;
        var vh = window.innerHeight;
        var oob = [];

        var allElements = document.querySelectorAll('button, div, span, p, h1, h2, h3, img, canvas, svg, input');
        allElements.forEach(function(el) {
            var rect = el.getBoundingClientRect();
            // 跳过不可见元素
            if (rect.width === 0 && rect.height === 0) return;
            var style = window.getComputedStyle(el);
            if (style.display === 'none' || style.visibility === 'hidden') return;

            if (rect.right > vw + 5 || rect.bottom > vh + 5 || rect.left < -5 || rect.top < -5) {
                var selector = el.tagName.toLowerCase();
                if (el.id) selector += '#' + el.id;
                else if (el.className && typeof el.className === 'string') selector += '.' + el.className.split(' ')[0];
                oob.push({
                    element: selector,
                    rect: { left: Math.round(rect.left), top: Math.round(rect.top), 
                            right: Math.round(rect.right), bottom: Math.round(rect.bottom) },
                    viewport: { width: vw, height: vh }
                });
            }
        });
        probe.outOfBoundsElements = oob;
    }

    // === 7. 最终状态收集 ===
    function collectFinalState() {
        probe.finalState = {
            score: getScoreValue(),
            stateText: getGameStateText(),
            totalEvents: probe.events.length,
            totalErrors: probe.errors.length,
            totalDomMutations: probe.domMutationsCount,
            totalStateChanges: probe.stateChanges.length,
            durationMs: Date.now() - probe.startTime
        };
        checkOutOfBounds();
    }

    // 页面卸载前收集最终状态
    window.addEventListener('beforeunload', collectFinalState);

    // 也提供手动调用接口（Playwright 收割前调用）
    probe.collectFinalState = collectFinalState;

    // 更新事件中的 DOM 变化标记
    document.addEventListener('click', function() {
        setTimeout(function() {
            var lastEvent = probe.events[probe.events.length - 1];
            if (lastEvent && lastEvent.type === 'click') {
                var scoreAfter = getScoreValue();
                lastEvent.scoreAfter = scoreAfter;
                lastEvent.domChanged = (probe.domMutationsCount > 0);

                // 记录响应延迟
                if (lastEvent.scoreBefore !== scoreAfter) {
                    probe.responseLatencies.push(Date.now() - probe.startTime - lastEvent.ts);
                }
            }
        }, 300);
    }, true);

    // 暴露到全局
    window.__GAME_PROBE__ = probe;
})();

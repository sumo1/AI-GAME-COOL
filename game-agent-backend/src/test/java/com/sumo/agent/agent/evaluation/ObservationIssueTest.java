package com.sumo.agent.agent.evaluation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ObservationIssue 单元测试 — 验证从 issue 文本到结构化分类的最小解析行为。
 */
class ObservationIssueTest {

    // 1. [评估] 前缀 → category=evaluation，无强关键词 → severity=minor
    @Test
    void fromIssueText_evaluationTagWithoutStrongKeyword_yieldsMinor() {
        ObservationIssue issue = ObservationIssue.fromIssueText("[评估] Playwright 超时");

        assertNotNull(issue, "非 null 输入不应返回 null");
        assertEquals("evaluation", issue.getCategory(), "[评估] 应映射到 evaluation");
        // "超时" 不在严重度关键词集合内，应回落到 minor（"错误/异常/白屏" 才是 critical）
        assertEquals("minor", issue.getSeverity(), "无 critical/major 关键词应为 minor");
        assertTrue(issue.getMessage().contains("Playwright 超时"), "message 应保留原文核心");
    }

    // 2. [可运行性] + 含 "无 JavaScript" 关键词 → critical
    @Test
    void fromIssueText_runnabilityNoJs_yieldsCritical() {
        ObservationIssue issue = ObservationIssue.fromIssueText("[可运行性] 未发现 JavaScript 代码");

        assertNotNull(issue);
        assertEquals("runnability", issue.getCategory());
        assertEquals("critical", issue.getSeverity(), "'无 JavaScript' 应触发 critical");
    }

    // 3. [交互] + 含 "未发现事件监听" 关键词 → major
    @Test
    void fromIssueText_interactivityNoListener_yieldsMajor() {
        ObservationIssue issue = ObservationIssue.fromIssueText("[交互] 未发现事件监听器");

        assertNotNull(issue);
        assertEquals("interactivity", issue.getCategory());
        assertEquals("major", issue.getSeverity(), "'未发现事件监听' 应触发 major");
    }

    // 4. [布局] + 含 "越界" 关键词 → major
    @Test
    void fromIssueText_layoutOutOfBounds_yieldsMajor() {
        ObservationIssue issue = ObservationIssue.fromIssueText("[布局] 元素越界，超出视口");

        assertNotNull(issue);
        assertEquals("layout", issue.getCategory());
        assertEquals("major", issue.getSeverity(), "'越界' 应触发 major");
    }

    // 5. 无前缀 → category=general，severity=minor
    @Test
    void fromIssueText_noPrefix_fallsBackToGeneral() {
        ObservationIssue issue = ObservationIssue.fromIssueText("简单 issue 无前缀");

        assertNotNull(issue);
        assertEquals("general", issue.getCategory(), "无前缀文本归 general");
        assertEquals("minor", issue.getSeverity());
        assertEquals("简单 issue 无前缀", issue.getMessage());
    }

    // 6. null 输入 → 返回 null（调用方过滤）
    @Test
    void fromIssueText_nullInput_returnsNull() {
        ObservationIssue issue = ObservationIssue.fromIssueText(null);

        assertNull(issue, "null 输入必须返回 null，不抛 NPE");
    }

    // 7. 空字符串 → category=general，message=""
    @Test
    void fromIssueText_emptyString_yieldsGeneralWithEmptyMessage() {
        ObservationIssue issue = ObservationIssue.fromIssueText("");

        assertNotNull(issue, "空字符串不应返回 null");
        assertEquals("general", issue.getCategory());
        assertEquals("minor", issue.getSeverity());
        assertEquals("", issue.getMessage());
    }
}

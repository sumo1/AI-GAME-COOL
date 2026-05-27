package com.sumo.agent.agent.evaluation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评估观察中的单条问题 — 从 ProbeReport.issues 文本解析而来。
 * <p>
 * 不做复杂 NLP，仅按前缀做最小分类，让 LLM 在下一轮上下文中能按 category/severity 取用。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ObservationIssue {

    /** 来源分类：evaluation / runnability / interactivity / layout / completeness / education / general */
    private String category;

    /** 严重度：critical / major / minor */
    private String severity;

    /** 问题原文 */
    private String message;

    /** 证据（保留扩展位，目前为 null） */
    private String evidence;

    /**
     * 从 issue 文本解析为结构化 ObservationIssue。
     * 输入格式形如 {@code [评估] xxx} / {@code [可运行性] yyy}，无前缀则归 general。
     *
     * @param issueText 原始 issue 文本
     * @return 解析结果；当 issueText 为 null 时返回 null（调用方负责过滤）
     */
    public static ObservationIssue fromIssueText(String issueText) {
        if (issueText == null) {
            return null;
        }

        String trimmed = issueText.trim();
        String category = "general";
        String message = trimmed;

        // 前缀解析：[XXX] message
        if (trimmed.startsWith("[")) {
            int end = trimmed.indexOf(']');
            if (end > 1) {
                String tag = trimmed.substring(1, end);
                category = mapCategory(tag);
                // 截掉 "[XXX] " 前缀（含可能的空格）
                message = trimmed.substring(end + 1).trim();
            }
        }

        String severity = inferSeverity(trimmed);
        return new ObservationIssue(category, severity, message, null);
    }

    /** 中文标签 → 标准化英文分类 */
    private static String mapCategory(String tag) {
        switch (tag) {
            case "评估":
                return "evaluation";
            case "可运行性":
                return "runnability";
            case "交互":
                return "interactivity";
            case "布局":
                return "layout";
            case "完整性":
                return "completeness";
            case "教育":
                return "education";
            default:
                return "general";
        }
    }

    /** 关键词 → severity */
    private static String inferSeverity(String text) {
        // critical：JS 缺失 / JS 错误 / 白屏 / 异常 / 无法运行
        if (text.contains("错误") || text.contains("异常") || text.contains("白屏")
                || text.contains("无 JavaScript") || text.contains("未发现 JavaScript")
                || text.contains("无法运行")) {
            return "critical";
        }
        // major：交互/布局/完整性方面的明确缺失
        if (text.contains("越界") || text.contains("未发现事件监听") || text.contains("缺少")) {
            return "major";
        }
        return "minor";
    }
}

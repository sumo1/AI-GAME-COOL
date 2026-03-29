package com.sumo.agent.agent.tools.generation;

/**
 * 异常分类器 — 区分网络异常 / LLM 空内容 / 业务逻辑错误
 */
public final class ErrorClassifier {

    private ErrorClassifier() {}

    public static String classify(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("timeout") || msg.contains("timed out")
                || e.getCause() instanceof java.net.SocketTimeoutException) {
            return "网络超时";
        }
        if (msg.contains("connection") || msg.contains("refused") || msg.contains("unreachable")) {
            return "网络连接异常";
        }
        if (msg.contains("rate limit") || msg.contains("429") || msg.contains("too many")) {
            return "API 限流";
        }
        if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized")) {
            return "认证失败";
        }
        if (msg.contains("500") || msg.contains("502") || msg.contains("503")) {
            return "服务端错误";
        }
        return "业务逻辑错误";
    }
}

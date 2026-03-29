package com.sumo.agent.agent.tools.generation;

/**
 * HTML 清洗工具 — 清理 LLM 输出的 HTML，确保结构完整
 */
public final class HtmlCleaner {

    private HtmlCleaner() {}

    public static String clean(String html) {
        if (html == null) return "";
        html = html.replaceAll("```html\\s*", "");
        html = html.replaceAll("```\\s*$", "");
        html = html.trim();

        if (!html.startsWith("<!DOCTYPE html>") && !html.startsWith("<html")) {
            html = "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n" + html;
        }
        if (!html.endsWith("</html>")) {
            html = html + "\n</html>";
        }
        if (!html.contains("charset")) {
            html = html.replace("<head>", "<head>\n    <meta charset=\"UTF-8\">");
        }
        return html;
    }
}

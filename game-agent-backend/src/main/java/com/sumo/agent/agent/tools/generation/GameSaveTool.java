package com.sumo.agent.agent.tools.generation;

import com.sumo.agent.agent.loop.WorkingMemory;
import com.sumo.agent.agent.tools.ToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 游戏保存工具 — 不调 LLM，只做清洗 + 存储。
 * <p>
 * 编排器 LLM 直接生成/修复 HTML 后，调用此工具保存到 WorkingMemory。
 * 替代了原来的 GameGenerationTool 和 GameFixTool。
 */
@Slf4j
@Component
public class GameSaveTool {

    @Autowired
    private ToolContext toolContext;

    @Tool(description = "保存生成或修复后的 HTML5 游戏代码。LLM 编写完整 HTML 后调用此工具保存。会自动清洗格式并存入工作记忆。")
    public String saveGame(
            @ToolParam(description = "完整的 HTML 游戏代码（从 <!DOCTYPE html> 到 </html>）") String htmlCode) {

        if (htmlCode == null || htmlCode.isBlank()) {
            return "错误：HTML 代码不能为空";
        }

        // 清洗 HTML（去除 markdown 代码块标记、补全结构）
        String cleanedHtml = HtmlCleaner.clean(htmlCode);

        WorkingMemory memory = toolContext.getWorkingMemory();
        if (memory == null) {
            log.error("[saveGame] WorkingMemory 为 null");
            return "错误：工作记忆未初始化";
        }

        // 判断是首次保存还是修复保存
        boolean isfix = memory.getGameHtml() != null;
        if (isfix) {
            int fixCount = toolContext.incrementAndGetFixCount();
            log.info("[saveGame] 修复保存第 {} 次, HTML 长度: {}", fixCount, cleanedHtml.length());
        } else {
            log.info("[saveGame] 首次保存, HTML 长度: {}", cleanedHtml.length());
        }

        // 更新 WorkingMemory
        memory.setGameHtml(cleanedHtml);
        memory.incrementGameVersion();

        log.info("[saveGame] 游戏已保存, 版本: {}, HTML 长度: {}",
                memory.getGameVersion(), cleanedHtml.length());

        return "游戏已保存（版本 " + memory.getGameVersion()
                + "，" + cleanedHtml.length() + " 字符"
                + (isfix ? "，第 " + toolContext.getFixCount() + " 次修复" : "")
                + "）。请调用 evaluateGame 评估质量。";
    }
}

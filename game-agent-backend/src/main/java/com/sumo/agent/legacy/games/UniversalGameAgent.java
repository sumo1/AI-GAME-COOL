/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.legacy.games;

import com.sumo.agent.legacy.core.AgentContext;
import com.sumo.agent.legacy.core.AgentPriority;
import com.sumo.agent.legacy.core.BaseAgent;
import com.sumo.agent.legacy.core.GameConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import com.sumo.agent.infra.model.ChatModelRegistry;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

import java.util.*;

/**
 * 通用游戏生成Agent - 使用AI动态生成任意类型的游戏
 */
@Slf4j
@Component("universalGameAgent")
@Deprecated
public class UniversalGameAgent extends BaseAgent {
    
    @Autowired(required = false)
    private ChatModel chatModel; // 兼容旧路径
    
    @Autowired(required = false)
    private ChatModelRegistry chatModelRouter;
    
    @Autowired(required = false)
    private Environment environment;
    
    @Override
    public void execute(AgentContext context) {
        GameConfig config = context.getGameConfig();
        String userInput = context.getUserInput();
        
        log.info("🎮 使用AI动态生成游戏: {}", userInput);
        
        // 选择模型（可由前端传入 model 选项，例如 dashscope/kimi-k2）
        ChatModel useModel = chatModel;
        if (chatModelRouter != null) {
            String modelKey = context.getAttribute("model");
            useModel = chatModelRouter.get(modelKey);
            
            // 在上下文记录模型名称，便于响应展示
            String modelName = resolveModelName(modelKey);
            context.setAttribute("modelName", modelName);
        }
        
        // 如果ChatModel不可用，生成默认游戏
        if (useModel == null) {
            log.warn("ChatModel未配置，生成默认游戏");
            generateDefaultGame(context, config, userInput);
            return;
        }
        
        try {
            // 单次调用：在系统提示中要求“先内部细化再生成”，最终仅输出HTML
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(userInput, config);
            SystemMessage systemMessage = new SystemMessage(systemPrompt);
            UserMessage userMessage = new UserMessage(userPrompt);
            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

            // 调试日志：输出组装后的完整提示词（System + User）
            // 注意：仅包含业务提示词，不含任何敏感凭证
            log.debug("================ Prompt Assembled (System) ================\n{}\n==========================================================", systemPrompt);
            log.debug("================ Prompt Assembled (User) ==================\n{}\n==========================================================", userPrompt);

            // 真正调用大模型
            String gameHtml = useModel.call(prompt).getResult().getOutput().getText();
            gameHtml = cleanAndValidateHtml(gameHtml);

            Map<String, Object> result = new HashMap<>();
            result.put("html", gameHtml);
            result.put("type", "universal");
            result.put("gameData", Map.of(
                "title", config.getTitle() != null ? config.getTitle() : "AI生成的游戏",
                "description", userInput,
                "generated", true
            ));
            result.put("generatedByLLM", true);
            result.put("modelName", context.getAttribute("modelName"));

            context.setResult(result);
            context.setSuccess(true);

        } catch (Exception e) {
            log.error("游戏生成失败", e);
            context.setSuccess(false);
            context.setResult("游戏生成失败：" + e.getMessage());
        }
    }
    
    private String buildSystemPrompt() {
        return """
            你是一个专业的儿童教育游戏开发专家。请根据用户输入生成一个完整的 HTML5 教育小游戏。
            
            一次完成两步（单次调用）：
            - 在内部思考并先细化需求：面向 4-8 岁儿童，明确主题/目标/玩法/控制方式/反馈与提示/评分与结束条件/可访问性/响应式等规范；
            - 然后依据该内部规范实现最终游戏。不要输出思考过程或中间结果，只输出最终 HTML。
            
            基本要求（必须同时满足）：
            1) 生成单个、可直接运行的完整 HTML 文件（<!DOCTYPE html>…</html>）。
            2) 所有样式与脚本均内联（<style>/<script>），不依赖任何外部资源或 CDN。
            3) 界面清晰、适合儿童，操作简单，同时支持键盘与可点击按钮（“开始/重新开始/方向等”，具体以主题为准）。
            4) 响应式：避免固定像素，优先百分比/视口单位/CSS 变量；确保游戏主区域在桌面端填充父容器≥90% 的宽高（如不足则做等比缩放适配），移动端占满宽度并保持纵横比。
            5) 游戏状态可见：分数/进度/提示需在页面中实时展示；违规或失败原因需清晰可见。
            
            交互与可用性规范（通用）：
            - 文案与控件一致：页面上应提供与说明一致的可视化按钮，且键盘操作同样可用。
            - 碰撞检测：使用轴对齐矩形相交（AABB）等稳健方式，而不是硬编码距离阈值。参考实现：
              在每一帧使用 getBoundingClientRect() 计算矩形，判定重叠：
              function overlap(a,b){ return !(a.right<b.left||a.left>b.right||a.bottom<b.top||a.top>b.bottom); }
            - 容器约束：将布局限制在游戏根容器（如 .game-area 或 #game-container），避免对 <body> 设置 display:flex/overflow:hidden 等全局布局；不要依赖固定像素，尽量使用百分比/视口单位或 CSS 变量。
            - 可访问性：按钮有可读文本或 aria-label；颜色对比度合理。
            
            主题选择：
            - 依据用户输入确定主题；如果用户未指定主题，选择一个通用且有教育意义的主题（例如数字认知、形状颜色、交通安全、环保等），但不要把任何特定主题写死在代码中。
            
            输出格式：
            - 只输出最终完整 HTML（从 <!DOCTYPE html> 到 </html>），不要包含 Markdown 代码块或解释文字。
            """;
    }
    
    private String buildUserPrompt(String userInput, GameConfig config) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请生成一个游戏，要求如下：\n");
        prompt.append("用户需求：").append(userInput).append("\n");
        
        if (config != null) {
            if (config.getAgeGroup() != null) {
                prompt.append("年龄组：").append(config.getAgeGroup()).append("\n");
            }
            if (config.getDifficulty() != null) {
                prompt.append("难度：").append(config.getDifficulty()).append("\n");
            }
            if (config.getTheme() != null) {
                prompt.append("主题：").append(config.getTheme()).append("\n");
            }
            if (config.getTitle() != null) {
                prompt.append("游戏标题：").append(config.getTitle()).append("\n");
            }
        }
        
        prompt.append("\n请确保游戏：\n");
        prompt.append("1. 完全符合用户的需求描述\n");
        prompt.append("2. 适合指定年龄段的儿童\n");
        prompt.append("3. 具有教育意义和趣味性\n");
        prompt.append("4. 界面美观，操作简单\n");
        
        return prompt.toString();
    }
    
    private String cleanAndValidateHtml(String html) {
        // 移除可能的markdown代码块标记
        html = html.replaceAll("```html\\s*", "");
        html = html.replaceAll("```\\s*$", "");
        html = html.trim();
        
        // 确保有完整的HTML结构
        if (!html.startsWith("<!DOCTYPE html>") && !html.startsWith("<html")) {
            html = "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n" + html;
        }
        
        if (!html.endsWith("</html>")) {
            html = html + "\n</html>";
        }
        
        // 确保有UTF-8编码声明
        if (!html.contains("charset")) {
            html = html.replace("<head>", "<head>\n    <meta charset=\"UTF-8\">");
        }
        
        return html;
    }
    
    @Override
    public String getName() {
        return "通用游戏生成Agent";
    }
    
    @Override
    public String getDescription() {
        return "使用AI动态生成任意类型的教育游戏";
    }
    
    @Override
    public AgentPriority getPriority() {
        return AgentPriority.LOW; // 作为后备选项，优先级最低
    }
    
    private void generateDefaultGame(AgentContext context, GameConfig config, String userInput) {
        String title = config.getTitle() != null ? config.getTitle() : "通用教育游戏";
        
        // 默认游戏模板
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>").append(title).append("</title>\n");
        html.append("    <style>\n");
        html.append("        body { font-family: Arial, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); margin: 0; padding: 20px; display: flex; justify-content: center; align-items: center; min-height: 100vh; }\n");
        html.append("        .container { background: white; border-radius: 20px; padding: 40px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); max-width: 600px; text-align: center; }\n");
        html.append("        h1 { color: #333; margin-bottom: 20px; }\n");
        html.append("        p { color: #666; line-height: 1.6; margin: 20px 0; }\n");
        html.append("        .game-area { background: #f8f9fa; border-radius: 10px; padding: 30px; margin: 20px 0; min-height: 200px; display: flex; align-items: center; justify-content: center; }\n");
        html.append("        button { background: #667eea; color: white; border: none; padding: 12px 24px; border-radius: 25px; font-size: 16px; cursor: pointer; margin: 10px; transition: transform 0.2s; }\n");
        html.append("        button:hover { transform: scale(1.05); }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <h1>🎮 ").append(title).append("</h1>\n");
        html.append("        <p>您的需求：").append(userInput).append("</p>\n");
        html.append("        <div class=\"game-area\">\n");
        html.append("            <div>\n");
        html.append("                <p>🚧 游戏正在开发中...</p>\n");
        html.append("                <p>这是一个基于您需求的游戏模板</p>\n");
        html.append("                <button onclick=\"alert('游戏功能开发中！')\">开始游戏</button>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        html.append("        <p style=\"font-size: 14px; color: #999;\">提示：配置AI服务后可自动生成完整游戏内容</p>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>");
        
        Map<String, Object> result = new HashMap<>();
        result.put("html", html.toString());
        result.put("type", "universal");
        result.put("gameData", Map.of(
            "title", title,
            "description", userInput,
            "generated", false
        ));
        result.put("generatedByLLM", false);
        
        context.setResult(result);
        context.setSuccess(true);
    }

    // 解析模型名称（带默认回退）
    private String resolveModelName(String modelKey) {
        if (modelKey == null || modelKey.isBlank() || "default".equalsIgnoreCase(modelKey) || "dashscope".equalsIgnoreCase(modelKey)) {
            if (environment != null) {
                String m = environment.getProperty("spring.ai.openai.chat.options.model");
                if (m != null && !m.isBlank()) return m;
            }
            return "dashscope-default";
        }
        if ("kimi-k2".equalsIgnoreCase(modelKey)) return "Moonshot-Kimi-K2-Instruct";
        if ("qwen3-coder-plus".equalsIgnoreCase(modelKey)) return "qwen3-coder-plus";
        if ("deepseek".equalsIgnoreCase(modelKey)) return "deepseek-v3.1";
        return modelKey;
    }
}

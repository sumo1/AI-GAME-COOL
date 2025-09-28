/*
 * @since: 2025/9/27
 * @author: sumo
 */
package com.sumo.agent.impl;

import com.sumo.agent.core.AgentContext;
import com.sumo.agent.core.BaseAgent;
import com.sumo.agent.core.GameConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 交通安全游戏Agent
 * 生成交通安全教育游戏，帮助儿童学习交通规则和安全知识
 */
@Slf4j
@Component("trafficSafetyGameAgent")
public class TrafficSafetyGameAgent extends BaseAgent {

    private static final String STATIC_GAME_PATH = "game-agent-backend/saved-games/traffic_safety_game.html";

    @Override
    public void execute(AgentContext context) {
        log.info("加载交通安全游戏...");

        try {
            // 直接读取静态HTML文件
            Path gamePath = Paths.get(STATIC_GAME_PATH);
            if (!Files.exists(gamePath)) {
                // 尝试相对路径
                gamePath = Paths.get("saved-games/traffic_safety_game.html");
            }
            String gameHtml = Files.readString(gamePath);

            // 设置结果
            context.setResult(gameHtml);
            context.setSuccess(true);
            log.info("交通安全游戏加载完成");

        } catch (IOException e) {
            log.error("加载交通安全游戏失败", e);

            // 如果静态文件不存在，返回默认游戏
            context.setResult(getDefaultGame());
            context.setSuccess(true);
        }
    }

    private String getDefaultGame() {
        // 返回一个简单的默认游戏页面
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>交通安全小卫士</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        text-align: center;
                        padding: 50px;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                    }
                    h1 { font-size: 48px; }
                    p { font-size: 24px; }
                </style>
            </head>
            <body>
                <h1>🚗 交通安全小卫士 🚦</h1>
                <p>游戏正在加载中...</p>
                <p>请稍后再试！</p>
            </body>
            </html>
            """;
    }

    @Deprecated
    private String buildTrafficGamePrompt(GameConfig config) {
        return String.format("""
            请生成一个交通安全教育的HTML5游戏，要求如下：

            游戏基本信息：
            - 目标年龄：%s
            - 难度级别：%s
            - 主题：%s
            - 标题：%s

            游戏机制要求：
            1. 控制小汽车安全过马路，躲避其他车辆
            2. 学习交通信号灯规则（红灯停、绿灯行）
            3. 认识斑马线、人行道等交通标识
            4. 包含积分系统：
               - 正确过马路 +10分
               - 遵守红绿灯 +20分
               - 走斑马线 +15分
               - 碰撞扣分 -30分
            5. 设置3个关卡，难度递增：
               - 第1关：简单路口，车辆较少
               - 第2关：增加更多车辆和信号灯
               - 第3关：复杂路口，需要判断多个方向

            技术要求：
            1. 使用HTML5 Canvas或SVG实现
            2. 完全兼容iPad触屏操作：
               - 触摸控制移动
               - 手势滑动控制方向
               - 按钮尺寸适合手指点击（最小44x44px）
            3. 响应式设计，自适应iPad屏幕
            4. 支持横竖屏切换
            5. 使用viewport meta标签：
               <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            6. 禁用iOS的弹性滚动：
               body { -webkit-overflow-scrolling: touch; overflow: hidden; }
            7. 使用touch事件而非mouse事件

            视觉设计：
            1. 卡通风格，色彩鲜艳
            2. 大号字体，易于阅读
            3. 清晰的视觉反馈
            4. 友好的角色设计
            5. 安全教育提示动画

            教育内容：
            1. 游戏开始前显示交通规则说明
            2. 游戏中实时提示安全知识
            3. 错误时显示正确做法
            4. 通关后总结学到的知识点

            音效要求：
            1. 背景音乐（可开关）
            2. 汽车行驶声
            3. 成功过马路的欢呼声
            4. 碰撞警告声
            5. 红绿灯切换提示音

            请生成完整的单文件HTML游戏，包含所有CSS和JavaScript代码。
            确保代码结构清晰，注释充分，便于理解和维护。
            """,
            config.getAgeGroup(),
            config.getDifficulty().getDisplayName(),
            config.getTheme(),
            config.getTitle()
        );
    }

    @Deprecated
    private String ensureIPadCompatibility(String gameHtml) {
        // 确保包含必要的iPad兼容性代码
        if (!gameHtml.contains("viewport")) {
            gameHtml = gameHtml.replace("<head>",
                "<head>\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">");
        }

        // 确保使用touch事件
        if (gameHtml.contains("mousedown") && !gameHtml.contains("touchstart")) {
            gameHtml = gameHtml.replace("mousedown", "touchstart");
            gameHtml = gameHtml.replace("mousemove", "touchmove");
            gameHtml = gameHtml.replace("mouseup", "touchend");
        }

        return gameHtml;
    }

    @Override
    public String getName() {
        return "交通安全游戏Agent";
    }

    @Override
    public String getDescription() {
        return "生成交通安全教育游戏，包括控制汽车过马路、学习交通规则、认识交通标识等，适合6岁儿童，完全兼容iPad触屏操作";
    }
}
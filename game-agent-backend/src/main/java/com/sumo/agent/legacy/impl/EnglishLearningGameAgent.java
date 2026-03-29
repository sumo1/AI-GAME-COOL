/*
 * @since: 2025/9/27
 * @author: sumo
 */
package com.sumo.agent.legacy.impl;

import com.sumo.agent.legacy.core.AgentContext;
import com.sumo.agent.legacy.core.BaseAgent;
import com.sumo.agent.legacy.core.GameConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 英语学习游戏Agent
 * 生成英语学习游戏，包括单词学习、字母认知、简单句子等
 */
@Slf4j
@Component("englishLearningGameAgent")
@Deprecated
public class EnglishLearningGameAgent extends BaseAgent {

    private static final String STATIC_GAME_PATH = "game-agent-backend/saved-games/english_learning_game.html";

    @Override
    public void execute(AgentContext context) {
        log.info("加载英语学习游戏...");

        try {
            // 直接读取静态HTML文件
            Path gamePath = Paths.get(STATIC_GAME_PATH);
            if (!Files.exists(gamePath)) {
                // 尝试相对路径
                gamePath = Paths.get("saved-games/english_learning_game.html");
            }
            String gameHtml = Files.readString(gamePath);

            // 设置结果
            context.setResult(gameHtml);
            context.setSuccess(true);
            log.info("英语学习游戏加载完成");

        } catch (IOException e) {
            log.error("加载英语学习游戏失败", e);

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
                <title>快乐学英语</title>
                <style>
                    body {
                        font-family: "Comic Sans MS", Arial, sans-serif;
                        text-align: center;
                        padding: 50px;
                        background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
                        color: white;
                    }
                    h1 { font-size: 48px; }
                    p { font-size: 24px; }
                </style>
            </head>
            <body>
                <h1>🌟 快乐学英语 🐾</h1>
                <p>游戏正在加载中...</p>
                <p>请稍后再试！</p>
            </body>
            </html>
            """;
    }

    @Deprecated
    private String buildEnglishGamePrompt(GameConfig config) {
        return String.format("""
            请生成一个英语学习HTML5游戏，要求如下：

            游戏基本信息：
            - 目标年龄：%s
            - 难度级别：%s
            - 主题：%s
            - 标题：%s

            游戏内容设计：
            1. 基础词汇学习（适合6岁儿童）：
               - 动物类：cat, dog, bird, fish, rabbit
               - 颜色类：red, blue, green, yellow, pink
               - 数字类：one to ten
               - 水果类：apple, banana, orange, grape
               - 日常用品：book, pen, ball, toy, cup

            2. 游戏模式（3个递进关卡）：

               【第1关：单词认知】
               - 显示图片，播放单词发音
               - 点击正确的单词卡片
               - 每个正确答案 +10分
               - 连续答对有连击奖励

               【第2关：听音选图】
               - 播放单词发音
               - 从4张图片中选择正确的
               - 每个正确答案 +15分
               - 限时模式增加挑战

               【第3关：单词拼写】
               - 显示图片和打乱的字母
               - 拖动字母拼出正确单词
               - 每个正确答案 +20分
               - 提供提示功能（扣5分）

            3. 积分系统：
               - 基础得分：答对加分
               - 连击奖励：连续答对3个 +30分
               - 速度奖励：10秒内答对 +5分
               - 完美通关：全部答对额外 +100分
               - 积分可解锁新的单词主题

            4. 成就系统：
               - 学习新手：完成第一关
               - 单词达人：累计学习50个单词
               - 完美学者：连续答对10题
               - 速度之星：30秒内完成一关

            技术要求：
            1. 完全兼容iPad触屏：
               - 所有交互使用touch事件
               - 按钮最小尺寸 60x60px
               - 拖放操作流畅自然
               - 支持多点触控

            2. 响应式布局：
               - viewport设置：width=device-width, initial-scale=1.0, maximum-scale=1.0
               - 自适应iPad Pro、iPad Air、iPad mini
               - 支持横竖屏自动切换
               - 使用flexbox或grid布局

            3. iOS优化：
               - 禁用弹性滚动：-webkit-overflow-scrolling: touch
               - 禁用文本选择：-webkit-user-select: none
               - 使用-webkit-tap-highlight-color: transparent
               - 阻止双击缩放

            视觉设计：
            1. 界面风格：
               - 卡通可爱风格，色彩明快
               - 大号圆角按钮，便于点击
               - 清晰的图标和插画
               - 动画过渡流畅

            2. 字体设置：
               - 英文使用 Comic Sans MS 或类似童趣字体
               - 中文使用黑体或圆体
               - 最小字号 18px

            3. 反馈动效：
               - 正确答案：星星爆炸效果
               - 错误答案：轻微震动提示
               - 得分动画：数字滚动上升
               - 通关动画：彩带庆祝效果

            音频设计：
            1. 必需音效：
               - 每个单词的标准美式发音
               - 正确/错误提示音
               - 背景音乐（可开关）
               - 按钮点击音
               - 成就解锁音

            2. 发音功能：
               - 点击单词可重复播放发音
               - 发音速度可调（慢速/正常）
               - 支持音节分解发音

            教育特色：
            1. 重复学习机制：错误的单词会重复出现
            2. 图文结合：每个单词配有生动插图
            3. 进度追踪：显示已学/未学单词
            4. 复习模式：定期复习已学内容

            特殊要求：
            1. 使用Web Audio API处理音频
            2. 使用localStorage保存进度和积分
            3. 支持离线游玩（缓存资源）
            4. 防止意外退出（beforeunload提示）

            请生成完整的单文件HTML游戏，包含所有CSS、JavaScript代码和内嵌的SVG图形。
            确保代码模块化，使用ES6语法，便于维护和扩展。
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
                """
                <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <meta name="apple-mobile-web-app-capable" content="yes">
                <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
                """);
        }

        // 添加iOS特定的CSS
        if (!gameHtml.contains("-webkit-tap-highlight-color")) {
            gameHtml = gameHtml.replace("<style>",
                """
                <style>
                * {
                    -webkit-tap-highlight-color: transparent;
                    -webkit-user-select: none;
                    -webkit-touch-callout: none;
                    user-select: none;
                }
                body {
                    -webkit-overflow-scrolling: touch;
                    overflow: hidden;
                    position: fixed;
                    width: 100%;
                    height: 100%;
                }
                """);
        }

        // 确保使用touch事件
        if (gameHtml.contains("addEventListener('click'") && !gameHtml.contains("addEventListener('touchstart'")) {
            // 为点击事件添加触摸支持
            gameHtml = gameHtml.replace("addEventListener('click'",
                "addEventListener('touchstart'");
        }

        // 转换mouse事件为touch事件
        gameHtml = gameHtml.replace("mousedown", "touchstart");
        gameHtml = gameHtml.replace("mousemove", "touchmove");
        gameHtml = gameHtml.replace("mouseup", "touchend");

        return gameHtml;
    }

    @Override
    public String getName() {
        return "英语学习游戏Agent";
    }

    @Override
    public String getDescription() {
        return "生成英语学习游戏，包括单词认知、听音选图、单词拼写等模式，带积分和成就系统，适合6岁儿童，完全兼容iPad";
    }
}
/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.analyzer;

import com.sumo.agent.core.GameConfig;
import com.sumo.agent.core.GameGeneratorAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户意图分析器
 * 分析用户输入，提取游戏生成参数
 */
@Slf4j
@Component
public class IntentAnalyzer {
    
    /**
     * 分析用户输入，提取游戏意图
     */
    public GameGeneratorAgent.GameIntent analyze(String userInput) {
        log.info("🔍 分析用户意图: {}", userInput);
        
        // 提取游戏类型
        GameConfig.GameType gameType = extractGameType(userInput);
        
        // 提取年龄组
        String ageGroup = extractAgeGroup(userInput);
        
        // 提取难度
        GameConfig.DifficultyLevel difficulty = extractDifficulty(userInput);
        
        // 提取主题
        String theme = extractTheme(userInput);
        
        // 生成标题
        String title = generateTitle(gameType, theme);
        
        // 是否需要计时
        boolean timerEnabled = userInput.contains("计时") || userInput.contains("时间");
        
        // 游戏时长
        int duration = extractDuration(userInput);
        
        return new GameGeneratorAgent.GameIntent(
            gameType,
            ageGroup,
            difficulty,
            theme,
            title,
            timerEnabled,
            duration
        );
    }
    
    /**
     * 提取游戏类型
     */
    private GameConfig.GameType extractGameType(String input) {
        if (containsAny(input, "数学", "加法", "减法", "乘法", "除法", "计算", "算术")) {
            return GameConfig.GameType.MATH;
        } else if (containsAny(input, "单词", "英语", "字母", "拼写", "词汇")) {
            return GameConfig.GameType.WORD;
        } else if (containsAny(input, "记忆", "记住", "配对", "记忆力")) {
            return GameConfig.GameType.MEMORY;
        } else if (containsAny(input, "拼图", "拼接", "组合")) {
            return GameConfig.GameType.PUZZLE;
        } else if (containsAny(input, "画", "绘画", "涂鸦", "创作")) {
            return GameConfig.GameType.DRAWING;
        }
        
        // 默认通用游戏，使用AI生成
        return GameConfig.GameType.UNIVERSAL;
    }
    
    /**
     * 提取年龄组
     */
    private String extractAgeGroup(String input) {
        // 匹配数字岁
        Pattern pattern = Pattern.compile("(\\d+)\\s*[-到至]?\\s*(\\d+)?\\s*岁");
        Matcher matcher = pattern.matcher(input);
        
        if (matcher.find()) {
            int age1 = Integer.parseInt(matcher.group(1));
            String age2Str = matcher.group(2);
            
            if (age2Str != null) {
                int age2 = Integer.parseInt(age2Str);
                return age1 + "-" + age2;
            } else {
                // 单个年龄，推断年龄组
                if (age1 <= 5) {
                    return "3-5";
                } else if (age1 <= 8) {
                    return "6-8";
                } else {
                    return "9-12";
                }
            }
        }
        
        // 默认年龄组
        return "6-8";
    }
    
    /**
     * 提取难度
     */
    private GameConfig.DifficultyLevel extractDifficulty(String input) {
        if (containsAny(input, "简单", "容易", "基础", "入门")) {
            return GameConfig.DifficultyLevel.EASY;
        } else if (containsAny(input, "中等", "普通", "一般")) {
            return GameConfig.DifficultyLevel.MEDIUM;
        } else if (containsAny(input, "困难", "难", "挑战", "高级")) {
            return GameConfig.DifficultyLevel.HARD;
        } else if (containsAny(input, "自适应", "递增", "渐进")) {
            return GameConfig.DifficultyLevel.ADAPTIVE;
        }
        
        return GameConfig.DifficultyLevel.EASY;
    }
    
    /**
     * 提取主题
     */
    private String extractTheme(String input) {
        if (containsAny(input, "动物", "小动物", "动物园")) {
            return "animals";
        } else if (containsAny(input, "太空", "宇宙", "星球", "火箭")) {
            return "space";
        } else if (containsAny(input, "童话", "公主", "王子", "魔法")) {
            return "fairy-tale";
        } else if (containsAny(input, "海洋", "海底", "鱼", "海")) {
            return "ocean";
        } else if (containsAny(input, "恐龙", "侏罗纪")) {
            return "dinosaur";
        } else if (containsAny(input, "超级英雄", "英雄", "超人")) {
            return "superhero";
        }
        
        return "adventure";
    }
    
    /**
     * 生成游戏标题
     */
    private String generateTitle(GameConfig.GameType gameType, String theme) {
        String themeTitle = getThemeTitle(theme);
        
        return switch (gameType) {
            case MATH -> themeTitle + "数学冒险";
            case WORD -> themeTitle + "单词探索";
            case MEMORY -> themeTitle + "记忆大师";
            case PUZZLE -> themeTitle + "拼图世界";
            case DRAWING -> themeTitle + "创意画板";
            case UNIVERSAL -> themeTitle + "游戏世界";
        };
    }
    
    /**
     * 获取主题标题
     */
    private String getThemeTitle(String theme) {
        return switch (theme) {
            case "animals" -> "小动物";
            case "space" -> "太空";
            case "fairy-tale" -> "童话";
            case "ocean" -> "海洋";
            case "dinosaur" -> "恐龙";
            case "superhero" -> "超级英雄";
            default -> "奇妙";
        };
    }
    
    /**
     * 提取游戏时长
     */
    private int extractDuration(String input) {
        Pattern pattern = Pattern.compile("(\\d+)\\s*分钟");
        Matcher matcher = pattern.matcher(input);
        
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        
        return 10; // 默认10分钟
    }
    
    /**
     * 检查字符串是否包含任意关键词
     */
    private boolean containsAny(String input, String... keywords) {
        for (String keyword : keywords) {
            if (input.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
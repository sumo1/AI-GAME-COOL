/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.core;

import lombok.Builder;
import lombok.Data;

/**
 * 游戏配置类
 */
@Data
@Builder
public class GameConfig {
    
    /**
     * 游戏类型
     */
    private GameType gameType;
    
    /**
     * 年龄组
     */
    private String ageGroup;
    
    /**
     * 难度级别
     */
    private DifficultyLevel difficulty;
    
    /**
     * 游戏主题
     */
    private String theme;
    
    /**
     * 游戏标题
     */
    private String title;
    
    /**
     * 是否需要计时
     */
    private boolean timerEnabled;
    
    /**
     * 是否有音效
     */
    private boolean soundEnabled;
    
    /**
     * 游戏时长（分钟）
     */
    private int duration;
    
    /**
     * 积分系统
     */
    private boolean scoreEnabled;
    
    /**
     * 游戏类型枚举
     */
    public enum GameType {
        MATH("数学游戏"),
        WORD("单词游戏"),
        MEMORY("记忆游戏"),
        PUZZLE("拼图游戏"),
        DRAWING("绘画游戏"),
        TRAFFIC_SAFETY("交通安全游戏"),
        ENGLISH_LEARNING("英语学习游戏"),
        UNIVERSAL("通用游戏");
        
        private final String displayName;
        
        GameType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * 难度级别枚举
     */
    public enum DifficultyLevel {
        EASY("简单"),
        MEDIUM("中等"),
        HARD("困难"),
        ADAPTIVE("自适应");
        
        private final String displayName;
        
        DifficultyLevel(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}
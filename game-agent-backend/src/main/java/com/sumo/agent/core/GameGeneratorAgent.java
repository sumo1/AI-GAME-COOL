/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.core;

import com.sumo.agent.analyzer.IntentAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏生成主Agent
 * 负责解析用户意图并调度合适的子Agent
 */
@Slf4j
@Service
public class GameGeneratorAgent {
    
    @Autowired
    private IntentAnalyzer intentAnalyzer;
    
    /**
     * 注册的子Agent
     */
    private final Map<String, BaseAgent> agentRegistry = new ConcurrentHashMap<>();
    
    /**
     * 注册子Agent
     */
    public void registerAgent(String name, BaseAgent agent) {
        agentRegistry.put(name, agent);
        log.info("📝 注册Agent: {}", name);
    }
    
    /**
     * 执行游戏生成流程
     */
    public GameGenerationResult generateGame(String userInput, String sessionId, Map<String, Object> options) {
        log.info("🎮 开始生成游戏，用户输入: {}", userInput);
        
        // 创建上下文
        AgentContext context = new AgentContext();
        context.setSessionId(sessionId);
        context.setUserInput(userInput);
        if (options != null) {
            options.forEach(context::setAttribute);
        }
        
        try {
            // 1. 分析用户意图
            GameIntent intent = intentAnalyzer.analyze(userInput);
            log.info("🔍 识别意图: {}", intent);
            
            // 2. 构建游戏配置
            GameConfig config = buildGameConfig(intent);
            context.setGameConfig(config);
            
            // 3. 选择合适的Agent
            BaseAgent selectedAgent = selectAgent(config.getGameType());
            if (selectedAgent == null) {
                throw new RuntimeException("未找到合适的游戏生成Agent");
            }
            
            log.info("👉 选择Agent: {}", selectedAgent.getName());
            
            // 4. 执行Agent
            selectedAgent.run(context);
            
            // 5. 返回结果
            if (context.isSuccess()) {
                return GameGenerationResult.success(
                    context.getResult(),
                    config,
                    selectedAgent.getName()
                );
            } else {
                return GameGenerationResult.failure(context.getError());
            }
            
        } catch (Exception e) {
            log.error("❌ 游戏生成失败", e);
            return GameGenerationResult.failure(e.getMessage());
        }
    }
    
    /**
     * 根据意图构建游戏配置
     */
    private GameConfig buildGameConfig(GameIntent intent) {
        return GameConfig.builder()
            .gameType(intent.gameType())
            .ageGroup(intent.ageGroup())
            .difficulty(intent.difficulty())
            .theme(intent.theme())
            .title(intent.title())
            .timerEnabled(intent.timerEnabled())
            .soundEnabled(true)
            .duration(intent.duration())
            .scoreEnabled(true)
            .build();
    }
    
    /**
     * 选择合适的Agent
     */
    private BaseAgent selectAgent(GameConfig.GameType gameType) {
        // 如果是通用类型，直接使用UniversalGameAgent
        if (gameType == GameConfig.GameType.UNIVERSAL) {
            BaseAgent agent = agentRegistry.get("universalGameAgent");
            if (agent != null) {
                log.info("使用通用游戏生成Agent处理用户自定义游戏");
            }
            return agent;
        }
        
        // 根据游戏类型选择Agent
        String agentName = gameType.name().toLowerCase() + "GameAgent";
        // 兼容旧的命名方式
        BaseAgent agent = agentRegistry.get(agentName);
        if (agent == null) {
            agentName = gameType.name().toLowerCase() + "Agent";
            agent = agentRegistry.get(agentName);
        }
        
        // 如果没有找到特定的Agent，使用通用的UniversalGameAgent
        if (agent == null) {
            agent = agentRegistry.get("universalGameAgent");
            if (agent != null) {
                log.info("使用通用游戏生成Agent处理类型: {}", gameType);
            }
        }
        
        return agent;
    }
    
    /**
     * 获取所有注册的Agent
     */
    public List<AgentInfo> getRegisteredAgents() {
        return agentRegistry.entrySet().stream()
            .map(entry -> new AgentInfo(
                entry.getKey(),
                entry.getValue().getName(),
                entry.getValue().getDescription(),
                entry.getValue().getPriority()
            ))
            .toList();
    }
    
    /**
     * Agent信息
     */
    public record AgentInfo(
        String key,
        String name,
        String description,
        AgentPriority priority
    ) {}
    
    /**
     * 游戏生成结果
     */
    public record GameGenerationResult(
        boolean success,
        Object gameData,
        GameConfig config,
        String agentName,
        String error
    ) {
        public static GameGenerationResult success(Object gameData, GameConfig config, String agentName) {
            return new GameGenerationResult(true, gameData, config, agentName, null);
        }
        
        public static GameGenerationResult failure(String error) {
            return new GameGenerationResult(false, null, null, null, error);
        }
    }
    
    /**
     * 游戏意图
     */
public record GameIntent(
        GameConfig.GameType gameType,
        String ageGroup,
        GameConfig.DifficultyLevel difficulty,
        String theme,
        String title,
        boolean timerEnabled,
        int duration
    ) {}
}

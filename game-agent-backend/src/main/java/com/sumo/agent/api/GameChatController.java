/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.api;

import com.sumo.agent.legacy.core.GameGeneratorAgent;
import com.sumo.agent.agent.loop.AgentLoop;
import com.sumo.agent.agent.loop.AgentLoopResult;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 游戏聊天控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "*")
public class GameChatController {

    private static final String DEFAULT_MODEL_KEY = "dashscope";

    private static final Map<String, String> MODEL_DISPLAY_NAMES = Map.of(
            DEFAULT_MODEL_KEY, "通义千问（DashScope）",
            "kimi-k2", "Moonshot-Kimi-K2-Instruct（百炼）",
            "qwen3-coder-plus", "Qwen3 Coder Plus（百炼）",
            "deepseek", "DeepSeek（百炼）"
    );
    
    @Autowired
    private GameGeneratorAgent gameGeneratorAgent;

    @Autowired
    private AgentLoop agentLoop;
    
    /**
     * 生成游戏
     */
    @PostMapping("/generate")
    public Mono<GameResponse> generateGame(@RequestBody GameRequest request) {
        log.info("📨 收到游戏生成请求: {}", request.getUserInput());
        
        String sessionId = request.getSessionId();
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        
        final String finalSessionId = sessionId;
        
        return Mono.fromCallable(() -> {
            GameGeneratorAgent.GameGenerationResult result = 
                gameGeneratorAgent.generateGame(request.getUserInput(), finalSessionId, request.getOptions());
            
            // 构造响应对象
            GameResponse response = new GameResponse();
            response.setSessionId(finalSessionId);
            response.setSuccess(result.success());
            
            if (result.success()) {
                // 业务数据
                response.setGameData(result.gameData());
                response.setConfig(result.config());
                response.setAgentName(result.agentName());

                // 提取Agent来源与模型名（用于前端卡片展示）
                String agentSource = "system";
                String modelName = null;
                boolean generatedByLLM = false;

                if (result.gameData() instanceof Map<?, ?> m) {
                    Object genFlag = m.get("generatedByLLM");
                    if (genFlag instanceof Boolean b) {
                        generatedByLLM = b;
                    } else {
                        // 回退：从嵌套的 gameData.generated 识别（通用Agent）
                        Object inner = m.get("gameData");
                        if (inner instanceof Map<?, ?> innerMap) {
                            Object g = innerMap.get("generated");
                            generatedByLLM = Boolean.TRUE.equals(g);
                        }
                    }
                    Object mn = m.get("modelName");
                    if (mn instanceof String s && !s.isBlank()) {
                        modelName = s;
                    }
                }

                agentSource = generatedByLLM ? "llm" : "system";

                response.setGeneratedByLLM(generatedByLLM);
                response.setAgentSource(agentSource);
                response.setModelName(modelName);
                response.setMessage("游戏生成成功！");
            } else {
                response.setError(result.error());
                response.setMessage("游戏生成失败: " + result.error());
            }
            
            return response;
        });
    }
    
    /**
     * V2 游戏生成 — 基于 AgentLoop 多轮迭代
     */
    @PostMapping("/v2/generate")
    public Mono<GameResponse> generateGameV2(@RequestBody GameRequest request) {
        log.info("📨 [V2] 收到游戏生成请求: {}", request.getUserInput());

        String sessionId = request.getSessionId();
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        final String finalSessionId = sessionId;

        final String finalModelKey = extractModelKey(request);

        return Mono.fromCallable(() -> {
            AgentLoopResult result = agentLoop.run(request.getUserInput(), finalModelKey);

            GameResponse response = new GameResponse();
            response.setSessionId(finalSessionId);
            response.setSuccess(result.success());

            if (result.success()) {
                Map<String, Object> gameMeta = buildV2GameMeta(request.getUserInput(), result);
                Map<String, Object> gameData = new HashMap<>();
                gameData.put("html", result.gameHtml());
                gameData.put("type", "agent_loop");
                gameData.put("generatedByLLM", true);
                gameData.put("gameData", gameMeta);

                response.setGameData(gameData);
                response.setConfig(buildV2Config(gameMeta));
                response.setAgentName("AgentLoop v2");
                response.setAgentSource("llm");
                response.setGeneratedByLLM(true);
                response.setModelName(resolveModelName(finalModelKey));
                response.setMessage(result.llmMessage() != null
                        ? result.llmMessage()
                        : "游戏生成成功！(迭代 " + result.iterations() + " 次)");
            } else {
                response.setError(result.error());
                response.setMessage("游戏生成失败: " + result.error());
            }

            return response;
        });
    }

    private String extractModelKey(GameRequest request) {
        if (request.getOptions() == null) {
            return null;
        }

        Object model = request.getOptions().get("model");
        if (model instanceof String modelKey && !modelKey.isBlank()) {
            return modelKey;
        }

        return null;
    }

    private Map<String, Object> buildV2GameMeta(String userInput, AgentLoopResult result) {
        Map<String, Object> gameMeta = new HashMap<>();
        gameMeta.put("title", "AI 生成的游戏");
        gameMeta.put("description", userInput);
        gameMeta.put("type", "AI 生成");
        gameMeta.put("generated", true);
        gameMeta.put("iterations", result.iterations());
        gameMeta.put("evalScore", result.evalScore());
        return gameMeta;
    }

    private Map<String, Object> buildV2Config(Map<String, Object> gameMeta) {
        Map<String, Object> config = new HashMap<>();
        config.put("gameType", gameMeta.get("type"));
        return config;
    }

    private String resolveModelName(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return MODEL_DISPLAY_NAMES.get(DEFAULT_MODEL_KEY);
        }

        return MODEL_DISPLAY_NAMES.getOrDefault(modelKey, MODEL_DISPLAY_NAMES.get(DEFAULT_MODEL_KEY));
    }

    /**
     * SSE流式生成游戏
     */
    @GetMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<GameEvent> generateGameStream(@RequestParam String userInput,
                                               @RequestParam(required = false) String sessionId) {
        log.info("📨 收到流式游戏生成请求: {}", userInput);
        
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        
        final String finalSessionId = sessionId;
        
        return Flux.interval(Duration.ofSeconds(1))
            .take(5)
            .map(i -> {
                GameEvent event = new GameEvent();
                event.setSessionId(finalSessionId);
                
                switch (i.intValue()) {
                    case 0:
                        event.setType("analyzing");
                        event.setMessage("正在分析您的需求...");
                        break;
                    case 1:
                        event.setType("configuring");
                        event.setMessage("正在配置游戏参数...");
                        break;
                    case 2:
                        event.setType("generating");
                        event.setMessage("正在生成游戏内容...");
                        break;
                    case 3:
                        event.setType("rendering");
                        event.setMessage("正在渲染游戏界面...");
                        break;
                    case 4:
                        event.setType("completed");
                        event.setMessage("游戏生成完成！");
                        // 这里应该包含实际的游戏数据
                        Map<String, Object> gameData = new HashMap<>();
                        gameData.put("html", "<div>游戏HTML内容</div>");
                        event.setData(gameData);
                        break;
                }
                
                return event;
            });
    }
    
    /**
     * 获取注册的Agent列表
     */
    @GetMapping("/agents")
    public Mono<Map<String, Object>> getAgents() {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new HashMap<>();
            response.put("agents", gameGeneratorAgent.getRegisteredAgents());
            response.put("total", gameGeneratorAgent.getRegisteredAgents().size());
            return response;
        });
    }
    
    /**
     * 游戏生成请求
     */
    @Data
    public static class GameRequest {
        private String userInput;
        private String sessionId;
        private Map<String, Object> options;
    }
    
    /**
     * 游戏生成响应
     */
    @Data
    public static class GameResponse {
        private String sessionId;
        private boolean success;
        private String message;
        private Object gameData;
        private Object config;
        private String agentName;
        private String agentSource;   // system / llm
        private String modelName;     // 若为llm，包含模型名
        private Boolean generatedByLLM;
        private String error;
    }
    
    /**
     * SSE事件
     */
    @Data
    public static class GameEvent {
        private String sessionId;
        private String type;
        private String message;
        private Object data;
    }
}

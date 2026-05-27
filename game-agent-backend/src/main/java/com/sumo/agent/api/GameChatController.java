/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.api;

import com.sumo.agent.legacy.core.GameGeneratorAgent;
import com.sumo.agent.agent.loop.AgentLoop;
import com.sumo.agent.agent.loop.AgentLoopResult;
import com.sumo.agent.infra.db.SessionEntity;
import com.sumo.agent.infra.storage.SessionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private static final String DEFAULT_MODEL_KEY = "qwen3.6-max-preview";

    /** Mock 演示模式 key：选中后绕过 LLM，直接返回 classpath 中的 fixture HTML */
    private static final String MOCK_FIXTURE_KEY = "mock-fixture";
    private static final String MOCK_FIXTURE_PATH = "mock-fixtures/snake-fixed.html";

    private static final Map<String, String> MODEL_DISPLAY_NAMES = Map.ofEntries(
            // 当前下拉框 5 个
            Map.entry("qwen3.6-max-preview", "Qwen3.6 Max Preview"),
            Map.entry("qwen3.7-max",         "Qwen3.7 Max"),
            Map.entry("kimi-k2.6",            "Kimi K2.6"),
            Map.entry("MiniMax-M2.5",         "MiniMax M2.5"),
            Map.entry("deepseek-v4-pro",     "DeepSeek V4 Pro"),
            // Mock 演示模式
            Map.entry(MOCK_FIXTURE_KEY,      "Mock 演示（不调 LLM）"),
            // 兼容旧 key
            Map.entry("dashscope",            "通义千问（DashScope）"),
            Map.entry("kimi-k2",              "Moonshot-Kimi-K2-Instruct（百炼）"),
            Map.entry("qwen3-coder-plus",     "Qwen3 Coder Plus（百炼）"),
            Map.entry("deepseek",             "DeepSeek（百炼）")
    );
    
    @Autowired
    private GameGeneratorAgent gameGeneratorAgent;

    @Autowired
    private AgentLoop agentLoop;

    @Autowired
    private SessionService sessionService;
    
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
     * V2 游戏生成 — 基于 AgentLoop 多轮迭代 + 会话持久化
     *
     * 顺序：ensureSession → agentLoop.run → recordRun
     * 写库失败不影响响应：catch 后 log.error，用户仍拿到生成的 HTML（容错优先）
     */
    @PostMapping("/v2/generate")
    public Mono<GameResponse> generateGameV2(@RequestBody GameRequest request) {
        log.info("📨 [V2] 收到游戏生成请求: {}", request.getUserInput());

        final String requestedSessionId = request.getSessionId();
        final String finalModelKey = extractModelKey(request);

        // Mock 演示旁路：不进 AgentLoop / LLM，直接返回 fixture HTML
        if (MOCK_FIXTURE_KEY.equalsIgnoreCase(finalModelKey)) {
            return Mono.fromCallable(() -> buildMockFixtureResponse(requestedSessionId, request.getUserInput()));
        }

        return Mono.fromCallable(() -> {
            // 1. 先 ensureSession（写库失败也要让请求继续走 AgentLoop —— 容错）
            String sessionId = null;
            try {
                SessionEntity session = sessionService.ensureSession(
                        requestedSessionId, request.getUserInput(), finalModelKey);
                sessionId = session.getId();
            } catch (Exception e) {
                log.error("写入会话失败（ensureSession），降级为本次请求 UUID: {}", e.getMessage(), e);
                sessionId = (requestedSessionId != null && !requestedSessionId.isBlank())
                        ? requestedSessionId
                        : UUID.randomUUID().toString();
            }
            final String finalSessionId = sessionId;

            // 2. 跑 AgentLoop（不进 synchronized 块，让多请求并行）
            AgentLoopResult result = agentLoop.run(request.getUserInput(), finalModelKey);

            // 3. 写 messages + game_run（失败也不影响响应）
            String gameRunId = null;
            try {
                SessionService.RecordResult recordResult =
                        sessionService.recordRun(finalSessionId, request.getUserInput(), result, finalModelKey);
                gameRunId = recordResult != null ? recordResult.gameRunId() : null;
            } catch (Exception e) {
                log.error("写入会话失败（recordRun, sessionId={}）: {}", finalSessionId, e.getMessage(), e);
            }

            // 4. 写 evidence（任务 260524 Step 4）；失败同样不影响响应
            try {
                sessionService.recordEvidence(finalSessionId, gameRunId, finalModelKey, result);
            } catch (Exception e) {
                log.error("写入 evidence 失败（不影响响应, sessionId={}）: {}", finalSessionId, e.getMessage(), e);
            }

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

    private GameResponse buildMockFixtureResponse(String requestedSessionId, String userInput) {
        String sessionId = (requestedSessionId != null && !requestedSessionId.isBlank())
                ? requestedSessionId
                : UUID.randomUUID().toString();

        GameResponse response = new GameResponse();
        response.setSessionId(sessionId);

        try {
            ClassPathResource resource = new ClassPathResource(MOCK_FIXTURE_PATH);
            String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            Map<String, Object> gameMeta = new HashMap<>();
            gameMeta.put("title", "Mock 贪吃蛇（演示）");
            gameMeta.put("description", userInput != null ? userInput : "Mock 演示");
            gameMeta.put("type", "mock");
            gameMeta.put("generated", false);
            gameMeta.put("iterations", 0);
            gameMeta.put("evalScore", 0);

            Map<String, Object> gameData = new HashMap<>();
            gameData.put("html", html);
            gameData.put("type", "mock_fixture");
            gameData.put("generatedByLLM", false);
            gameData.put("gameData", gameMeta);

            Map<String, Object> config = new HashMap<>();
            config.put("gameType", "mock");

            response.setSuccess(true);
            response.setGameData(gameData);
            response.setConfig(config);
            response.setAgentName("Mock Fixture");
            response.setAgentSource("system");
            response.setGeneratedByLLM(false);
            response.setModelName(MODEL_DISPLAY_NAMES.get(MOCK_FIXTURE_KEY));
            response.setMessage("已加载 Mock 演示游戏（未调用 LLM）");
        } catch (IOException e) {
            log.error("加载 mock fixture 失败: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setError("加载 mock fixture 失败: " + e.getMessage());
            response.setMessage("Mock 演示加载失败");
        }

        return response;
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

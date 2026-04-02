package com.sumo.agent.infra.model;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云百炼（DashScope）配置类 — 通过 OpenAI 兼容 API 访问
 * <p>
 * spring-ai-alibaba 暂不兼容 Spring AI 2.0 / Spring Boot 4.x，
 * 改用 DashScope 的 OpenAI 兼容端点：https://dashscope.aliyuncs.com/compatible-mode/v1
 */
@Slf4j
@Configuration
public class DashScopeConfig {

    private static final String DASHSCOPE_OPENAI_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode";

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus-2025-07-28}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double temperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:4000}")
    private Integer maxTokens;

    @Autowired(required = false)
    private RestClient.Builder restClientBuilder;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Bean
    @Primary
    public ChatModel dashScopeChatModel() {
        log.info("配置阿里云百炼 ChatModel（OpenAI 兼容模式）- 模型: {}", model);

        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .baseUrl(DASHSCOPE_OPENAI_BASE_URL)
                .apiKey(apiKey);

        if (restClientBuilder != null) {
            apiBuilder.restClientBuilder(restClientBuilder);
        }

        OpenAiApi openAiApi = apiBuilder.build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        OpenAiChatModel.Builder modelBuilder = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options);

        if (observationRegistry != null) {
            modelBuilder.observationRegistry(observationRegistry);
        }

        OpenAiChatModel chatModel = modelBuilder.build();
        log.info("阿里云百炼 ChatModel 配置完成（OpenAI 兼容模式）");
        return chatModel;
    }
}

package com.sumo.agent.infra.model;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import io.micrometer.observation.ObservationRegistry;

/**
 * kimi-k2.6 模型（阿里云百炼 OpenAI 兼容模式）
 */
@Configuration
public class KimiK26Config {

    private static final String DASHSCOPE_OPENAI_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode";

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Autowired(required = false)
    private RestClient.Builder restClientBuilder;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Bean("kimiK26ChatModel")
    public ChatModel kimiK26ChatModel() {
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .baseUrl(DASHSCOPE_OPENAI_BASE_URL)
                .apiKey(apiKey);
        if (restClientBuilder != null) apiBuilder.restClientBuilder(restClientBuilder);
        OpenAiApi openAiApi = apiBuilder.build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("kimi-k2.6")
                .temperature(0.7)
                .maxTokens(16000)
                .build();

        OpenAiChatModel.Builder modelBuilder = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options);
        if (observationRegistry != null) modelBuilder.observationRegistry(observationRegistry);
        return modelBuilder.build();
    }
}

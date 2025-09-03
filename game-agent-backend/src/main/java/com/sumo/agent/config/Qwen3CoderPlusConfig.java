package com.sumo.agent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import io.micrometer.observation.ObservationRegistry;

/**
 * Qwen3 Coder Plus 模型（阿里云百炼）
 */
@Configuration
public class Qwen3CoderPlusConfig {

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Autowired(required = false)
    private RestClient.Builder restClientBuilder;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Bean("qwen3CoderPlusChatModel")
    public ChatModel qwen3CoderPlusChatModel() {
        DashScopeApi.Builder apiBuilder = DashScopeApi.builder().apiKey(apiKey);
        if (restClientBuilder != null) {
            apiBuilder.restClientBuilder(restClientBuilder);
        }

        DashScopeApi api = apiBuilder.build();

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel("qwen3-coder-plus")
                .withTemperature(0.7)
                .build();

        DashScopeChatModel.Builder modelBuilder = DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(options);

        if (observationRegistry != null) {
            modelBuilder.observationRegistry(observationRegistry);
        }

        return modelBuilder.build();
    }
}


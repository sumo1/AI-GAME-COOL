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
 * 在阿里云百炼平台上使用 Moonshot-Kimi-K2-Instruct 模型。
 * 复用与 DashScope 相同的 API Key 与 Base URL，仅切换模型名。
 */
@Configuration
public class KimiDashScopeConfig {

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Autowired(required = false)
    private RestClient.Builder restClientBuilder;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Bean("kimiK2ChatModel")
    public ChatModel kimiK2ChatModel() {
        DashScopeApi.Builder apiBuilder = DashScopeApi.builder().apiKey(apiKey);
        if (restClientBuilder != null) {
            apiBuilder.restClientBuilder(restClientBuilder);
        }

        DashScopeApi api = apiBuilder.build();

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel("Moonshot-Kimi-K2-Instruct")
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


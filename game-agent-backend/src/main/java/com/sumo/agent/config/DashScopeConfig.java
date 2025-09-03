package com.sumo.agent.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云百炼（DashScope）配置类
 */
@Slf4j
@Configuration
public class DashScopeConfig {
    
    @Value("${spring.ai.dashscope.api-key}")
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
    @Primary  // 设置为主要的ChatModel
    public ChatModel dashScopeChatModel() {
        log.info("配置阿里云百炼 ChatModel - 模型: {}", model);
        
        // 使用Builder模式创建DashScope API客户端
        DashScopeApi.Builder apiBuilder = DashScopeApi.builder()
                .apiKey(apiKey);
        
        if (restClientBuilder != null) {
            apiBuilder.restClientBuilder(restClientBuilder);
        }
        
        DashScopeApi dashScopeApi = apiBuilder.build();
        
        // 配置聊天选项
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .build();
        
        // 使用Builder模式创建ChatModel
        DashScopeChatModel.Builder modelBuilder = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options);
        
        if (observationRegistry != null) {
            modelBuilder.observationRegistry(observationRegistry);
        }
        
        DashScopeChatModel chatModel = modelBuilder.build();
        
        log.info("阿里云百炼 ChatModel配置完成 - 使用模型: {}", model);
        return chatModel;
    }
}

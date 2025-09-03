package com.sumo.agent.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * AI模型配置类 - 支持OpenAI和阿里云百炼
 * 当前已切换到阿里云百炼，此配置暂时禁用
 */
@Slf4j
//@Configuration  // 暂时禁用，使用阿里云百炼
public class OpenAIConfig {
    
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;
    
    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String baseUrl;
    
    @Value("${spring.ai.openai.chat.options.model:gpt-3.5-turbo}")
    private String model;
    
    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double temperature;
    
    @Value("${spring.ai.openai.chat.options.max-tokens:4000}")
    private Integer maxTokens;
    
    @Value("${proxy.enabled:false}")
    private boolean proxyEnabled;
    
    @Value("${proxy.host:127.0.0.1}")
    private String proxyHost;
    
    @Value("${proxy.port:7890}")
    private int proxyPort;
    
    @Value("${proxy.type:http}")
    private String proxyType;
    
    
    @Autowired(required = false)
    private ObservationRegistry observationRegistry;
    
    private RestClient.Builder customRestClientBuilder() {
        RestClient.Builder builder = RestClient.builder();
        
        if (proxyEnabled) {
            log.info("启用代理配置 - {}://{}:{}", proxyType, proxyHost, proxyPort);
            
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            
            // 配置代理
            Proxy.Type type = "socks5".equalsIgnoreCase(proxyType) ? 
                Proxy.Type.SOCKS : Proxy.Type.HTTP;
            Proxy proxy = new Proxy(type, new InetSocketAddress(proxyHost, proxyPort));
            requestFactory.setProxy(proxy);
            
            // 设置超时时间
            requestFactory.setConnectTimeout(30000);
            requestFactory.setReadTimeout(60000);
            
            builder.requestFactory(requestFactory);
        }
        
        return builder;
    }
    
    @Bean
    public ChatModel chatModel() {
        log.info("配置OpenAI ChatModel - 模型: {}, BaseURL: {}", model, baseUrl);
        
        if (proxyEnabled) {
            log.info("使用代理连接OpenAI: {}://{}:{}", proxyType, proxyHost, proxyPort);
        }
        
        // 使用自定义的RestClient.Builder（包含代理配置）
        RestClient.Builder clientBuilder = customRestClientBuilder();
        
        // 使用Builder模式创建OpenAI API客户端
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(clientBuilder);
        
        OpenAiApi openAiApi = apiBuilder.build();
        
        // 配置聊天选项
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
        
        // 使用Builder模式创建ChatModel
        OpenAiChatModel.Builder modelBuilder = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options);
        
        if (observationRegistry != null) {
            modelBuilder.observationRegistry(observationRegistry);
        }
        
        OpenAiChatModel chatModel = modelBuilder.build();
        
        log.info("OpenAI ChatModel配置完成");
        return chatModel;
    }
}
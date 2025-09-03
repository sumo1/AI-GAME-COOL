package com.sumo.agent.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 统一的 ChatModel 路由器，根据 key 选择对应模型。
 * 支持：
 * - 默认/Primary（当前为 DashScopeConfig 提供的 ChatModel） => key: null, "default", "dashscope"
 * - Kimi K2 => key: "kimi-k2"
 */
@Component
public class ChatModelRouter {

    @Autowired(required = false)
    private ChatModel defaultChatModel; // 来自 DashScopeConfig（@Primary）

    @Autowired(required = false)
    @Qualifier("kimiK2ChatModel")
    private ChatModel kimiK2ChatModel;

    @Autowired(required = false)
    @Qualifier("qwen3CoderPlusChatModel")
    private ChatModel qwen3CoderPlusChatModel;

    @Autowired(required = false)
    @Qualifier("deepseekChatModel")
    private ChatModel deepseekChatModel;

    public ChatModel get(String key) {
        if (key == null || key.isBlank() || "default".equalsIgnoreCase(key) || "dashscope".equalsIgnoreCase(key)) {
            return defaultChatModel;
        }
        if ("kimi-k2".equalsIgnoreCase(key)) {
            return kimiK2ChatModel != null ? kimiK2ChatModel : defaultChatModel;
        }
        if ("qwen3-coder-plus".equalsIgnoreCase(key)) {
            return qwen3CoderPlusChatModel != null ? qwen3CoderPlusChatModel : defaultChatModel;
        }
        if ("deepseek".equalsIgnoreCase(key)) {
            return deepseekChatModel != null ? deepseekChatModel : defaultChatModel;
        }
        // 未知模型，回退默认
        return defaultChatModel;
    }
}

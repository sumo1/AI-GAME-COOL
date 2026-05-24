package com.sumo.agent.infra.model;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 统一的 ChatModel 注册表，根据 key 选择对应模型。
 *
 * 支持的 key（DashScope OpenAI 兼容模式）：
 * - 默认/Primary => null / "default" / "dashscope"
 * - "qwen3.6-max-preview"
 * - "qwen3.7-max"
 * - "kimi-k2.6"
 * - "MiniMax-M2.5"  （大小写不敏感匹配）
 * - "deepseek-v4-pro"
 *
 * 历史 key（保留向后兼容，不在前端下拉框）：kimi-k2 / qwen3-coder-plus / deepseek
 */
@Component
public class ChatModelRegistry {

    @Autowired(required = false)
    @Qualifier("dashScopeChatModel")
    private ChatModel defaultChatModel;

    // === 新增 5 个 ===
    @Autowired(required = false)
    @Qualifier("qwen36MaxPreviewChatModel")
    private ChatModel qwen36MaxPreviewChatModel;

    @Autowired(required = false)
    @Qualifier("qwen37MaxChatModel")
    private ChatModel qwen37MaxChatModel;

    @Autowired(required = false)
    @Qualifier("kimiK26ChatModel")
    private ChatModel kimiK26ChatModel;

    @Autowired(required = false)
    @Qualifier("minimaxM25ChatModel")
    private ChatModel minimaxM25ChatModel;

    @Autowired(required = false)
    @Qualifier("deepseekV4ProChatModel")
    private ChatModel deepseekV4ProChatModel;

    // === 旧（保留兼容）===
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
        if (key == null || key.isBlank()
                || "default".equalsIgnoreCase(key) || "dashscope".equalsIgnoreCase(key)) {
            return defaultChatModel;
        }
        // 新 5 模型
        if ("qwen3.6-max-preview".equalsIgnoreCase(key)) {
            return qwen36MaxPreviewChatModel != null ? qwen36MaxPreviewChatModel : defaultChatModel;
        }
        if ("qwen3.7-max".equalsIgnoreCase(key)) {
            return qwen37MaxChatModel != null ? qwen37MaxChatModel : defaultChatModel;
        }
        if ("kimi-k2.6".equalsIgnoreCase(key)) {
            return kimiK26ChatModel != null ? kimiK26ChatModel : defaultChatModel;
        }
        if ("MiniMax-M2.5".equalsIgnoreCase(key)) {
            return minimaxM25ChatModel != null ? minimaxM25ChatModel : defaultChatModel;
        }
        if ("deepseek-v4-pro".equalsIgnoreCase(key)) {
            return deepseekV4ProChatModel != null ? deepseekV4ProChatModel : defaultChatModel;
        }
        // 旧 key 兼容
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

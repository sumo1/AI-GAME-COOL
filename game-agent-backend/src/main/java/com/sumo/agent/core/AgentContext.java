/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.core;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent执行上下文
 * 用于在Agent执行过程中传递数据
 */
@Data
public class AgentContext {
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 用户输入
     */
    private String userInput;
    
    /**
     * Agent执行结果
     */
    private Object result;
    
    /**
     * 是否执行成功
     */
    private boolean success = true;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 开始时间
     */
    private long startTime;
    
    /**
     * 扩展参数
     */
    private Map<String, Object> attributes = new HashMap<>();
    
    /**
     * 游戏相关配置
     */
    private GameConfig gameConfig;
    
    /**
     * 设置属性
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    /**
     * 获取属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }
    
    /**
     * 判断是否包含属性
     */
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }
}
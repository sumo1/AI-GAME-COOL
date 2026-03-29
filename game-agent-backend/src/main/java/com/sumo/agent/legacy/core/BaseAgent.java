/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.legacy.core;

import lombok.extern.slf4j.Slf4j;

/**
 * Agent基础抽象类
 * 定义了所有Agent的基本行为和生命周期
 */
@Slf4j
@Deprecated
public abstract class BaseAgent {
    
    /**
     * 执行Agent主逻辑
     * 
     * @param context 执行上下文
     */
    public abstract void execute(AgentContext context);
    
    /**
     * 获取Agent名称
     * 
     * @return Agent的名称
     */
    public abstract String getName();
    
    /**
     * 获取Agent描述
     * 用于帮助主Agent选择合适的子Agent
     * 
     * @return Agent的详细描述
     */
    public abstract String getDescription();
    
    /**
     * 获取Agent优先级
     * 
     * @return 优先级枚举
     */
    public AgentPriority getPriority() {
        return AgentPriority.MEDIUM;
    }
    
    /**
     * Agent前置处理
     * 
     * @param context 执行上下文
     */
    protected void preHandle(AgentContext context) {
        log.info("🚀 开始执行Agent: {}", getName());
        context.setStartTime(System.currentTimeMillis());
    }
    
    /**
     * Agent后置处理
     * 
     * @param context 执行上下文
     */
    protected void postHandle(AgentContext context) {
        long duration = System.currentTimeMillis() - context.getStartTime();
        log.info("✅ Agent执行完成: {}, 耗时: {}ms", getName(), duration);
    }
    
    /**
     * 模板方法，定义执行流程
     * 
     * @param context 执行上下文
     */
    public final void run(AgentContext context) {
        try {
            preHandle(context);
            execute(context);
            postHandle(context);
        } catch (Exception e) {
            handleError(context, e);
        }
    }
    
    /**
     * 错误处理
     * 
     * @param context 执行上下文
     * @param e 异常
     */
    protected void handleError(AgentContext context, Exception e) {
        log.error("❌ Agent执行失败: {}", getName(), e);
        context.setError(e.getMessage());
        context.setSuccess(false);
    }
}
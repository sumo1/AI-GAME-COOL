/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent优先级枚举
 */
@Getter
@AllArgsConstructor
public enum AgentPriority {
    
    HIGH("高", 3),
    MEDIUM("中", 2),
    LOW("低", 1);
    
    private final String displayName;
    private final int level;
}
package com.sumo.agent.v2.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心 — 通过 Spring 自动发现所有 GameTool 组件
 */
@Slf4j
@Component
public class ToolRegistry {

    @Autowired(required = false)
    private List<GameTool> tools = Collections.emptyList();

    private final Map<String, GameTool> toolMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (GameTool tool : tools) {
            String name = tool.getProfile().name();
            toolMap.put(name, tool);
            log.info("注册工具: {} - {}", name, tool.getProfile().description());
        }
        log.info("工具注册完成，共 {} 个工具", toolMap.size());
    }

    public GameTool getTool(String name) {
        return toolMap.get(name);
    }

    public List<GameTool> getAllTools() {
        return List.copyOf(toolMap.values());
    }

    public Map<String, GameTool> getToolMap() {
        return Collections.unmodifiableMap(toolMap);
    }
}

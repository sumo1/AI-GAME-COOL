/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent;

import com.sumo.agent.legacy.core.BaseAgent;
import com.sumo.agent.legacy.core.GameGeneratorAgent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * 游戏Agent框架启动类
 */
@Slf4j
@SpringBootApplication
public class Application {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private GameGeneratorAgent gameGeneratorAgent;
    
    public static void main(String[] args) {
        // 启动Spring应用
        ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);

        // 启动完成日志
        log.info("🚀 游戏Agent框架启动成功！");

        // 读取实际端口，确保日志与配置一致（默认8088）
        Environment env = context.getEnvironment();
        String port = env.getProperty("server.port", "8088");

        log.info("📍 访问地址: http://localhost:{}", port);
    }
    
    /**
     * 自动注册所有Agent
     */
    @PostConstruct
    public void registerAgents() {
        // 注册所有Agent
        log.info("📝 开始注册Agent...");
        
        // 获取所有BaseAgent的实现类
        Map<String, BaseAgent> agents = applicationContext.getBeansOfType(BaseAgent.class);
        
        agents.forEach((name, agent) -> {
            gameGeneratorAgent.registerAgent(name, agent);
            log.info("✅ 注册Agent: {} - {}", name, agent.getName());
        });
        
        log.info("📊 共注册 {} 个Agent", agents.size());
    }
}

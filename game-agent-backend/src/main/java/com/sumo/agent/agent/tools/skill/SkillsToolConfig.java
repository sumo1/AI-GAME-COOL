package com.sumo.agent.agent.tools.skill;

import com.sumo.agent.agent.skill.SkillLoader;
import com.sumo.agent.agent.tools.ToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * SkillsTool 配置 — 注册 spring-ai-agent-utils 的 SkillsTool。
 * <p>
 * 用 SkillsToolCallbackWrapper 包装，拦截 load 调用以同步 ToolContext.activeSkillDefinition。
 */
@Slf4j
@Configuration
public class SkillsToolConfig {

    /**
     * 创建 SkillsTool ToolCallback（包装后版本）。
     * 扫描 classpath:skills/ 下的所有 SKILL.md 文件。
     */
    @Bean
    public ToolCallback skillsToolCallback(ToolContext toolContext, SkillLoader skillLoader) {
        ToolCallback innerCallback = SkillsTool.builder()
                .addSkillsResource(new ClassPathResource("skills"))
                .build();

        log.info("SkillsTool 已创建（classpath:skills/），已用 ToolContext 包装器增强");
        return new SkillsToolCallbackWrapper(innerCallback, toolContext, skillLoader);
    }
}

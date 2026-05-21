package com.sumo.agent.infra.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SQLite 持久化引导配置（任务 260521-game-storage-db）
 *
 * 职责：
 * 1. **DataSource 创建之前**确保 DB 文件所在目录存在（Hikari 拿连接时父目录必须已存在）
 *    通过 BeanFactoryPostProcessor 在 bean 实例化前介入，比 @PostConstruct 早
 * 2. 应用启动就绪后把 SQLite 切到 WAL 模式 + 打开 foreign_keys 约束
 *    通过 ApplicationReadyEvent 触发，确保 schema.sql 已跑完
 *
 * 不做：业务方法、查询、表定义（schema.sql 负责）。
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    /**
     * 在所有 bean 实例化之前确保 SQLite DB 文件的父目录存在。
     * Spring Boot 的 DataSourceScriptDatabaseInitializer 会在 bean 初始化阶段拿连接，
     * 那时若父目录不存在 SQLite 直接失败；用 BeanFactoryPostProcessor 提前一步。
     */
    @Bean
    public static BeanFactoryPostProcessor sqliteDirectoryEnsurer() {
        return new BeanFactoryPostProcessor() {
            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                Environment env = beanFactory.getBean(Environment.class);
                String url = env.getProperty("spring.datasource.url", "");
                ensureParentDir(url);
            }
        };
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> applySqlitePragmas(JdbcTemplate jdbcTemplate) {
        return event -> {
            try {
                String mode = jdbcTemplate.queryForObject("PRAGMA journal_mode=WAL", String.class);
                log.info("SQLite journal_mode = {}", mode);
            } catch (Exception e) {
                log.warn("设置 PRAGMA journal_mode=WAL 失败", e);
            }
            try {
                jdbcTemplate.execute("PRAGMA foreign_keys=ON");
                Integer fk = jdbcTemplate.queryForObject("PRAGMA foreign_keys", Integer.class);
                log.info("SQLite foreign_keys = {}", fk);
            } catch (Exception e) {
                log.warn("设置 PRAGMA foreign_keys=ON 失败", e);
            }
        };
    }

    /**
     * SQLite jdbc URL 形如 jdbc:sqlite:./data/game-agent.db
     * Hikari 在拿连接时会失败如果父目录不存在；提前创建。
     */
    private static void ensureParentDir(String jdbcUrl) {
        String prefix = "jdbc:sqlite:";
        if (jdbcUrl == null || !jdbcUrl.startsWith(prefix)) {
            log.warn("非 SQLite jdbcUrl，跳过目录预创建: {}", jdbcUrl);
            return;
        }
        String filePath = jdbcUrl.substring(prefix.length());
        if (":memory:".equals(filePath) || filePath.isBlank()) {
            return;
        }
        File dbFile = new File(filePath);
        Path parent = dbFile.getAbsoluteFile().toPath().getParent();
        if (parent == null) return;
        try {
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
                log.info("已创建 SQLite DB 目录: {}", parent);
            }
        } catch (Exception e) {
            log.warn("创建 SQLite DB 目录失败: {}", parent, e);
        }
    }
}

package com.sumo.agent.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 加载器 — 从 classpath:/skills/ 加载 YAML，构建 Skill 实例。
 * <p>
 * 加载后每个 SkillDefinition 被包装为 {@link DefaultSkill}。
 * 保留 getSkill(name) 返回 SkillDefinition 以兼容现有 Tool 调用。
 */
@Slf4j
@Component
public class SkillLoader {

    /** YAML 数据（兼容旧调用） */
    private final Map<String, SkillDefinition> definitions = new ConcurrentHashMap<>();

    /** Skill 策略实例 */
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadSkills();
    }

    private void loadSkills() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:skills/*.yaml");

            Yaml yaml = new Yaml(new Constructor(SkillDefinition.class, new LoaderOptions()));

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    SkillDefinition def = yaml.load(is);
                    if (def != null && def.getName() != null) {
                        definitions.put(def.getName(), def);

                        // 构建 Skill 策略实例
                        Skill skill = new DefaultSkill(def);
                        skills.put(def.getName(), skill);

                        log.info("加载 Skill: {} ({}) [{}项评估检查, {}项修复提示]",
                                def.getName(), def.getDisplayName(),
                                skill.getEvaluationChecks().size(),
                                skill.getFixHints().size());
                    }
                } catch (Exception e) {
                    log.warn("加载 Skill 文件失败: {}", resource.getFilename(), e);
                }
            }

            log.info("Skill 加载完成，共 {} 个", skills.size());
        } catch (Exception e) {
            log.warn("扫描 Skill 目录失败", e);
        }
    }

    /** 获取 SkillDefinition（兼容旧 Tool 调用） */
    public Optional<SkillDefinition> getSkill(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    /** 获取 Skill 策略实例 */
    public Optional<Skill> getSkillInstance(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public List<SkillDefinition> listSkills() {
        return List.copyOf(definitions.values());
    }

    /**
     * 按关键词过滤 Skill（匹配 name / description / tags）
     */
    public List<SkillDefinition> listSkills(String filter) {
        if (filter == null || filter.isBlank()) {
            return listSkills();
        }
        String lowerFilter = filter.toLowerCase();
        return definitions.values().stream()
                .filter(s -> s.getName().toLowerCase().contains(lowerFilter)
                        || s.getDescription().toLowerCase().contains(lowerFilter)
                        || (s.getTags() != null && s.getTags().stream()
                                .anyMatch(t -> t.toLowerCase().contains(lowerFilter))))
                .toList();
    }
}

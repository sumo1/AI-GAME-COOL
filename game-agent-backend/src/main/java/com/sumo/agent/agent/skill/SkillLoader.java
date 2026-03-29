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
 * Skill 加载器 — 从 classpath:/skills/ 目录加载 YAML 文件
 */
@Slf4j
@Component
public class SkillLoader {

    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();

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
                    SkillDefinition skill = yaml.load(is);
                    if (skill != null && skill.getName() != null) {
                        skills.put(skill.getName(), skill);
                        log.info("加载 Skill: {} ({})", skill.getName(), skill.getDisplayName());
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

    public Optional<SkillDefinition> getSkill(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public List<SkillDefinition> listSkills() {
        return List.copyOf(skills.values());
    }

    /**
     * 按关键词过滤 Skill（匹配 name / description / tags）
     */
    public List<SkillDefinition> listSkills(String filter) {
        if (filter == null || filter.isBlank()) {
            return listSkills();
        }
        String lowerFilter = filter.toLowerCase();
        return skills.values().stream()
                .filter(s -> s.getName().toLowerCase().contains(lowerFilter)
                        || s.getDescription().toLowerCase().contains(lowerFilter)
                        || (s.getTags() != null && s.getTags().stream()
                                .anyMatch(t -> t.toLowerCase().contains(lowerFilter))))
                .toList();
    }
}

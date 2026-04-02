package com.sumo.agent.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 加载器 — 从 classpath:skills/{name}/SKILL.md 加载元数据。
 * <p>
 * 负责解析 SKILL.md frontmatter 获取 gameType 等元数据。
 * list/load 功能已交给 spring-ai-agent-utils 的 SkillsTool。
 */
@Slf4j
@Component
public class SkillLoader {

    private final Map<String, SkillDefinition> definitions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadSkills();
    }

    private void loadSkills() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] skillMdFiles = resolver.getResources("classpath:skills/*/SKILL.md");

            for (Resource res : skillMdFiles) {
                try {
                    String content = res.getContentAsString(StandardCharsets.UTF_8);
                    SkillDefinition def = parseSkillMd(content);

                    if (def == null || def.getName() == null) {
                        log.warn("SKILL.md 解析失败（无 name）: {}", res.getURL());
                        continue;
                    }

                    // 加载 assets/template.html（可选）
                    loadTemplate(resolver, def);

                    definitions.put(def.getName(), def);

                    log.info("加载 Skill: {} ({})", def.getName(), def.getDescription() != null
                            ? def.getDescription().substring(0, Math.min(40, def.getDescription().length())) + "..."
                            : "?");

                } catch (Exception e) {
                    log.warn("加载 SKILL.md 失败: {}", res.getFilename(), e);
                }
            }

            log.info("Skill 加载完成，共 {} 个", definitions.size());
        } catch (Exception e) {
            log.warn("扫描 skills 目录失败", e);
        }
    }

    /**
     * 解析 SKILL.md：frontmatter → 元数据（机器用），body → 原样保留（LLM 读）
     */
    @SuppressWarnings("unchecked")
    private SkillDefinition parseSkillMd(String content) {
        if (!content.startsWith("---")) return null;
        int secondDelim = content.indexOf("---", 3);
        if (secondDelim < 0) return null;

        String frontmatterYaml = content.substring(3, secondDelim).trim();
        String body = content.substring(secondDelim + 3).trim();

        Yaml yaml = new Yaml();
        Map<String, Object> fm = yaml.load(frontmatterYaml);
        if (fm == null) return null;

        SkillDefinition def = new SkillDefinition();
        def.setName((String) fm.get("name"));
        def.setDescription((String) fm.get("description"));
        def.setDisplayName((String) fm.getOrDefault("displayName", def.getName()));
        def.setAgeGroup(String.valueOf(fm.getOrDefault("ageGroup", "")));
        def.setGameType((String) fm.get("gameType"));

        Object tagsObj = fm.get("tags");
        if (tagsObj instanceof List) {
            def.setTags(((List<Object>) tagsObj).stream().map(String::valueOf).toList());
        }

        // Body 原样保留，不做结构化解析——LLM 自己读
        def.setInstructions(body);

        return def;
    }

    private void loadTemplate(PathMatchingResourcePatternResolver resolver, SkillDefinition def) {
        try {
            Resource templateResource = resolver.getResource(
                    "classpath:skills/" + def.getName() + "/assets/template.html");
            if (templateResource.exists()) {
                def.setTemplate(templateResource.getContentAsString(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            log.debug("未找到模板: {}/assets/template.html", def.getName());
        }
    }

    // ==================== 公共 API ====================

    public Optional<SkillDefinition> getSkill(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    public List<SkillDefinition> listSkills() {
        return List.copyOf(definitions.values());
    }
}

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
 * Skill 加载器 — 从 classpath:skills/{name}/SKILL.md 加载 Skill 定义。
 * <p>
 * 对齐 AgentSkills.io 规范：
 * <ul>
 *   <li>Frontmatter（YAML）：只读 name + description + metadata</li>
 *   <li>Body（Markdown）：原样保留，LLM 自己读</li>
 *   <li>不再创建 "Skill 策略实例"——skill 就是文本，不是 Java 对象</li>
 * </ul>
 *
 * @see <a href="https://agentskills.io/specification">AgentSkills.io Specification</a>
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
                    log.info("加载 Skill: {} ({})", def.getName(),
                            truncate(def.getDescription(), 50));

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
     * 解析 SKILL.md — 严格按 AgentSkills.io 规范：
     * frontmatter 只取 name + description + metadata，其余全部忽略。
     * body 原样保留给 LLM 读。
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

        // Required fields
        def.setName((String) fm.get("name"));
        def.setDescription((String) fm.get("description"));

        // Optional: metadata map（规范允许任意 key-value）
        Object metaObj = fm.get("metadata");
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (metaObj instanceof Map) {
            metadata.putAll((Map<String, Object>) metaObj);
        }

        // 非规范字段（ageGroup、gameType、tags 等）也收到 metadata 里，保持向后兼容
        for (String key : List.of("ageGroup", "gameType", "tags", "displayName")) {
            Object val = fm.get(key);
            if (val != null) {
                metadata.put(key, val);
            }
        }
        if (!metadata.isEmpty()) {
            def.setMetadata(metadata);
        }

        // Body 原样保留
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

    public List<SkillDefinition> listSkills(String filter) {
        if (filter == null || filter.isBlank()) {
            return listSkills();
        }
        String lowerFilter = filter.toLowerCase();
        return definitions.values().stream()
                .filter(s -> matches(s, lowerFilter))
                .toList();
    }

    private boolean matches(SkillDefinition s, String lowerFilter) {
        if (s.getName().toLowerCase().contains(lowerFilter)) return true;
        if (s.getDescription() != null && s.getDescription().toLowerCase().contains(lowerFilter)) return true;

        // 也匹配 metadata 中的 tags
        if (s.getMetadata() != null) {
            Object tags = s.getMetadata().get("tags");
            if (tags instanceof List<?> tagList) {
                return tagList.stream()
                        .anyMatch(t -> String.valueOf(t).toLowerCase().contains(lowerFilter));
            }
        }
        return false;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "?";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}

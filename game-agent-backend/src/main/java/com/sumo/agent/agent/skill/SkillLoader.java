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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 加载器 — 从 classpath:skills/{name}/SKILL.md 加载 Skill（AgentSkills.io 规范）。
 * <p>
 * 每个 Skill 是一个目录：SKILL.md（frontmatter + 操作手册）+ assets/template.html（可选）。
 */
@Slf4j
@Component
public class SkillLoader {

    private final Map<String, SkillDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadSkills();
    }

    private void loadSkills() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] skillMdFiles = resolver.getResources("classpath:skills/*/SKILL.md");

            for (Resource skillMdResource : skillMdFiles) {
                try {
                    String content = skillMdResource.getContentAsString(StandardCharsets.UTF_8);
                    SkillDefinition def = parseSkillMd(content);

                    if (def == null || def.getName() == null) {
                        log.warn("SKILL.md 解析失败（无 name）: {}", skillMdResource.getURL());
                        continue;
                    }

                    // 加载 assets/template.html
                    loadTemplate(resolver, def);

                    // 从 SKILL.md body 解析结构化段落
                    parseStructuredSections(def);

                    definitions.put(def.getName(), def);
                    Skill skill = new DefaultSkill(def);
                    skills.put(def.getName(), skill);

                    log.info("加载 Skill: {} [{}项检查, {}项修复提示]",
                            def.getName(),
                            skill.getEvaluationChecks().size(),
                            skill.getFixHints().size());

                } catch (Exception e) {
                    log.warn("加载 SKILL.md 失败: {}", skillMdResource.getFilename(), e);
                }
            }

            log.info("Skill 加载完成，共 {} 个", skills.size());
        } catch (Exception e) {
            log.warn("扫描 skills 目录失败", e);
        }
    }

    /**
     * 解析 SKILL.md：frontmatter（YAML）+ body（Markdown）
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

    /**
     * 从 SKILL.md body 解析结构化段落：评估重点 → evaluationCriteria，常见问题 → fixHints
     */
    private void parseStructuredSections(SkillDefinition def) {
        String body = def.getInstructions();
        if (body == null || body.isEmpty()) return;

        def.setEvaluationCriteria(parseListSection(body, "评估重点"));
        def.setFixHints(parseFixHintsSection(body));
    }

    private List<String> parseListSection(String body, String sectionTitle) {
        Pattern sectionPattern = Pattern.compile("^##\\s+" + Pattern.quote(sectionTitle) + "\\s*$", Pattern.MULTILINE);
        Matcher m = sectionPattern.matcher(body);
        if (!m.find()) return List.of();

        int start = m.end();
        Pattern nextSection = Pattern.compile("^##\\s+", Pattern.MULTILINE);
        Matcher nextM = nextSection.matcher(body);
        int end = body.length();
        if (nextM.find(start)) {
            end = nextM.start();
        }

        String section = body.substring(start, end);
        List<String> items = new ArrayList<>();
        Pattern listItem = Pattern.compile("^-\\s+(.+)$", Pattern.MULTILINE);
        Matcher listM = listItem.matcher(section);
        while (listM.find()) {
            items.add(listM.group(1).trim());
        }
        return items;
    }

    private List<FixHint> parseFixHintsSection(String body) {
        Pattern sectionPattern = Pattern.compile("^##\\s+常见问题\\s*$", Pattern.MULTILINE);
        Matcher m = sectionPattern.matcher(body);
        if (!m.find()) return List.of();

        int start = m.end();
        Pattern nextSection = Pattern.compile("^##\\s+", Pattern.MULTILINE);
        Matcher nextM = nextSection.matcher(body);
        int end = body.length();
        if (nextM.find(start)) {
            end = nextM.start();
        }

        String section = body.substring(start, end);
        List<FixHint> hints = new ArrayList<>();
        Pattern hintPattern = Pattern.compile("-\\s+\\*\\*(.+?)\\*\\*\\s*[→\\->]+\\s*(.+)$", Pattern.MULTILINE);
        Matcher hintM = hintPattern.matcher(section);
        while (hintM.find()) {
            hints.add(new FixHint(hintM.group(1).trim(), hintM.group(2).trim()));
        }
        return hints;
    }

    // ==================== 公共 API ====================

    public Optional<SkillDefinition> getSkill(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    public Optional<Skill> getSkillInstance(String name) {
        return Optional.ofNullable(skills.get(name));
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
                .filter(s -> s.getName().toLowerCase().contains(lowerFilter)
                        || (s.getDescription() != null && s.getDescription().toLowerCase().contains(lowerFilter))
                        || (s.getTags() != null && s.getTags().stream()
                                .anyMatch(t -> t.toLowerCase().contains(lowerFilter))))
                .toList();
    }
}

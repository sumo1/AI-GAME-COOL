package com.sumo.agent.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 加载器 — 支持两种格式：
 * <ol>
 *   <li><b>SKILL.md 目录</b>（AgentSkills.io 规范）: skills-v2/{name}/SKILL.md + assets/template.html</li>
 *   <li><b>YAML 文件</b>（旧格式，向后兼容）: skills/{name}.yaml</li>
 * </ol>
 * SKILL.md 优先：如果 skills-v2 中存在同名 Skill，忽略旧 YAML。
 */
@Slf4j
@Component
public class SkillLoader {

    private final Map<String, SkillDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadSkillMdFormat();
        loadYamlFormat();
    }

    /**
     * 加载 SKILL.md 目录格式（优先）
     */
    private void loadSkillMdFormat() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] skillMdFiles = resolver.getResources("classpath:skills-v2/*/SKILL.md");

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

                    log.info("加载 Skill (SKILL.md): {} ({}) [{}项检查, {}项修复提示]",
                            def.getName(), def.getDescription() != null ? def.getDescription().substring(0, Math.min(30, def.getDescription().length())) + "..." : "?",
                            skill.getEvaluationChecks().size(),
                            skill.getFixHints().size());

                } catch (Exception e) {
                    log.warn("加载 SKILL.md 失败: {}", skillMdResource.getFilename(), e);
                }
            }
        } catch (Exception e) {
            log.warn("扫描 skills-v2 目录失败", e);
        }
    }

    /**
     * 加载旧 YAML 格式（向后兼容，跳过已由 SKILL.md 加载的）
     */
    private void loadYamlFormat() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:skills/*.yaml");

            Yaml yaml = new Yaml(new org.yaml.snakeyaml.constructor.Constructor(SkillDefinition.class, new LoaderOptions()));

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    SkillDefinition def = yaml.load(is);
                    if (def != null && def.getName() != null) {
                        // 跳过已由 SKILL.md 加载的
                        if (definitions.containsKey(def.getName())) {
                            log.debug("跳过 YAML Skill {} (已由 SKILL.md 加载)", def.getName());
                            continue;
                        }
                        definitions.put(def.getName(), def);
                        skills.put(def.getName(), new DefaultSkill(def));
                        log.info("加载 Skill (YAML): {} ({})", def.getName(), def.getDisplayName());
                    }
                } catch (Exception e) {
                    log.warn("加载 YAML Skill 失败: {}", resource.getFilename(), e);
                }
            }

            log.info("Skill 加载完成，共 {} 个", skills.size());
        } catch (Exception e) {
            log.warn("扫描 skills YAML 目录失败", e);
        }
    }

    /**
     * 解析 SKILL.md：frontmatter（YAML）+ body（Markdown）
     */
    @SuppressWarnings("unchecked")
    private SkillDefinition parseSkillMd(String content) {
        // 分离 frontmatter 和 body
        if (!content.startsWith("---")) return null;
        int secondDelim = content.indexOf("---", 3);
        if (secondDelim < 0) return null;

        String frontmatterYaml = content.substring(3, secondDelim).trim();
        String body = content.substring(secondDelim + 3).trim();

        // 解析 frontmatter 为 Map
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

        // SKILL.md body 就是操作手册
        def.setInstructions(body);

        return def;
    }

    /**
     * 加载 assets/template.html
     */
    private void loadTemplate(PathMatchingResourcePatternResolver resolver, SkillDefinition def) {
        try {
            Resource templateResource = resolver.getResource(
                    "classpath:skills-v2/" + def.getName() + "/assets/template.html");
            if (templateResource.exists()) {
                def.setTemplate(templateResource.getContentAsString(StandardCharsets.UTF_8));
                log.debug("加载模板: {}/assets/template.html ({} 字符)", def.getName(), def.getTemplate().length());
            }
        } catch (Exception e) {
            log.debug("未找到模板文件: {}/assets/template.html", def.getName());
        }
    }

    /**
     * 从 SKILL.md body 解析结构化段落：评估重点 → evaluationCriteria，常见问题 → fixHints
     */
    private void parseStructuredSections(SkillDefinition def) {
        String body = def.getInstructions();
        if (body == null || body.isEmpty()) return;

        // 解析 "## 评估重点" 段落中的列表项
        def.setEvaluationCriteria(parseListSection(body, "评估重点"));

        // 解析 "## 常见问题" 段落中的 "**xxx** → yyy" 格式
        def.setFixHints(parseFixHintsSection(body));
    }

    /**
     * 从 Markdown body 中提取指定标题下的列表项
     */
    private List<String> parseListSection(String body, String sectionTitle) {
        // 找到 ## sectionTitle 开始的位置
        Pattern sectionPattern = Pattern.compile("^##\\s+" + Pattern.quote(sectionTitle) + "\\s*$", Pattern.MULTILINE);
        Matcher m = sectionPattern.matcher(body);
        if (!m.find()) return List.of();

        int start = m.end();
        // 找到下一个 ## 标题或文档结束
        Pattern nextSection = Pattern.compile("^##\\s+", Pattern.MULTILINE);
        Matcher nextM = nextSection.matcher(body);
        int end = body.length();
        if (nextM.find(start)) {
            end = nextM.start();
        }

        String section = body.substring(start, end);

        // 提取 "- xxx" 列表项
        List<String> items = new ArrayList<>();
        Pattern listItem = Pattern.compile("^-\\s+(.+)$", Pattern.MULTILINE);
        Matcher listM = listItem.matcher(section);
        while (listM.find()) {
            items.add(listM.group(1).trim());
        }

        return items;
    }

    /**
     * 从 "## 常见问题" 段落解析 FixHint（格式："**症状** → 方案"）
     */
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

        // 匹配 "- **xxx** → yyy" 格式
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

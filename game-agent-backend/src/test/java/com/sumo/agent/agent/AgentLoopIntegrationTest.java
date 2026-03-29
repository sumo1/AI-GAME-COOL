package com.sumo.agent.agent;

import com.sumo.agent.agent.evaluation.GameEvaluator;
import com.sumo.agent.agent.evaluation.ProbeReport;
import com.sumo.agent.agent.loop.WorkingMemory;
import com.sumo.agent.agent.skill.*;;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentLoop v2 集成测试
 * <p>
 * 测试核心组件的独立逻辑，不依赖外部 LLM 服务。
 */
class AgentLoopIntegrationTest {

    // ==================== WorkingMemory 测试 ====================

    @Nested
    @DisplayName("WorkingMemory 测试")
    class WorkingMemoryTest {

        @Test
        @DisplayName("基本 toContextXml 输出应包含游戏状态")
        void testBasicContextXml() {
            WorkingMemory memory = new WorkingMemory();
            memory.setIteration(1);
            memory.setEvalScore(75);
            memory.setGameVersion(2);

            String xml = memory.toContextXml();

            assertTrue(xml.contains("<working_memory>"));
            assertTrue(xml.contains("<version>2</version>"));
            assertTrue(xml.contains("<last_eval_score>75/100</last_eval_score>"));
            assertTrue(xml.contains("<iteration>1 of 5</iteration>"));
            assertTrue(xml.contains("</working_memory>"));
        }

        @Test
        @DisplayName("短 HTML 应直接包含在 toContextXml 中")
        void testShortHtmlInContext() {
            WorkingMemory memory = new WorkingMemory();
            String shortHtml = "<html><head><title>Test</title></head><body>Hello</body></html>";
            memory.setGameHtml(shortHtml);

            String xml = memory.toContextXml();

            assertTrue(xml.contains("<game_html>"));
            assertTrue(xml.contains(shortHtml));
        }

        @Test
        @DisplayName("超长 HTML 应输出摘要而非完整内容")
        void testLongHtmlSummary() {
            WorkingMemory memory = new WorkingMemory();

            // 构造一个超过 8000 字符的 HTML
            StringBuilder htmlBuilder = new StringBuilder();
            htmlBuilder.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<title>数学冒险游戏</title>\n");
            htmlBuilder.append("<style>\n.game-container { width: 100%; }\n.score-board { color: red; }\n.option-btn { cursor: pointer; }\n</style>\n");
            htmlBuilder.append("</head>\n<body>\n");
            htmlBuilder.append("<div id=\"gameArea\" class=\"game-container\">\n");
            htmlBuilder.append("<div id=\"scoreDisplay\" class=\"score-board\">分数: 0</div>\n");
            htmlBuilder.append("</div>\n");
            htmlBuilder.append("<script>\n");
            htmlBuilder.append("function startGame() { console.log('start'); }\n");
            htmlBuilder.append("function checkAnswer(ans) { return ans === correct; }\n");
            htmlBuilder.append("function updateScore(delta) { score += delta; }\n");
            // 填充内容使其超过 8000 字符
            for (int i = 0; i < 200; i++) {
                htmlBuilder.append("// 游戏逻辑代码行 ").append(i).append(": var data_").append(i)
                        .append(" = { value: ").append(i).append(", label: '选项").append(i).append("' };\n");
            }
            htmlBuilder.append("</script>\n</body>\n</html>");

            String longHtml = htmlBuilder.toString();
            assertTrue(longHtml.length() > 8000, "测试 HTML 应超过 8000 字符");

            memory.setGameHtml(longHtml);
            String xml = memory.toContextXml();

            // 不应包含完整 HTML
            assertFalse(xml.contains("<game_html>"));
            // 应包含摘要标签
            assertTrue(xml.contains("<html_summary>"));
            assertTrue(xml.contains("<html_length>"));
        }

        @Test
        @DisplayName("getHtmlSummary 应提取关键结构信息")
        void testHtmlSummaryExtraction() {
            WorkingMemory memory = new WorkingMemory();

            // 构造测试 HTML
            StringBuilder htmlBuilder = new StringBuilder();
            htmlBuilder.append("<!DOCTYPE html><html><head><title>测试游戏</title></head><body>");
            htmlBuilder.append("<div id=\"main\" class=\"container game-area\">");
            htmlBuilder.append("<canvas id=\"gameCanvas\"></canvas>");
            htmlBuilder.append("</div>");
            htmlBuilder.append("<script>");
            htmlBuilder.append("function initGame() {}");
            htmlBuilder.append("function drawBoard() {}");
            htmlBuilder.append("function handleClick(e) {}");
            // 填充
            for (int i = 0; i < 300; i++) {
                htmlBuilder.append("var x").append(i).append(" = ").append(i).append("; ");
            }
            htmlBuilder.append("</script></body></html>");

            memory.setGameHtml(htmlBuilder.toString());
            String summary = memory.getHtmlSummary();

            // 应包含标题
            assertTrue(summary.contains("测试游戏"), "摘要应包含标题");
            // 应包含 CSS 类名
            assertTrue(summary.contains("container"), "摘要应包含 CSS 类名");
            // 应包含 JS 函数
            assertTrue(summary.contains("initGame"), "摘要应包含 JS 函数名");
            assertTrue(summary.contains("drawBoard"), "摘要应包含 JS 函数名");
            assertTrue(summary.contains("handleClick"), "摘要应包含 JS 函数名");
            // 应包含 ID
            assertTrue(summary.contains("main"), "摘要应包含元素 ID");
            assertTrue(summary.contains("gameCanvas"), "摘要应包含元素 ID");
            // 应包含 canvas 标签信息
            assertTrue(summary.contains("canvas"), "摘要应包含特殊标签信息");
        }

        @Test
        @DisplayName("短 HTML 的 getHtmlSummary 应返回原始内容")
        void testShortHtmlSummaryReturnsOriginal() {
            WorkingMemory memory = new WorkingMemory();
            String shortHtml = "<html><body>Short</body></html>";
            memory.setGameHtml(shortHtml);

            assertEquals(shortHtml, memory.getHtmlSummary());
        }

        @Test
        @DisplayName("preloadedSkill 应包含在 toContextXml 输出中")
        void testPreloadedSkillInContext() {
            WorkingMemory memory = new WorkingMemory();
            memory.setPreloadedSkill("math_adventure");

            String xml = memory.toContextXml();

            assertTrue(xml.contains("<preloaded_skill>math_adventure</preloaded_skill>"));
        }

        @Test
        @DisplayName("openIssues 应包含在 toContextXml 输出中")
        void testOpenIssuesInContext() {
            WorkingMemory memory = new WorkingMemory();
            memory.getOpenIssues().add("[布局] 2个元素越界");
            memory.getOpenIssues().add("[交互] 点击无响应");

            String xml = memory.toContextXml();

            assertTrue(xml.contains("<open_issues>"));
            assertTrue(xml.contains("[布局] 2个元素越界"));
            assertTrue(xml.contains("[交互] 点击无响应"));
        }
    }

    // ==================== SkillLoader 过滤测试 ====================

    @Nested
    @DisplayName("SkillLoader 过滤测试")
    class SkillLoaderFilterTest {

        @Test
        @DisplayName("SkillDefinition 的 toSummary 应包含核心字段")
        void testSkillDefinitionSummary() {
            SkillDefinition skill = new SkillDefinition();
            skill.setName("math_adventure");
            skill.setDisplayName("数学冒险");
            skill.setDescription("10以内加减法");
            skill.setAgeGroup("4-8");
            skill.setGameType("MATH");
            skill.setTags(List.of("数学", "加减法"));

            String summary = skill.toSummary();

            assertTrue(summary.contains("math_adventure"));
            assertTrue(summary.contains("数学冒险"));
            assertTrue(summary.contains("10以内加减法"));
            assertTrue(summary.contains("4-8"));
        }

        @Test
        @DisplayName("SkillDefinition getter/setter 应正确工作")
        void testSkillDefinitionGetterSetter() {
            SkillDefinition skill = new SkillDefinition();
            skill.setName("test");
            skill.setDisplayName("测试");
            skill.setDescription("测试描述");
            skill.setAgeGroup("6-10");
            skill.setDifficulty(List.of("easy", "medium"));
            skill.setTags(List.of("tag1", "tag2"));
            skill.setGameType("TEST");
            skill.setTemplate("<html></html>");
            skill.setPromptHint("提示");
            skill.setEvaluationCriteria(List.of("标准1"));

            assertEquals("test", skill.getName());
            assertEquals("测试", skill.getDisplayName());
            assertEquals("测试描述", skill.getDescription());
            assertEquals("6-10", skill.getAgeGroup());
            assertEquals(List.of("easy", "medium"), skill.getDifficulty());
            assertEquals(List.of("tag1", "tag2"), skill.getTags());
            assertEquals("TEST", skill.getGameType());
            assertEquals("<html></html>", skill.getTemplate());
            assertEquals("提示", skill.getPromptHint());
            assertEquals(List.of("标准1"), skill.getEvaluationCriteria());
        }
    }

    // ==================== Skill 接口测试 ====================

    @Nested
    @DisplayName("Skill 接口测试")
    class SkillInterfaceTest {

        @Test
        @DisplayName("DefaultSkill 应从 SkillDefinition 派生评估检查")
        void testDefaultSkillEvaluationChecks() {
            SkillDefinition def = new SkillDefinition();
            def.setName("test_quiz");
            def.setGameType("quiz");
            def.setEvaluationCriteria(List.of("答对/答错是否有明确反馈", "是否有计时功能"));

            Skill skill = new DefaultSkill(def);
            List<EvaluationCheck> checks = skill.getEvaluationChecks();

            // 应包含通用检查（hasScoreDisplay, jsErrorsBelow, outOfBoundsBelow）+ 派生检查 + gameType 检查
            assertTrue(checks.size() >= 3, "至少应有 3 项通用检查，实际: " + checks.size());
        }

        @Test
        @DisplayName("EvaluationCheck.htmlMustContain 通过时返回 empty")
        void testHtmlMustContainPass() {
            var check = EvaluationCheck.htmlMustContain("score", "missing score");
            var result = check.check("<div id='score'>0</div>", null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("EvaluationCheck.htmlMustContain 失败时返回 issue")
        void testHtmlMustContainFail() {
            var check = EvaluationCheck.htmlMustContain("score", "missing score");
            var result = check.check("<div>hello</div>", null);
            assertTrue(result.isPresent());
            assertEquals("missing score", result.get());
        }

        @Test
        @DisplayName("EvaluationCheck.hasScoreDisplay 能检测计分元素")
        void testHasScoreDisplay() {
            var check = EvaluationCheck.hasScoreDisplay();
            assertTrue(check.check("<div>分数: 0</div>", null).isEmpty());
            assertTrue(check.check("<span id='score'>100</span>", null).isEmpty());
            assertTrue(check.check("<div>no scoring</div>", null).isPresent());
        }

        @Test
        @DisplayName("EvaluationCheck.jsErrorsBelow 检测 JS 错误阈值")
        void testJsErrorsBelow() {
            var check = EvaluationCheck.jsErrorsBelow(2);

            // 无 report 时通过
            assertTrue(check.check("", null).isEmpty());

            // 少于阈值时通过
            ProbeReport report = createBaseReport();
            assertTrue(check.check("", report).isEmpty());

            // 超过阈值时失败
            List<ProbeReport.ProbeError> errors = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                var err = new ProbeReport.ProbeError();
                err.setMsg("err" + i);
                errors.add(err);
            }
            report.setErrors(errors);
            assertTrue(check.check("", report).isPresent());
        }

        @Test
        @DisplayName("DefaultSkill.getGenerationGuidance 包含 promptHint 和评估标准")
        void testGenerationGuidance() {
            SkillDefinition def = new SkillDefinition();
            def.setPromptHint("生成数学游戏的提示");
            def.setEvaluationCriteria(List.of("必须有计分", "必须有反馈"));

            Skill skill = new DefaultSkill(def);
            String guidance = skill.getGenerationGuidance();

            assertTrue(guidance.contains("生成数学游戏的提示"));
            assertTrue(guidance.contains("质量检查项"));
            assertTrue(guidance.contains("必须有计分"));
            assertTrue(guidance.contains("必须有反馈"));
        }

        @Test
        @DisplayName("DefaultSkill.getFixHints 返回 YAML 定义的修复提示")
        void testFixHints() {
            SkillDefinition def = new SkillDefinition();
            def.setFixHints(List.of(
                    new FixHint("答案错误", "检查算术逻辑"),
                    new FixHint("布局溢出", "限制容器高度")
            ));

            Skill skill = new DefaultSkill(def);
            List<FixHint> hints = skill.getFixHints();

            assertEquals(2, hints.size());
            assertEquals("答案错误", hints.get(0).symptom());
            assertEquals("检查算术逻辑", hints.get(0).solution());
        }

        @Test
        @DisplayName("DefaultSkill.getFixHints 无 fixHints 时返回空列表")
        void testEmptyFixHints() {
            SkillDefinition def = new SkillDefinition();
            Skill skill = new DefaultSkill(def);
            assertTrue(skill.getFixHints().isEmpty());
        }

        private ProbeReport createBaseReport() {
            ProbeReport report = new ProbeReport();
            report.setPageLoaded(true);
            report.setErrors(List.of());
            report.setEvents(List.of());
            report.setStateChanges(List.of());
            report.setOutOfBoundsElements(List.of());
            report.setStateTransitions(List.of());
            report.setResponseLatencies(List.of());
            report.setConsoleWarnings(List.of());
            report.setDomMutationsCount(0);
            return report;
        }
    }

    // ==================== GameEvaluator 评分计算测试 ====================

    @Nested
    @DisplayName("GameEvaluator 评分计算测试")
    class GameEvaluatorScoreTest {

        /**
         * 创建一个 GameEvaluator 实例仅用于 computeScores 测试
         * （computeScores 是 package-private，不依赖 Playwright）
         */
        private final GameEvaluator evaluator = new GameEvaluator();

        @Test
        @DisplayName("页面加载成功、无错误应得满分可运行性")
        void testPerfectRunnability() {
            ProbeReport report = createBaseReport();
            report.setPageLoaded(true);
            report.setErrors(List.of());

            evaluator.computeScores(report);

            assertEquals(20, report.getRunnabilityScore());
        }

        @Test
        @DisplayName("页面白屏应得 0 分可运行性")
        void testWhiteScreenRunnability() {
            ProbeReport report = createBaseReport();
            report.setPageLoaded(false);

            evaluator.computeScores(report);

            assertEquals(0, report.getRunnabilityScore());
            assertTrue(report.getIssues().stream().anyMatch(i -> i.contains("白屏")));
        }

        @Test
        @DisplayName("3个以上 JS 错误应严重扣分")
        void testManyJsErrors() {
            ProbeReport report = createBaseReport();
            report.setPageLoaded(true);

            List<ProbeReport.ProbeError> errors = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                ProbeReport.ProbeError err = new ProbeReport.ProbeError();
                err.setMsg("Error " + i);
                errors.add(err);
            }
            report.setErrors(errors);

            evaluator.computeScores(report);

            assertEquals(5, report.getRunnabilityScore());
        }

        @Test
        @DisplayName("无越界元素应得满分布局")
        void testPerfectLayout() {
            ProbeReport report = createBaseReport();
            report.setPageLoaded(true);
            report.setOutOfBoundsElements(List.of());

            evaluator.computeScores(report);

            assertEquals(20, report.getLayoutScore());
        }

        @Test
        @DisplayName("5个以上越界元素应得 0 分布局")
        void testSevereLayoutIssues() {
            ProbeReport report = createBaseReport();
            report.setPageLoaded(true);

            List<ProbeReport.OutOfBoundsElement> oob = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                ProbeReport.OutOfBoundsElement el = new ProbeReport.OutOfBoundsElement();
                el.setElement("div#el" + i);
                oob.add(el);
            }
            report.setOutOfBoundsElements(oob);

            evaluator.computeScores(report);

            assertEquals(0, report.getLayoutScore());
        }

        @Test
        @DisplayName("有点击且 DOM 变化应得满分交互")
        void testGoodInteractivity() {
            ProbeReport report = createBaseReport();
            report.setPageLoaded(true);

            List<ProbeReport.ProbeEvent> events = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                ProbeReport.ProbeEvent event = new ProbeReport.ProbeEvent();
                event.setType("click");
                event.setDomChanged(true);
                events.add(event);
            }
            report.setEvents(events);

            evaluator.computeScores(report);

            assertEquals(20, report.getInteractivityScore());
        }

        @Test
        @DisplayName("无交互事件应得 0 分交互")
        void testNoInteractivity() {
            ProbeReport report = createBaseReport();
            report.setPageLoaded(true);
            report.setEvents(List.of());

            evaluator.computeScores(report);

            assertEquals(0, report.getInteractivityScore());
        }

        @Test
        @DisplayName("有状态转换和分数变化应得满分完整性")
        void testFullCompleteness() {
            ProbeReport report = createBaseReport();
            report.setPageLoaded(true);
            report.setStateTransitions(List.of("init", "playing", "ended"));

            ProbeReport.StateChange sc = new ProbeReport.StateChange();
            sc.setType("score_change");
            report.setStateChanges(List.of(sc));
            report.setDomMutationsCount(10);

            evaluator.computeScores(report);

            assertEquals(20, report.getCompletenessScore());
        }

        @Test
        @DisplayName("无任何状态变化应得 0 分完整性")
        void testNoCompleteness() {
            ProbeReport report = createBaseReport();
            report.setPageLoaded(true);
            report.setStateTransitions(List.of());
            report.setStateChanges(List.of());
            report.setDomMutationsCount(0);

            evaluator.computeScores(report);

            assertEquals(0, report.getCompletenessScore());
        }

        @Test
        @DisplayName("总分应为各维度之和")
        void testTotalScoreCalculation() {
            ProbeReport report = createBaseReport();
            report.setPageLoaded(true);
            report.setErrors(List.of());
            report.setOutOfBoundsElements(List.of());
            report.setEvents(List.of());
            report.setStateTransitions(List.of());
            report.setStateChanges(List.of());
            report.setDomMutationsCount(0);

            evaluator.computeScores(report);

            int expected = report.getRunnabilityScore() + report.getLayoutScore()
                    + report.getInteractivityScore() + report.getCompletenessScore()
                    + report.getEducationScore();
            assertEquals(expected, report.getTotalScore());
        }

        @Test
        @DisplayName("教育匹配度默认应为 15 分")
        void testDefaultEducationScore() {
            ProbeReport report = createBaseReport();
            report.setPageLoaded(true);

            evaluator.computeScores(report);

            assertEquals(15, report.getEducationScore());
        }

        /**
         * 构建基础 ProbeReport（所有列表非空，避免 NPE）
         */
        private ProbeReport createBaseReport() {
            ProbeReport report = new ProbeReport();
            report.setPageLoaded(true);
            report.setErrors(List.of());
            report.setEvents(List.of());
            report.setStateChanges(List.of());
            report.setOutOfBoundsElements(List.of());
            report.setStateTransitions(List.of());
            report.setResponseLatencies(List.of());
            report.setConsoleWarnings(List.of());
            report.setDomMutationsCount(0);
            return report;
        }
    }

    // ==================== AgentLoopResult 测试 ====================

    @Nested
    @DisplayName("AgentLoopResult 测试")
    class AgentLoopResultTest {

        @Test
        @DisplayName("success 工厂方法应正确创建成功结果")
        void testSuccessResult() {
            var result = com.sumo.agent.agent.loop.AgentLoopResult.success(
                    "<html>game</html>", "游戏生成成功", 2, 85);

            assertTrue(result.success());
            assertEquals("<html>game</html>", result.gameHtml());
            assertEquals("游戏生成成功", result.llmMessage());
            assertEquals(2, result.iterations());
            assertEquals(85, result.evalScore());
            assertNull(result.error());
        }

        @Test
        @DisplayName("failure 工厂方法应正确创建失败结果")
        void testFailureResult() {
            var result = com.sumo.agent.agent.loop.AgentLoopResult.failure("LLM 调用失败", 3);

            assertFalse(result.success());
            assertNull(result.gameHtml());
            assertEquals("LLM 调用失败", result.error());
            assertEquals(3, result.iterations());
            assertEquals(0, result.evalScore());
        }
    }
}

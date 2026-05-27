package com.sumo.agent.infra.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumo.agent.agent.evaluation.EvaluationObservation;
import com.sumo.agent.agent.evaluation.ObservationIssue;
import com.sumo.agent.agent.loop.AgentLoopResult;
import com.sumo.agent.agent.loop.RunTrace;
import com.sumo.agent.agent.loop.TraceEntry;
import com.sumo.agent.infra.db.GameRunEvaluationEntity;
import com.sumo.agent.infra.db.GameRunEvaluationRepository;
import com.sumo.agent.infra.db.SessionEntity;
import com.sumo.agent.infra.db.SessionRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 任务 260524-skill-distillation-evidence Step 4 端到端 SSOT 验证：
 * 真启 Spring + 真 SQLite，{@link SessionService#recordEvidence} 后直查表，
 * 验证 evidence 各 JSON 字段是合法 JSON 且关键字段可解出。
 */
@SpringBootTest
class EvidenceWriteEndToEndTest {

    private static final Path TEST_DB = Path.of("./data/test-step4-evidence.db");

    @DynamicPropertySource
    static void setTestDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DB);
        registry.add("AGENT_DB_URL", () -> "jdbc:sqlite:" + TEST_DB);
    }

    @Autowired SessionService sessionService;
    @Autowired GameRunEvaluationRepository evaluationRepository;
    @Autowired SessionRepository sessionRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM skill_distillation_candidates");
        jdbc.update("DELETE FROM game_run_evaluations");
        jdbc.update("DELETE FROM game_runs");
        jdbc.update("DELETE FROM messages");
        jdbc.update("DELETE FROM sessions");
    }

    @AfterAll
    static void cleanupTempDb() throws IOException {
        Path[] paths = {
                TEST_DB,
                Path.of(TEST_DB + "-wal"),
                Path.of(TEST_DB + "-shm"),
                Path.of(TEST_DB + "-journal"),
        };
        for (Path p : paths) {
            Files.deleteIfExists(p);
        }
    }

    // -----------------------------------------------------------------------
    // 1. 成功路径 — 完整 evidence 都写入，每个 JSON 字段都是合法 JSON
    // -----------------------------------------------------------------------
    @Test
    void recordEvidence_successPath_writesAllJsonFields() throws Exception {
        SessionEntity sess = newSession("evidence-success");
        sessionRepository.insert(sess);

        EvaluationObservation obs = new EvaluationObservation();
        obs.setTotalScore(85);
        obs.getScoresByDimension().put("runnability", 18);
        obs.getScoresByDimension().put("layout", 17);
        obs.getScoresByDimension().put("interactivity", 17);
        obs.getScoresByDimension().put("completeness", 17);
        obs.getScoresByDimension().put("education", 16);
        obs.getIssues().add(ObservationIssue.fromIssueText("[评估] 过度复杂"));

        EvaluationObservation.ProbeSummary probe = new EvaluationObservation.ProbeSummary();
        probe.setPageLoaded(true);
        probe.setJsErrorCount(0);
        probe.setEventCount(5);
        probe.setDomMutationsCount(12);
        probe.setOutOfBoundsCount(0);
        probe.setStateTransitions(List.of("idle->playing", "playing->ended"));
        probe.setFinalScore(100);
        obs.setProbeSummary(probe);

        RunTrace trace = new RunTrace();
        TraceEntry entry = new TraceEntry();
        entry.setIteration(1);
        entry.setScoreBefore(0);
        entry.setScoreAfter(85);
        entry.setIssueCount(1);
        entry.setResponseLength(500);
        entry.setGameVersion(1);
        entry.setSummary("score 0→85 (+85)");
        entry.setEvaluationDegraded(false);
        List<String> snapshot = new ArrayList<>();
        snapshot.add("[评估] 过度复杂");
        entry.setIssuesSnapshot(snapshot);
        trace.append(entry);

        AgentLoopResult result = AgentLoopResult.successWithEvidence(
                "<html>...</html>", "ok", 1, 85, obs, trace, "snake-adventure");

        String evalId = sessionService.recordEvidence(
                sess.getId(), null, "qwen3.6-max-preview", result);

        assertNotNull(evalId);

        Optional<GameRunEvaluationEntity> found = evaluationRepository.findById(evalId);
        assertTrue(found.isPresent());
        GameRunEvaluationEntity ev = found.get();

        assertEquals(sess.getId(), ev.getSessionId());
        assertNull(ev.getGameRunId(), "传入 gameRunId=null 应保留为 NULL");
        assertEquals("snake-adventure", ev.getSkillName());
        assertEquals("qwen3.6-max-preview", ev.getModelKey());
        assertEquals(1, ev.getSuccess());
        assertNull(ev.getErrorType(), "成功路径不应有 errorType");
        assertEquals(85, ev.getTotalScore());
        assertEquals(0, ev.getDegraded());
        assertNull(ev.getDegradedReason());
        assertEquals(1, ev.getIterationCount());
        assertEquals("score 0→85 (+85)", ev.getFinalIterationSummary());

        // JSON 字段是合法 JSON 且关键内容可解出
        ObjectMapper m = new ObjectMapper();
        JsonNode scores = m.readTree(ev.getScoresJson());
        assertTrue(scores.isObject());
        assertEquals(18, scores.get("runnability").asInt());
        assertEquals(17, scores.get("layout").asInt());

        JsonNode probeJson = m.readTree(ev.getProbeSummaryJson());
        assertTrue(probeJson.isObject());
        assertTrue(probeJson.get("pageLoaded").asBoolean());
        assertEquals(5, probeJson.get("eventCount").asInt());

        JsonNode issuesJson = m.readTree(ev.getClassifiedIssuesJson());
        assertTrue(issuesJson.isArray());
        assertEquals(1, issuesJson.size());
        assertEquals("evaluation", issuesJson.get(0).get("category").asText());

        JsonNode tracesJson = m.readTree(ev.getIterTracesJson());
        assertTrue(tracesJson.isArray());
        assertEquals(1, tracesJson.size(), "iter_traces_json 长度 = iteration_count");
        assertEquals(85, tracesJson.get(0).get("scoreAfter").asInt());
        assertFalse(tracesJson.get(0).has("issuesSnapshot"),
                "iter_traces_json 不应含 issuesSnapshot 大字段");
    }

    // -----------------------------------------------------------------------
    // 2. 失败路径 — error_type 写入，scores 是 "{}"，traces 是 "[]"
    // -----------------------------------------------------------------------
    @Test
    void recordEvidence_failurePath_writesErrorTypeAndEmptyJson() {
        SessionEntity sess = newSession("evidence-failure");
        sessionRepository.insert(sess);

        AgentLoopResult result = AgentLoopResult.failureWithEvidence(
                "Playwright timeout", 3, "网络超时", null, null, "snake-adventure");

        String evalId = sessionService.recordEvidence(
                sess.getId(), null, "qwen3.6-max-preview", result);

        Optional<GameRunEvaluationEntity> found = evaluationRepository.findById(evalId);
        assertTrue(found.isPresent());
        GameRunEvaluationEntity ev = found.get();

        assertEquals(0, ev.getSuccess(), "success=0 对应失败路径");
        assertEquals("网络超时", ev.getErrorType(), "失败路径必须有 errorType");
        assertNull(ev.getGameRunId(), "失败路径 gameRunId 应为 NULL");
        assertEquals(3, ev.getIterationCount());
        assertEquals(0, ev.getTotalScore());
        assertEquals("snake-adventure", ev.getSkillName());

        // null obs/trace → 空对象/空数组
        assertEquals("{}", ev.getScoresJson());
        assertEquals("{}", ev.getProbeSummaryJson());
        assertEquals("[]", ev.getClassifiedIssuesJson());
        assertEquals("[]", ev.getIterTracesJson());
    }

    // -----------------------------------------------------------------------
    // 3. 降级评估 — degraded=1 + degraded_reason 非空
    // -----------------------------------------------------------------------
    @Test
    void recordEvidence_degradedObservation_marksDegraded() {
        SessionEntity sess = newSession("evidence-degraded");
        sessionRepository.insert(sess);

        EvaluationObservation degraded = EvaluationObservation.degraded(
                40, "Playwright 启动失败", List.of("[评估] 评估降级：Playwright 启动失败"));

        AgentLoopResult result = AgentLoopResult.successWithEvidence(
                "<html></html>", "fallback", 2, 40, degraded, new RunTrace(), "math-adventure");

        String evalId = sessionService.recordEvidence(
                sess.getId(), null, "kimi-k2.6", result);

        GameRunEvaluationEntity ev = evaluationRepository.findById(evalId).orElseThrow();
        assertEquals(1, ev.getDegraded(), "degraded observation 必须落 degraded=1");
        assertEquals("Playwright 启动失败", ev.getDegradedReason());
        assertEquals("math-adventure", ev.getSkillName());
        assertEquals("kimi-k2.6", ev.getModelKey());
    }

    // -----------------------------------------------------------------------
    // 4. EvidenceMapper null 安全
    // -----------------------------------------------------------------------
    @Test
    void evidenceMapper_handlesNullInputsWithoutNpe() {
        assertEquals("{}", EvidenceMapper.toScoresJson(null));
        assertEquals("{}", EvidenceMapper.toProbeSummaryJson(null));
        assertEquals("[]", EvidenceMapper.toClassifiedIssuesJson(null));
        assertEquals("[]", EvidenceMapper.toIterTracesJson(null));

        // obs 非空但内部为 null 也别炸
        EvaluationObservation empty = new EvaluationObservation();
        empty.setProbeSummary(null);
        assertEquals("{}", EvidenceMapper.toProbeSummaryJson(empty));
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------
    private static SessionEntity newSession(String title) {
        SessionEntity e = new SessionEntity();
        e.setTitle(title);
        e.setModelKey("qwen3.6-max-preview");
        Instant now = Instant.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        e.setMessageCount(0);
        e.setGameCount(0);
        return e;
    }
}

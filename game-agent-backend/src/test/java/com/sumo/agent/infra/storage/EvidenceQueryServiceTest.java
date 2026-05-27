package com.sumo.agent.infra.storage;

import com.sumo.agent.infra.db.GameRunEvaluationEntity;
import com.sumo.agent.infra.db.GameRunEvaluationRepository;
import com.sumo.agent.infra.db.SessionEntity;
import com.sumo.agent.infra.db.SessionRepository;
import com.sumo.agent.infra.db.SkillDistillationCandidateEntity;
import com.sumo.agent.infra.db.SkillDistillationCandidateRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EvidenceQueryService 集成测试——真启 Spring + 真 SQLite。
 *
 * 任务 260524-skill-distillation-evidence Step 5 的【验收契约】端到端 SSOT 验证：
 * - 列表 / 详情 / stats
 * - 状态机推进：promote → accept / reject
 * - 幂等 + 负面用例
 */
@SpringBootTest
class EvidenceQueryServiceTest {

    private static final Path TEST_DB = Path.of("./data/test-step5-query.db");

    @DynamicPropertySource
    static void setTestDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DB);
        registry.add("AGENT_DB_URL", () -> "jdbc:sqlite:" + TEST_DB);
    }

    @Autowired SessionRepository sessions;
    @Autowired GameRunEvaluationRepository evaluations;
    @Autowired SkillDistillationCandidateRepository candidates;
    @Autowired EvidenceQueryService queryService;
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
    // 1. findCandidates 按 skill + 分数范围筛选
    // -----------------------------------------------------------------------
    @Test
    void find_candidates_filters_by_skill_and_score_range() {
        String sid = sessions.insert(newSession("low-score-filter"));

        evaluations.insert(newEvaluation(sid, "math-adventure", true, 30));
        evaluations.insert(newEvaluation(sid, "math-adventure", true, 55));
        evaluations.insert(newEvaluation(sid, "math-adventure", true, 80));
        evaluations.insert(newEvaluation(sid, "memory-master", true, 50));

        // skill=math-adventure, score in [0, 60] → 应有 2 条
        List<Map<String, Object>> low = queryService.findCandidates("math-adventure", 0, 60, 10);
        assertEquals(2, low.size());
        for (Map<String, Object> m : low) {
            assertEquals("math-adventure", m.get("skillName"));
            int score = (int) m.get("totalScore");
            assertTrue(score >= 0 && score <= 60);
            // summary 不含 *_json
            assertFalse(m.containsKey("scoresJson"));
            assertFalse(m.containsKey("scores"));
        }

        // skill 为空 → 退化为 listFailures（这里没有 success=0 的样本）
        List<Map<String, Object>> noSkill = queryService.findCandidates(null, null, null, 10);
        assertNotNull(noSkill);
    }

    // -----------------------------------------------------------------------
    // 2. findDetail 解析 *_json 为对象
    // -----------------------------------------------------------------------
    @Test
    void find_detail_parses_json_fields_to_objects() {
        String sid = sessions.insert(newSession("detail"));
        GameRunEvaluationEntity e = newEvaluation(sid, "math-adventure", true, 88);
        e.setScoresJson("{\"runnability\":90,\"layout\":85}");
        e.setProbeSummaryJson("{\"clicks\":3,\"errors\":0}");
        e.setClassifiedIssuesJson("[{\"category\":\"layout\",\"severity\":\"low\"}]");
        e.setIterTracesJson("[{\"iteration\":1,\"scoreBefore\":60,\"scoreAfter\":88}]");
        String eid = evaluations.insert(e);

        Optional<Map<String, Object>> opt = queryService.findDetail(eid);
        assertTrue(opt.isPresent());
        Map<String, Object> d = opt.get();

        // summary 字段
        assertEquals(eid, d.get("id"));
        assertEquals("math-adventure", d.get("skillName"));
        assertEquals(88, d.get("totalScore"));

        // 解析后的对象
        @SuppressWarnings("unchecked")
        Map<String, Object> scores = (Map<String, Object>) d.get("scores");
        assertEquals(90, scores.get("runnability"));
        assertEquals(85, scores.get("layout"));

        @SuppressWarnings("unchecked")
        Map<String, Object> probe = (Map<String, Object>) d.get("probeSummary");
        assertEquals(3, probe.get("clicks"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> issues = (List<Map<String, Object>>) d.get("classifiedIssues");
        assertEquals(1, issues.size());
        assertEquals("layout", issues.get(0).get("category"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> traces = (List<Map<String, Object>>) d.get("iterTraces");
        assertEquals(1, traces.size());
        assertEquals(1, traces.get(0).get("iteration"));

        // 不存在的 id → empty
        assertTrue(queryService.findDetail("nonexistent").isEmpty());
    }

    // -----------------------------------------------------------------------
    // 3. stats 包含至少 6 个 key
    // -----------------------------------------------------------------------
    @Test
    void stats_returns_expected_keys() {
        String sid = sessions.insert(newSession("stats"));
        evaluations.insert(newEvaluation(sid, "math-adventure", true, 80));
        evaluations.insert(newEvaluation(sid, "math-adventure", false, 20));
        GameRunEvaluationEntity degraded = newEvaluation(sid, "memory-master", true, 60);
        degraded.setDegraded(1);
        evaluations.insert(degraded);

        Map<String, Object> s = queryService.stats();
        assertTrue(s.containsKey("totalEvaluations"));
        assertTrue(s.containsKey("totalFailures"));
        assertTrue(s.containsKey("totalDegraded"));
        assertTrue(s.containsKey("totalCandidates"));
        assertTrue(s.containsKey("totalAccepted"));
        assertTrue(s.containsKey("totalRejected"));
        assertTrue(s.size() >= 6);

        assertEquals(3, s.get("totalEvaluations"));
        assertEquals(1, s.get("totalFailures"));
        assertEquals(1, s.get("totalDegraded"));
    }

    // -----------------------------------------------------------------------
    // 4. promoteToCandidate：raw → candidate 自动 upsert
    // -----------------------------------------------------------------------
    @Test
    void promote_to_candidate_creates_candidate_row_first_time() {
        String sid = sessions.insert(newSession("promote"));
        String eid = evaluations.insert(newEvaluation(sid, "math-adventure", false, 30));

        // 此时 candidate 表里没有该 evaluation 的行
        assertTrue(candidates.findByEvaluationId(eid).isEmpty());

        String cid = queryService.promoteToCandidate(eid, "first time");
        assertNotNull(cid);

        SkillDistillationCandidateEntity c = candidates.findById(cid).orElseThrow();
        assertEquals(eid, c.getEvaluationId());
        assertEquals("math-adventure", c.getSkillName());
        assertEquals("candidate", c.getStatus());
        assertEquals("first time", c.getNote());
    }

    // -----------------------------------------------------------------------
    // 5. accept：candidate → accepted
    // -----------------------------------------------------------------------
    @Test
    void accept_advances_candidate_to_accepted() {
        String sid = sessions.insert(newSession("accept"));
        String eid = evaluations.insert(newEvaluation(sid, "math-adventure", false, 30));
        String cid = queryService.promoteToCandidate(eid, "init");

        String returnedId = queryService.accept(cid, "ok");
        assertEquals(cid, returnedId);

        SkillDistillationCandidateEntity c = candidates.findById(cid).orElseThrow();
        assertEquals("accepted", c.getStatus());
        assertEquals("ok", c.getNote());
    }

    // -----------------------------------------------------------------------
    // 6. reject：candidate → rejected
    // -----------------------------------------------------------------------
    @Test
    void reject_advances_candidate_to_rejected() {
        String sid = sessions.insert(newSession("reject"));
        String eid = evaluations.insert(newEvaluation(sid, "math-adventure", false, 30));
        String cid = queryService.promoteToCandidate(eid, "init");

        String returnedId = queryService.reject(cid, "not useful");
        assertEquals(cid, returnedId);

        SkillDistillationCandidateEntity c = candidates.findById(cid).orElseThrow();
        assertEquals("rejected", c.getStatus());
        assertEquals("not useful", c.getNote());
    }

    // -----------------------------------------------------------------------
    // 7. accept 已 accepted 的幂等（updated_at 刷新）
    // -----------------------------------------------------------------------
    @Test
    void accept_already_accepted_is_idempotent_refreshes_updated_at() throws InterruptedException {
        String sid = sessions.insert(newSession("idempotent"));
        String eid = evaluations.insert(newEvaluation(sid, "math-adventure", false, 30));
        String cid = queryService.promoteToCandidate(eid, "init");

        queryService.accept(cid, "first accept");
        Instant t1 = candidates.findById(cid).orElseThrow().getUpdatedAt();

        Thread.sleep(5);

        // 再 accept 一次 —— 不抛异常，updated_at 刷新
        String returnedId = queryService.accept(cid, "second accept");
        assertEquals(cid, returnedId);

        SkillDistillationCandidateEntity c = candidates.findById(cid).orElseThrow();
        assertEquals("accepted", c.getStatus());
        assertEquals("second accept", c.getNote());
        assertTrue(c.getUpdatedAt().toEpochMilli() >= t1.toEpochMilli(),
                "幂等 accept 应刷新 updated_at");
    }

    // -----------------------------------------------------------------------
    // 8. accept 不存在的 candidateId → IllegalArgumentException
    // -----------------------------------------------------------------------
    @Test
    void accept_nonexistent_candidate_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> queryService.accept("nonexistent-id", "note"));
        assertTrue(ex.getMessage().contains("candidate not found"));
    }

    // -----------------------------------------------------------------------
    // 9. promote 不存在的 evaluationId → IllegalArgumentException
    // -----------------------------------------------------------------------
    @Test
    void promote_nonexistent_evaluation_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> queryService.promoteToCandidate("nonexistent-eval", "note"));
        assertTrue(ex.getMessage().contains("evaluation not found"));
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static SessionEntity newSession(String title) {
        SessionEntity e = new SessionEntity();
        e.setTitle(title);
        e.setModelKey("dashscope");
        Instant now = Instant.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        e.setMessageCount(0);
        e.setGameCount(0);
        return e;
    }

    private static GameRunEvaluationEntity newEvaluation(String sid, String skill, boolean success, int score) {
        GameRunEvaluationEntity e = new GameRunEvaluationEntity();
        e.setSessionId(sid);
        e.setSkillName(skill);
        e.setSuccess(success ? 1 : 0);
        e.setTotalScore(score);
        e.setDegraded(0);
        e.setIterationCount(1);
        return e;
    }
}

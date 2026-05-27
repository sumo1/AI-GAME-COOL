package com.sumo.agent.infra.db;

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GameRunEvaluationRepository 烟测——真启 Spring + 真 SQLite。
 *
 * 沿用 RepositorySmokeTest 的 @DynamicPropertySource + 临时 DB 风格。
 * 任务 260524-skill-distillation-evidence Step 3 的【验收契约】端到端 SSOT 验证。
 */
@SpringBootTest
class GameRunEvaluationRepositoryTest {

    private static final Path TEST_DB = Path.of("./data/test-step3-eval.db");

    @DynamicPropertySource
    static void setTestDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DB);
        registry.add("AGENT_DB_URL", () -> "jdbc:sqlite:" + TEST_DB);
    }

    @Autowired SessionRepository sessions;
    @Autowired GameRunEvaluationRepository evaluations;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        // FK 级联自动处理，但显式按相反顺序删一遍兜底
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
    // 1. insert 新评估 → findById 返回完整字段（含 _json）
    // -----------------------------------------------------------------------
    @Test
    void insert_then_findById_returns_full_fields_including_json() {
        String sid = sessions.insert(newSession("eval-detail"));

        GameRunEvaluationEntity e = newEvaluation(sid, "math-adventure", true, 88);
        e.setScoresJson("{\"runnability\":90,\"layout\":85}");
        e.setProbeSummaryJson("{\"clicks\":3,\"errors\":0}");
        e.setClassifiedIssuesJson("[{\"category\":\"layout\",\"severity\":\"low\"}]");
        e.setIterTracesJson("[{\"iteration\":1,\"scoreBefore\":60,\"scoreAfter\":88}]");
        e.setFinalIterationSummary("迭代 2 轮后达标");
        e.setModelKey("dashscope");
        e.setIterationCount(2);

        String id = evaluations.insert(e);
        assertNotNull(id);
        assertFalse(id.isBlank());
        assertNotNull(e.getCreatedAt(), "insert 应回填 createdAt");

        Optional<GameRunEvaluationEntity> found = evaluations.findById(id);
        assertTrue(found.isPresent());
        GameRunEvaluationEntity got = found.get();
        assertEquals(id, got.getId());
        assertEquals(sid, got.getSessionId());
        assertEquals("math-adventure", got.getSkillName());
        assertEquals("dashscope", got.getModelKey());
        assertEquals(1, got.getSuccess());
        assertEquals(88, got.getTotalScore());
        assertEquals(0, got.getDegraded());
        assertEquals(2, got.getIterationCount());
        assertEquals("迭代 2 轮后达标", got.getFinalIterationSummary());

        // findById 必须读出 *_json 字段
        assertEquals("{\"runnability\":90,\"layout\":85}", got.getScoresJson());
        assertEquals("{\"clicks\":3,\"errors\":0}", got.getProbeSummaryJson());
        assertEquals("[{\"category\":\"layout\",\"severity\":\"low\"}]", got.getClassifiedIssuesJson());
        assertEquals("[{\"iteration\":1,\"scoreBefore\":60,\"scoreAfter\":88}]", got.getIterTracesJson());

        // 查不到 → empty
        assertTrue(evaluations.findById("nonexistent").isEmpty());
    }

    // -----------------------------------------------------------------------
    // 2. listBySession 不含 _json 大字段（沿用 GameRunRepository.listRecent 风格）
    // -----------------------------------------------------------------------
    @Test
    void list_by_session_excludes_json_fields() {
        String sid = sessions.insert(newSession("list-no-json"));

        GameRunEvaluationEntity e = newEvaluation(sid, "memory-master", true, 92);
        e.setScoresJson("{\"runnability\":95}".repeat(50));   // 模拟大字段
        e.setIterTracesJson("[".repeat(100));
        evaluations.insert(e);

        List<GameRunEvaluationEntity> list = evaluations.listBySession(sid);
        assertEquals(1, list.size());

        GameRunEvaluationEntity got = list.get(0);
        assertNull(got.getScoresJson(), "listBySession 不应读 scoresJson");
        assertNull(got.getProbeSummaryJson(), "listBySession 不应读 probeSummaryJson");
        assertNull(got.getClassifiedIssuesJson(), "listBySession 不应读 classifiedIssuesJson");
        assertNull(got.getIterTracesJson(), "listBySession 不应读 iterTracesJson");

        // 但其它字段应在
        assertEquals("memory-master", got.getSkillName());
        assertEquals(92, got.getTotalScore());
        assertEquals(1, got.getSuccess());
    }

    // -----------------------------------------------------------------------
    // 3. listBySkill 按 skill 过滤
    // -----------------------------------------------------------------------
    @Test
    void list_by_skill_filters_by_skill_name() {
        String sid = sessions.insert(newSession("skill-filter"));

        evaluations.insert(newEvaluation(sid, "math-adventure", true, 80));
        evaluations.insert(newEvaluation(sid, "math-adventure", false, 40));
        evaluations.insert(newEvaluation(sid, "memory-master", true, 90));

        List<GameRunEvaluationEntity> mathList = evaluations.listBySkill("math-adventure", 10);
        assertEquals(2, mathList.size());
        for (GameRunEvaluationEntity e : mathList) {
            assertEquals("math-adventure", e.getSkillName());
        }

        List<GameRunEvaluationEntity> memList = evaluations.listBySkill("memory-master", 10);
        assertEquals(1, memList.size());
        assertEquals("memory-master", memList.get(0).getSkillName());

        // limit 生效
        List<GameRunEvaluationEntity> limited = evaluations.listBySkill("math-adventure", 1);
        assertEquals(1, limited.size());

        // 不存在的 skill → 空列表
        assertTrue(evaluations.listBySkill("nonexistent-skill", 10).isEmpty());
    }

    // -----------------------------------------------------------------------
    // 4. listFailures 只返回 success=0
    // -----------------------------------------------------------------------
    @Test
    void list_failures_returns_only_success_zero() {
        String sid = sessions.insert(newSession("failures"));

        evaluations.insert(newEvaluation(sid, "math-adventure", true, 80));
        String f1 = evaluations.insert(newEvaluation(sid, "math-adventure", false, 30));
        evaluations.insert(newEvaluation(sid, "memory-master", true, 90));
        String f2 = evaluations.insert(newEvaluation(sid, "memory-master", false, 25));

        List<GameRunEvaluationEntity> failures = evaluations.listFailures(10);
        assertEquals(2, failures.size());
        for (GameRunEvaluationEntity e : failures) {
            assertEquals(0, e.getSuccess(), "listFailures 必须只返回 success=0");
        }

        // 验证两条都是失败的——id 顺序按 created_at DESC（最新先）
        List<String> ids = failures.stream().map(GameRunEvaluationEntity::getId).toList();
        assertTrue(ids.contains(f1));
        assertTrue(ids.contains(f2));
    }

    // -----------------------------------------------------------------------
    // 5. 删 sessions 时级联删 evaluations（FK CASCADE）
    // -----------------------------------------------------------------------
    @Test
    void delete_session_cascades_to_evaluations() {
        String sid = sessions.insert(newSession("cascade-eval"));
        String eid = evaluations.insert(newEvaluation(sid, "math-adventure", true, 80));

        // 验证插入成功
        assertEquals(1, evaluations.listBySession(sid).size());
        assertTrue(evaluations.findById(eid).isPresent());

        // 删 session
        int affected = sessions.deleteById(sid);
        assertEquals(1, affected);

        // 级联应使 evaluation 消失
        assertTrue(evaluations.findById(eid).isEmpty(), "FK CASCADE 应级联删 evaluation");
        assertEquals(0, evaluations.listBySession(sid).size());
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

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
 * SkillDistillationCandidateRepository 烟测——真启 Spring + 真 SQLite。
 *
 * 任务 260524-skill-distillation-evidence Step 3 的【验收契约】端到端 SSOT 验证。
 */
@SpringBootTest
class SkillDistillationCandidateRepositoryTest {

    private static final Path TEST_DB = Path.of("./data/test-step3-candidate.db");

    @DynamicPropertySource
    static void setTestDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DB);
        registry.add("AGENT_DB_URL", () -> "jdbc:sqlite:" + TEST_DB);
    }

    @Autowired SessionRepository sessions;
    @Autowired GameRunEvaluationRepository evaluations;
    @Autowired SkillDistillationCandidateRepository candidates;
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
    // 1. insert 默认 status='raw'
    // -----------------------------------------------------------------------
    @Test
    void insert_defaults_status_to_raw() {
        String sid = sessions.insert(newSession("candidate-raw"));
        String eid = evaluations.insert(newEvaluation(sid, "math-adventure", true, 80));

        SkillDistillationCandidateEntity c = new SkillDistillationCandidateEntity();
        c.setEvaluationId(eid);
        c.setSkillName("math-adventure");
        // 不显式设置 status —— Repository 应兜底为 'raw'

        String id = candidates.insert(c);
        assertNotNull(id);
        assertEquals("raw", c.getStatus(), "insert 应回填 status='raw'");
        assertNotNull(c.getCreatedAt());
        assertNotNull(c.getUpdatedAt());

        Optional<SkillDistillationCandidateEntity> found = candidates.findById(id);
        assertTrue(found.isPresent());
        SkillDistillationCandidateEntity got = found.get();
        assertEquals("raw", got.getStatus());
        assertEquals("math-adventure", got.getSkillName());
        assertEquals(eid, got.getEvaluationId());
    }

    // -----------------------------------------------------------------------
    // 2. updateStatus 推进 raw → candidate → accepted
    // -----------------------------------------------------------------------
    @Test
    void update_status_advances_state_machine() throws InterruptedException {
        String sid = sessions.insert(newSession("state-machine"));
        String eid = evaluations.insert(newEvaluation(sid, "memory-master", true, 85));

        SkillDistillationCandidateEntity c = new SkillDistillationCandidateEntity();
        c.setEvaluationId(eid);
        c.setSkillName("memory-master");
        String id = candidates.insert(c);
        Instant t0 = c.getUpdatedAt();

        // 等 1ms 让 updated_at 能被观察到变化
        Thread.sleep(2);

        // raw → candidate
        assertEquals(1, candidates.updateStatus(id, "candidate", "由 dreamer 筛出"));
        SkillDistillationCandidateEntity step1 = candidates.findById(id).orElseThrow();
        assertEquals("candidate", step1.getStatus());
        assertEquals("由 dreamer 筛出", step1.getNote());
        assertTrue(step1.getUpdatedAt().toEpochMilli() >= t0.toEpochMilli(),
                "updateStatus 必须刷新 updated_at");

        Thread.sleep(2);

        // candidate → accepted
        assertEquals(1, candidates.updateStatus(id, "accepted", "已合入 SKILL.md"));
        SkillDistillationCandidateEntity step2 = candidates.findById(id).orElseThrow();
        assertEquals("accepted", step2.getStatus());
        assertEquals("已合入 SKILL.md", step2.getNote());

        // 不存在 id → 0 行
        assertEquals(0, candidates.updateStatus("nonexistent", "rejected", null));
    }

    // -----------------------------------------------------------------------
    // 3. listBySkill(name, null, 10) 不过滤 status
    // -----------------------------------------------------------------------
    @Test
    void list_by_skill_with_null_status_does_not_filter() {
        String sid = sessions.insert(newSession("list-no-filter"));
        String eid = evaluations.insert(newEvaluation(sid, "math-adventure", true, 80));

        // 三种状态各插一条
        String c1 = candidates.insert(newCandidate(eid, "math-adventure", "raw"));
        String c2 = candidates.insert(newCandidate(eid, "math-adventure", "candidate"));
        String c3 = candidates.insert(newCandidate(eid, "math-adventure", "accepted"));
        // 另一个 skill 一条 —— 不应被返回
        candidates.insert(newCandidate(eid, "memory-master", "raw"));

        List<SkillDistillationCandidateEntity> all = candidates.listBySkill("math-adventure", null, 10);
        assertEquals(3, all.size(), "status=null 不过滤");

        List<String> ids = all.stream().map(SkillDistillationCandidateEntity::getId).toList();
        assertTrue(ids.contains(c1));
        assertTrue(ids.contains(c2));
        assertTrue(ids.contains(c3));

        // status 空字符串等价于 null
        List<SkillDistillationCandidateEntity> blank = candidates.listBySkill("math-adventure", "", 10);
        assertEquals(3, blank.size());
    }

    // -----------------------------------------------------------------------
    // 4. listBySkill(name, "candidate", 10) 过滤
    // -----------------------------------------------------------------------
    @Test
    void list_by_skill_with_status_filters() {
        String sid = sessions.insert(newSession("list-with-filter"));
        String eid = evaluations.insert(newEvaluation(sid, "math-adventure", true, 80));

        candidates.insert(newCandidate(eid, "math-adventure", "raw"));
        String cid = candidates.insert(newCandidate(eid, "math-adventure", "candidate"));
        candidates.insert(newCandidate(eid, "math-adventure", "accepted"));
        candidates.insert(newCandidate(eid, "math-adventure", "rejected"));

        List<SkillDistillationCandidateEntity> filtered = candidates.listBySkill("math-adventure", "candidate", 10);
        assertEquals(1, filtered.size());
        assertEquals(cid, filtered.get(0).getId());
        assertEquals("candidate", filtered.get(0).getStatus());

        // limit 生效
        List<SkillDistillationCandidateEntity> all = candidates.listBySkill("math-adventure", null, 2);
        assertEquals(2, all.size());

        // 没匹配 → 空
        assertTrue(candidates.listBySkill("math-adventure", "nonexistent-status", 10).isEmpty());
    }

    // -----------------------------------------------------------------------
    // 5. deleteById
    // -----------------------------------------------------------------------
    @Test
    void delete_by_id_removes_row() {
        String sid = sessions.insert(newSession("delete-test"));
        String eid = evaluations.insert(newEvaluation(sid, "math-adventure", true, 80));
        String cid = candidates.insert(newCandidate(eid, "math-adventure", "raw"));

        assertTrue(candidates.findById(cid).isPresent());
        assertEquals(1, candidates.deleteById(cid));
        assertTrue(candidates.findById(cid).isEmpty());

        // 删不存在 id → 0
        assertEquals(0, candidates.deleteById("nonexistent"));
    }

    // -----------------------------------------------------------------------
    // 6. 删 evaluation 级联删 candidate（FK CASCADE）
    // -----------------------------------------------------------------------
    @Test
    void delete_evaluation_cascades_to_candidate() {
        String sid = sessions.insert(newSession("cascade-candidate"));
        String eid = evaluations.insert(newEvaluation(sid, "math-adventure", true, 80));
        String cid = candidates.insert(newCandidate(eid, "math-adventure", "raw"));

        assertTrue(candidates.findById(cid).isPresent());

        // evaluations 没暴露 deleteById；用 JdbcTemplate 直接删
        int affected = jdbc.update("DELETE FROM game_run_evaluations WHERE id = ?", eid);
        assertEquals(1, affected);

        // candidate 应级联消失
        assertTrue(candidates.findById(cid).isEmpty(), "FK CASCADE 应级联删 candidate");

        // 重新插入一条候选用于测试 session→evaluation→candidate 双层级联
        String eid2 = evaluations.insert(newEvaluation(sid, "memory-master", true, 90));
        String cid2 = candidates.insert(newCandidate(eid2, "memory-master", "candidate"));
        assertTrue(candidates.findById(cid2).isPresent());

        // 删 session → evaluation 级联删 → candidate 也级联删
        sessions.deleteById(sid);
        assertTrue(candidates.findById(cid2).isEmpty(),
                "session→evaluation→candidate 双层 FK CASCADE 应生效");
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

    private static SkillDistillationCandidateEntity newCandidate(String eid, String skill, String status) {
        SkillDistillationCandidateEntity c = new SkillDistillationCandidateEntity();
        c.setEvaluationId(eid);
        c.setSkillName(skill);
        c.setStatus(status);
        return c;
    }
}

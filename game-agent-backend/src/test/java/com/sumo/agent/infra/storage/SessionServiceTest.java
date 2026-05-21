package com.sumo.agent.infra.storage;

import com.sumo.agent.agent.loop.AgentLoopResult;
import com.sumo.agent.infra.db.GameRunRepository;
import com.sumo.agent.infra.db.MessageEntity;
import com.sumo.agent.infra.db.MessageRepository;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SessionService 烟测——真启 Spring 上下文 + 真 SQLite（不 mock AgentLoop，但用 fake AgentLoopResult）。
 * 所有断言查 DB 真值，不信 service 返回。
 */
@SpringBootTest
class SessionServiceTest {

    private static final Path TEST_DB = Path.of("./data/test-step3-service.db");

    @DynamicPropertySource
    static void setTestDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DB);
    }

    @Autowired SessionService sessionService;
    @Autowired SessionRepository sessions;
    @Autowired MessageRepository messages;
    @Autowired GameRunRepository gameRuns;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
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
    // 1. ensureSession_creates_when_missing
    // -----------------------------------------------------------------------
    @Test
    void ensureSession_creates_when_missing() {
        // 长 userInput（>40 字符）验证 title 截断
        String longInput = "做一个10以内加法游戏并且要有动物主题、计分系统和难度递进，再加点鼓励性的反馈和音效，让小朋友更有动力坚持玩下去。";
        // 校验输入确实 > 40 字符（避免 SUT 不应触发的情形）
        assertTrue(longInput.length() > 40, "测试输入应 > 40 字符，实际：" + longInput.length());
        SessionEntity entity = sessionService.ensureSession(null, longInput, "dashscope");

        assertNotNull(entity.getId());
        assertFalse(entity.getId().isBlank());

        // 查 DB 真值
        Long count = jdbc.queryForObject("SELECT count(*) FROM sessions", Long.class);
        assertEquals(1L, count);

        SessionEntity got = sessions.findById(entity.getId()).orElseThrow();
        assertEquals("dashscope", got.getModelKey());
        // title 长度 ≤ 43（40 + "..."）
        assertTrue(got.getTitle().length() <= 43,
                "title 应被截断，实际长度 " + got.getTitle().length() + ": " + got.getTitle());
        assertTrue(got.getTitle().endsWith("..."), "长 title 应以 ... 结尾");
        assertEquals(0, got.getMessageCount());
        assertEquals(0, got.getGameCount());
    }

    // -----------------------------------------------------------------------
    // 2. ensureSession_touches_existing
    // -----------------------------------------------------------------------
    @Test
    void ensureSession_touches_existing() throws InterruptedException {
        SessionEntity first = sessionService.ensureSession(null, "数学游戏", "dashscope");
        long firstUpdate = first.getUpdatedAt().toEpochMilli();
        String id = first.getId();

        // 等 2ms 确保时间戳变化
        Thread.sleep(2);

        SessionEntity second = sessionService.ensureSession(id, "继续上次的", "dashscope");
        assertEquals(id, second.getId(), "同 id 不应新建");

        // DB 中 sessions 仍只有 1 行
        Long count = jdbc.queryForObject("SELECT count(*) FROM sessions", Long.class);
        assertEquals(1L, count);

        // updated_at 已变大
        SessionEntity got = sessions.findById(id).orElseThrow();
        assertTrue(got.getUpdatedAt().toEpochMilli() > firstUpdate,
                "updated_at 应被 touch 更新；before=" + firstUpdate + " after=" + got.getUpdatedAt().toEpochMilli());
    }

    // -----------------------------------------------------------------------
    // 3. recordRun_writes_two_messages_and_one_game_run
    // -----------------------------------------------------------------------
    @Test
    void recordRun_writes_two_messages_and_one_game_run() {
        SessionEntity s = sessionService.ensureSession(null, "数学游戏", "dashscope");

        AgentLoopResult result = AgentLoopResult.success(
                "<!DOCTYPE html><html><body>game</body></html>",
                "已生成 10 以内加法游戏",
                3, 88
        );
        SessionService.RecordResult rec = sessionService.recordRun(s.getId(), "数学游戏", result, "dashscope");

        assertNotNull(rec.userMessageId());
        assertNotNull(rec.assistantMessageId());
        assertNotNull(rec.gameRunId());

        // 查 DB 真值——messages 共 2 条
        List<MessageEntity> msgs = messages.listBySession(s.getId());
        assertEquals(2, msgs.size());
        // 顺序：user 在前（按 created_at ASC）
        assertEquals("user", msgs.get(0).getRole());
        assertEquals("数学游戏", msgs.get(0).getContent());
        assertNull(msgs.get(0).getIterations(), "user 消息 iterations 应为 null");
        assertNull(msgs.get(0).getEvalScore(), "user 消息 eval_score 应为 null");

        assertEquals("assistant", msgs.get(1).getRole());
        assertEquals("已生成 10 以内加法游戏", msgs.get(1).getContent());
        assertEquals(3, msgs.get(1).getIterations());
        assertEquals(88, msgs.get(1).getEvalScore());

        // game_runs 共 1 条
        Long gameCount = jdbc.queryForObject(
                "SELECT count(*) FROM game_runs WHERE session_id = ?", Long.class, s.getId());
        assertEquals(1L, gameCount);

        // game_run html 不空 + 含 DOCTYPE
        var game = gameRuns.findHtmlById(rec.gameRunId()).orElseThrow();
        assertNotNull(game.getHtml());
        assertTrue(game.getHtml().contains("<!DOCTYPE html>"));

        // game_run 字段：favorited 默认 0
        Long favorited = jdbc.queryForObject(
                "SELECT favorited FROM game_runs WHERE id = ?", Long.class, rec.gameRunId());
        assertEquals(0L, favorited);
    }

    // -----------------------------------------------------------------------
    // 4. recordRun_skips_game_when_failure
    // -----------------------------------------------------------------------
    @Test
    void recordRun_skips_game_when_failure() {
        SessionEntity s = sessionService.ensureSession(null, "失败请求", "dashscope");

        AgentLoopResult result = AgentLoopResult.failure("LLM timeout", 1);
        SessionService.RecordResult rec = sessionService.recordRun(s.getId(), "失败请求", result, "dashscope");

        // user + assistant 都写了
        assertNotNull(rec.userMessageId());
        assertNotNull(rec.assistantMessageId());
        // game_run 不写
        assertNull(rec.gameRunId(), "失败时不应写 game_run");

        // DB 真值确认
        Long gameCount = jdbc.queryForObject(
                "SELECT count(*) FROM game_runs WHERE session_id = ?", Long.class, s.getId());
        assertEquals(0L, gameCount);

        // assistant 消息保留了错误标识
        List<MessageEntity> msgs = messages.listBySession(s.getId());
        assertEquals(2, msgs.size());
        assertTrue(msgs.get(1).getContent().contains("LLM timeout") || msgs.get(1).getContent().contains("失败"),
                "assistant 消息应记录失败原因，实际：" + msgs.get(1).getContent());
    }

    // -----------------------------------------------------------------------
    // 5. recordRun_increments_counters
    // -----------------------------------------------------------------------
    @Test
    void recordRun_increments_counters() {
        SessionEntity s = sessionService.ensureSession(null, "计数测试", "dashscope");
        assertEquals(0, sessions.findById(s.getId()).orElseThrow().getMessageCount());

        // 第一轮 —— 成功
        sessionService.recordRun(
                s.getId(), "test1",
                AgentLoopResult.success("<!DOCTYPE html><html></html>", "ok", 2, 80),
                "dashscope"
        );
        SessionEntity after1 = sessions.findById(s.getId()).orElseThrow();
        assertEquals(2, after1.getMessageCount());
        assertEquals(1, after1.getGameCount());

        // 第二轮 —— 失败
        sessionService.recordRun(
                s.getId(), "test2",
                AgentLoopResult.failure("nope", 0),
                "dashscope"
        );
        SessionEntity after2 = sessions.findById(s.getId()).orElseThrow();
        assertEquals(4, after2.getMessageCount(), "失败也写 user+assistant message");
        assertEquals(1, after2.getGameCount(), "失败不增 game_count");

        // 第三轮 —— 成功
        sessionService.recordRun(
                s.getId(), "test3",
                AgentLoopResult.success("<!DOCTYPE html><html></html>", "ok2", 1, 90),
                "dashscope"
        );
        SessionEntity after3 = sessions.findById(s.getId()).orElseThrow();
        assertEquals(6, after3.getMessageCount());
        assertEquals(2, after3.getGameCount());

        // 与实际 DB 行数一致
        Long actualMsgs = jdbc.queryForObject(
                "SELECT count(*) FROM messages WHERE session_id = ?", Long.class, s.getId());
        Long actualGames = jdbc.queryForObject(
                "SELECT count(*) FROM game_runs WHERE session_id = ?", Long.class, s.getId());
        assertEquals(actualMsgs.intValue(), after3.getMessageCount());
        assertEquals(actualGames.intValue(), after3.getGameCount());
    }
}

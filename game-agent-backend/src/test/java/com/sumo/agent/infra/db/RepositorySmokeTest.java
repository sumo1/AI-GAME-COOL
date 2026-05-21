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
 * Repository 烟测——真启 Spring 上下文 + 真 SQLite（不 mock）。
 *
 * 用临时 DB 文件避开开发库污染；每个 @Test 前清表。
 */
@SpringBootTest
class RepositorySmokeTest {

    /** 临时测试 DB 路径（与 plan 端到端段一致：./game-agent-backend/data/test-step2.db）。 */
    private static final Path TEST_DB = Path.of("./data/test-step2.db");

    @DynamicPropertySource
    static void setTestDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DB);
        registry.add("AGENT_DB_URL", () -> "jdbc:sqlite:" + TEST_DB);
    }

    @Autowired SessionRepository sessions;
    @Autowired MessageRepository messages;
    @Autowired GameRunRepository gameRuns;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        // FK 级联会自动清 messages / game_runs；显式按相反顺序删一遍兜底
        jdbc.update("DELETE FROM game_runs");
        jdbc.update("DELETE FROM messages");
        jdbc.update("DELETE FROM sessions");
    }

    /** 测试结束后清理临时 DB 文件（含 WAL/SHM 副产物），避免污染开发库目录。 */
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
    // 1. insert_session_then_findById
    // -----------------------------------------------------------------------
    @Test
    void insert_session_then_findById() {
        Instant now = Instant.ofEpochMilli(1_700_000_000_000L);
        SessionEntity e = new SessionEntity();
        e.setTitle("数学冒险");
        e.setModelKey("dashscope");
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        e.setMessageCount(0);
        e.setGameCount(0);

        String id = sessions.insert(e);
        assertNotNull(id);
        assertFalse(id.isBlank());

        Optional<SessionEntity> found = sessions.findById(id);
        assertTrue(found.isPresent());
        SessionEntity got = found.get();
        assertEquals(id, got.getId());
        assertEquals("数学冒险", got.getTitle());
        assertEquals("dashscope", got.getModelKey());
        assertEquals(now, got.getCreatedAt());
        assertEquals(now, got.getUpdatedAt());
        assertEquals(0, got.getMessageCount());
        assertEquals(0, got.getGameCount());
    }

    // -----------------------------------------------------------------------
    // 2. insert_message_does_not_auto_increment_session_counter
    //    （计数由 Service 层维护，Repository.insertMessage 不自动 +1；
    //     incrementCounters 单独调用才会更新计数）
    // -----------------------------------------------------------------------
    @Test
    void insert_message_does_not_auto_increment_session_counter() {
        String sid = sessions.insert(newSession("counter-test"));

        // insert message —— 不应改 sessions.message_count
        MessageEntity msg = new MessageEntity();
        msg.setSessionId(sid);
        msg.setRole("user");
        msg.setContent("hello");
        messages.insert(msg);

        SessionEntity s = sessions.findById(sid).orElseThrow();
        assertEquals(0, s.getMessageCount(), "Repository 不应自动改 message_count");

        // 显式调 incrementCounters 才更新
        sessions.incrementCounters(sid, 1, 0);
        s = sessions.findById(sid).orElseThrow();
        assertEquals(1, s.getMessageCount());
        assertEquals(0, s.getGameCount());
    }

    // -----------------------------------------------------------------------
    // 3. list_recent_sorted_by_updated_at
    // -----------------------------------------------------------------------
    @Test
    void list_recent_sorted_by_updated_at() throws InterruptedException {
        long base = System.currentTimeMillis();
        SessionEntity a = newSessionWithTime("A", base);
        SessionEntity b = newSessionWithTime("B", base + 10);
        SessionEntity c = newSessionWithTime("C", base + 20);
        String idA = sessions.insert(a);
        String idB = sessions.insert(b);
        String idC = sessions.insert(c);

        // touch B → 让它最新
        long touchTime = base + 100;
        sessions.touch(idB, touchTime);

        List<SessionEntity> list = sessions.listRecent(10);
        assertEquals(3, list.size());
        // 顺序：B（被 touch）→ C → A
        assertEquals(idB, list.get(0).getId());
        assertEquals(idC, list.get(1).getId());
        assertEquals(idA, list.get(2).getId());
        assertEquals(touchTime, list.get(0).getUpdatedAt().toEpochMilli());
    }

    // -----------------------------------------------------------------------
    // 4. list_recent_excludes_html
    // -----------------------------------------------------------------------
    @Test
    void list_recent_excludes_html() {
        String sid = sessions.insert(newSession("with-html"));
        String mid = messages.insert(newMessage(sid, "user", "play"));

        GameRunEntity g = new GameRunEntity();
        g.setSessionId(sid);
        g.setMessageId(mid);
        g.setTitle("数学游戏");
        g.setHtml("<!DOCTYPE html><html>大体积内容".repeat(10));
        g.setEvalScore(85);
        g.setIterations(2);
        g.setFavorited(false);
        gameRuns.insert(g);

        List<GameRunEntity> list = gameRuns.listRecent(10);
        assertEquals(1, list.size());
        GameRunEntity got = list.get(0);
        assertNull(got.getHtml(), "listRecent 返回的 entity 不应含 html");
        // 但其它字段应在
        assertEquals("数学游戏", got.getTitle());
        assertEquals(85, got.getEvalScore());
        assertEquals(2, got.getIterations());
        assertFalse(got.isFavorited());

        // listBySession 也不应含 html
        List<GameRunEntity> bySession = gameRuns.listBySession(sid);
        assertEquals(1, bySession.size());
        assertNull(bySession.get(0).getHtml());
    }

    // -----------------------------------------------------------------------
    // 5. find_html_by_id_returns_html
    // -----------------------------------------------------------------------
    @Test
    void find_html_by_id_returns_html() {
        String sid = sessions.insert(newSession("html-detail"));
        String mid = messages.insert(newMessage(sid, "assistant", "ok"));
        GameRunEntity g = new GameRunEntity();
        g.setSessionId(sid);
        g.setMessageId(mid);
        g.setTitle("详情查询");
        g.setHtml("<!DOCTYPE html><html><body>x</body></html>");
        g.setEvalScore(80);
        g.setIterations(1);
        g.setFavorited(false);
        String gid = gameRuns.insert(g);

        Optional<GameRunEntity> got = gameRuns.findHtmlById(gid);
        assertTrue(got.isPresent());
        assertEquals(gid, got.get().getId());
        assertNotNull(got.get().getHtml());
        assertTrue(got.get().getHtml().contains("<!DOCTYPE html>"));
        // findHtmlById 仅返回 id+html，其它字段为 null/默认
        assertNull(got.get().getTitle());

        // findById（详情）应返回完整字段含 html
        Optional<GameRunEntity> full = gameRuns.findById(gid);
        assertTrue(full.isPresent());
        assertNotNull(full.get().getHtml());
        assertEquals("详情查询", full.get().getTitle());
        assertEquals(80, full.get().getEvalScore());

        // 查不到的 id —— 应返回 empty
        assertTrue(gameRuns.findHtmlById("nonexistent").isEmpty());
        assertTrue(gameRuns.findById("nonexistent").isEmpty());
        assertTrue(sessions.findById("nonexistent").isEmpty());
    }

    // -----------------------------------------------------------------------
    // 6. set_favorited_then_list_favorites
    // -----------------------------------------------------------------------
    @Test
    void set_favorited_then_list_favorites() {
        String sid = sessions.insert(newSession("fav-test"));
        String mid = messages.insert(newMessage(sid, "assistant", "ok"));

        String g1 = gameRuns.insert(newGameRun(sid, mid, "game1", 70, false));
        String g2 = gameRuns.insert(newGameRun(sid, mid, "game2", 90, false));
        String g3 = gameRuns.insert(newGameRun(sid, mid, "game3", 85, false));

        // 标记 g1 和 g3 为收藏
        assertEquals(1, gameRuns.setFavorited(g1, true));
        assertEquals(1, gameRuns.setFavorited(g3, true));

        List<GameRunEntity> favs = gameRuns.listFavorites(10);
        assertEquals(2, favs.size());
        // 排序：eval_score DESC（g3=85 在前，g1=70 在后）
        assertEquals(g3, favs.get(0).getId());
        assertEquals(g1, favs.get(1).getId());
        // 列表不应含 html
        assertNull(favs.get(0).getHtml());

        // 取消收藏 g1
        assertEquals(1, gameRuns.setFavorited(g1, false));
        favs = gameRuns.listFavorites(10);
        assertEquals(1, favs.size());
        assertEquals(g3, favs.get(0).getId());

        // 对不存在 id setFavorited —— 返回 0，不抛
        assertEquals(0, gameRuns.setFavorited("nonexistent", true));
    }

    // -----------------------------------------------------------------------
    // 7. delete_session_cascades_to_messages_and_game_runs
    //    （契约负面用例第 4 条）
    // -----------------------------------------------------------------------
    @Test
    void delete_session_cascades_to_messages_and_game_runs() {
        String sid = sessions.insert(newSession("cascade-test"));
        String mid = messages.insert(newMessage(sid, "user", "x"));
        String gid = gameRuns.insert(newGameRun(sid, mid, "g", 80, false));

        // 验证插入
        assertEquals(1, messages.listBySession(sid).size());
        assertEquals(1, gameRuns.listBySession(sid).size());

        // 删 session
        int affected = sessions.deleteById(sid);
        assertEquals(1, affected);

        // 级联应使 messages / game_runs 也消失
        assertTrue(sessions.findById(sid).isEmpty());
        assertEquals(0, messages.listBySession(sid).size());
        assertEquals(0, gameRuns.listBySession(sid).size());
        assertTrue(gameRuns.findById(gid).isEmpty());

        // 删不存在的 id —— 返回 0
        assertEquals(0, sessions.deleteById("nonexistent"));

        // incrementCounters 对不存在 id —— 不抛（受影响 0 行）
        assertDoesNotThrow(() -> sessions.incrementCounters("nonexistent", 1, 1));
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static SessionEntity newSession(String title) {
        return newSessionWithTime(title, System.currentTimeMillis());
    }

    private static SessionEntity newSessionWithTime(String title, long ts) {
        SessionEntity e = new SessionEntity();
        e.setTitle(title);
        e.setModelKey("dashscope");
        e.setCreatedAt(Instant.ofEpochMilli(ts));
        e.setUpdatedAt(Instant.ofEpochMilli(ts));
        e.setMessageCount(0);
        e.setGameCount(0);
        return e;
    }

    private static MessageEntity newMessage(String sessionId, String role, String content) {
        MessageEntity m = new MessageEntity();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    private static GameRunEntity newGameRun(String sid, String mid, String title, int score, boolean fav) {
        GameRunEntity g = new GameRunEntity();
        g.setSessionId(sid);
        g.setMessageId(mid);
        g.setTitle(title);
        g.setHtml("<!DOCTYPE html><html><body>" + title + "</body></html>");
        g.setEvalScore(score);
        g.setIterations(1);
        g.setFavorited(fav);
        return g;
    }
}

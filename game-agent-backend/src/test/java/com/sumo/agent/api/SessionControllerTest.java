package com.sumo.agent.api;

import com.sumo.agent.infra.db.GameRunEntity;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * SessionController 烟测——真启 Spring 上下文 + 真 SQLite + RANDOM_PORT HTTP。
 *
 * 所有断言查 DB 真值或解析真实 HTTP 响应；不 mock controller。
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
class SessionControllerTest {

    private static final Path TEST_DB = Path.of("./data/test-step4a.db");

    @DynamicPropertySource
    static void setTestDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DB);
        registry.add("AGENT_DB_URL", () -> "jdbc:sqlite:" + TEST_DB);
    }

    @LocalServerPort int port;

    /**
     * Spring Boot 4 移除了 TestRestTemplate；用普通 RestTemplate + 不抛异常的错误处理器 + RANDOM_PORT。
     */
    private final RestTemplate http = buildRestTemplate();

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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * RestTemplate 默认对非 2xx 抛异常；这里安装一个空 handler，让响应原样返回 status + body，
     * 与 TestRestTemplate 行为一致。
     */
    private static RestTemplate buildRestTemplate() {
        RestTemplate t = new RestTemplate(new SimpleClientHttpRequestFactory());
        t.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
            @Override
            public void handleError(java.net.URI url, org.springframework.http.HttpMethod method,
                                    org.springframework.http.client.ClientHttpResponse response) {
                // 不抛
            }
        });
        return t;
    }

    // -----------------------------------------------------------------------
    // 1. list_sessions_returns_recent_first
    // -----------------------------------------------------------------------
    @Test
    void list_sessions_returns_recent_first() {
        long base = System.currentTimeMillis();
        String idA = sessions.insert(newSession("A", base));
        String idB = sessions.insert(newSession("B", base + 10));
        String idC = sessions.insert(newSession("C", base + 20));

        // touch B → 让它最新
        sessions.touch(idB, base + 100);

        ResponseEntity<Map> resp = http.getForEntity(url("/api/sessions?limit=10"), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals(Boolean.TRUE, body.get("success"));
        assertEquals(3, body.get("count"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        assertEquals(idB, data.get(0).get("id"), "B 被 touch，应排第一");
        assertEquals(idC, data.get(1).get("id"));
        assertEquals(idA, data.get(2).get("id"));

        // 字段：含 messageCount/gameCount，时间戳为 Long ms
        Map<String, Object> first = data.get(0);
        assertTrue(first.containsKey("messageCount"));
        assertTrue(first.containsKey("gameCount"));
        assertTrue(first.get("createdAt") instanceof Number, "createdAt 应是数字（ms epoch）");
        assertNull(first.get("html"), "列表不应含 html 字段");
    }

    // -----------------------------------------------------------------------
    // 2. get_messages_returns_in_chronological_order
    // -----------------------------------------------------------------------
    @Test
    void get_messages_returns_in_chronological_order() {
        String sid = sessions.insert(newSession("chrono", System.currentTimeMillis()));

        long t0 = Instant.now().toEpochMilli();
        MessageEntity user = new MessageEntity();
        user.setSessionId(sid);
        user.setRole("user");
        user.setContent("加法游戏");
        user.setCreatedAt(Instant.ofEpochMilli(t0));
        messages.insert(user);

        MessageEntity asst = new MessageEntity();
        asst.setSessionId(sid);
        asst.setRole("assistant");
        asst.setContent("已生成");
        asst.setIterations(2);
        asst.setEvalScore(85);
        asst.setCreatedAt(Instant.ofEpochMilli(t0 + 5));
        messages.insert(asst);

        ResponseEntity<Map> resp = http.getForEntity(url("/api/sessions/" + sid + "/messages"), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        Map<String, Object> body = resp.getBody();
        assertEquals(Boolean.TRUE, body.get("success"));
        assertEquals(2, body.get("count"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        assertEquals("user", data.get(0).get("role"));
        assertEquals("加法游戏", data.get(0).get("content"));
        assertNull(data.get(0).get("iterations"));
        assertNull(data.get(0).get("evalScore"));

        assertEquals("assistant", data.get(1).get("role"));
        assertEquals(2, data.get(1).get("iterations"));
        assertEquals(85, data.get(1).get("evalScore"));
        assertTrue(data.get(1).get("createdAt") instanceof Number);
    }

    // -----------------------------------------------------------------------
    // 3. clone_session_copies_messages_not_games
    // -----------------------------------------------------------------------
    @Test
    void clone_session_copies_messages_not_games() {
        String sid = sessions.insert(newSession("source", System.currentTimeMillis()));

        // 原会话 2 messages + 1 game_run
        // 两条 message 显式拉开毫秒（默认 Instant.now() 在毫秒级可能相等，
        // SQLite ORDER BY created_at ASC 在等值时不保证稳定 → CI flakiness）
        long t0 = System.currentTimeMillis();
        MessageEntity userMsg = makeMsg(sid, "user", "玩游戏");
        userMsg.setCreatedAt(java.time.Instant.ofEpochMilli(t0));
        String mid1 = messages.insert(userMsg);

        MessageEntity asstMsg = makeMsg(sid, "assistant", "好的");
        asstMsg.setCreatedAt(java.time.Instant.ofEpochMilli(t0 + 5));
        String mid2 = messages.insert(asstMsg);

        gameRuns.insert(makeGameRun(sid, mid2, "原游戏", 80, false));

        ResponseEntity<Map> resp = http.postForEntity(url("/api/sessions/" + sid + "/clone"), null, Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        String newSid = (String) data.get("newSessionId");
        assertNotNull(newSid);
        assertNotEquals(sid, newSid);
        assertEquals(sid, data.get("sourceSessionId"));
        assertEquals(2, data.get("copiedMessages"));

        // DB 真值：新 session 有 2 messages + 0 game_runs
        Long newMsgs = jdbc.queryForObject(
                "SELECT count(*) FROM messages WHERE session_id = ?", Long.class, newSid);
        Long newGames = jdbc.queryForObject(
                "SELECT count(*) FROM game_runs WHERE session_id = ?", Long.class, newSid);
        assertEquals(2L, newMsgs);
        assertEquals(0L, newGames);

        // 原 session 完全不变
        assertEquals(2, messages.listBySession(sid).size());
        assertEquals(1, gameRuns.listBySession(sid).size());

        // 复制后的 messages 顺序与原一致（user 在前）
        List<MessageEntity> copied = messages.listBySession(newSid);
        assertEquals("user", copied.get(0).getRole());
        assertEquals("玩游戏", copied.get(0).getContent());
        assertEquals("assistant", copied.get(1).getRole());

        // 新 session 计数
        SessionEntity newSession = sessions.findById(newSid).orElseThrow();
        assertEquals(2, newSession.getMessageCount());
        assertEquals(0, newSession.getGameCount());

        // clone 不存在的会话 → 404
        ResponseEntity<Map> notFound = http.postForEntity(url("/api/sessions/nonexistent/clone"), null, Map.class);
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // 4. favorite_then_list_favorites
    // -----------------------------------------------------------------------
    @Test
    void favorite_then_list_favorites() {
        String sid = sessions.insert(newSession("fav", System.currentTimeMillis()));
        String mid = messages.insert(makeMsg(sid, "assistant", "ok"));
        String gid = gameRuns.insert(makeGameRun(sid, mid, "可收藏游戏", 88, false));
        String otherGid = gameRuns.insert(makeGameRun(sid, mid, "不收藏", 90, false));

        // 标记收藏
        ResponseEntity<Map> favResp = http.postForEntity(
                url("/api/sessions/games/" + gid + "/favorite"), null, Map.class);
        assertEquals(HttpStatus.OK, favResp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> favData = (Map<String, Object>) favResp.getBody().get("data");
        assertEquals(gid, favData.get("id"));
        assertEquals(Boolean.TRUE, favData.get("favorited"));

        // DB 真值
        Integer dbFav = jdbc.queryForObject(
                "SELECT favorited FROM game_runs WHERE id = ?", Integer.class, gid);
        assertEquals(1, dbFav);

        // favorites 列表只返回 gid
        ResponseEntity<Map> listResp = http.getForEntity(
                url("/api/sessions/games/favorites?limit=50"), Map.class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> favs = (List<Map<String, Object>>) listResp.getBody().get("data");
        assertEquals(1, favs.size());
        assertEquals(gid, favs.get(0).get("id"));
        assertEquals(Boolean.TRUE, favs.get(0).get("favorited"));
        assertNull(favs.get(0).get("html"));

        // 取消收藏
        ResponseEntity<Map> unfavResp = http.postForEntity(
                url("/api/sessions/games/" + gid + "/unfavorite"), null, Map.class);
        assertEquals(HttpStatus.OK, unfavResp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> unfavData = (Map<String, Object>) unfavResp.getBody().get("data");
        assertEquals(Boolean.FALSE, unfavData.get("favorited"));

        Integer afterUnfav = jdbc.queryForObject(
                "SELECT favorited FROM game_runs WHERE id = ?", Integer.class, gid);
        assertEquals(0, afterUnfav);

        // 收藏不存在的游戏 → 404
        ResponseEntity<Map> nf = http.postForEntity(
                url("/api/sessions/games/nonexistent/favorite"), null, Map.class);
        assertEquals(HttpStatus.NOT_FOUND, nf.getStatusCode());

        // 防止未使用警告
        assertNotEquals(otherGid, gid);
    }

    // -----------------------------------------------------------------------
    // 5. list_games_response_shape
    // -----------------------------------------------------------------------
    @Test
    void list_games_response_shape() {
        String sid = sessions.insert(newSession("shape", System.currentTimeMillis()));
        String mid = messages.insert(makeMsg(sid, "assistant", "ok"));
        gameRuns.insert(makeGameRun(sid, mid, "shape-game", 85, true));

        ResponseEntity<Map> resp = http.getForEntity(url("/api/sessions/" + sid + "/games"), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        Map<String, Object> body = resp.getBody();
        assertEquals(Boolean.TRUE, body.get("success"));
        assertEquals(1, body.get("count"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        Map<String, Object> game = data.get(0);

        // 字段名：id, sessionId, messageId, title, evalScore, iterations, favorited, createdAt
        assertNotNull(game.get("id"));
        assertEquals(sid, game.get("sessionId"));
        assertEquals(mid, game.get("messageId"));
        assertEquals("shape-game", game.get("title"));
        assertEquals(85, game.get("evalScore"));
        assertEquals(Boolean.TRUE, game.get("favorited"));
        assertTrue(game.get("createdAt") instanceof Number);

        // 列表里 html 字段不应存在或为 null
        assertNull(game.get("html"));
    }

    // -----------------------------------------------------------------------
    // 6. get_html_returns_full_content
    // -----------------------------------------------------------------------
    @Test
    void get_html_returns_full_content() {
        String sid = sessions.insert(newSession("html", System.currentTimeMillis()));
        String mid = messages.insert(makeMsg(sid, "assistant", "ok"));
        String gid = gameRuns.insert(makeGameRun(sid, mid, "html-test", 90, false));

        ResponseEntity<Map> resp = http.getForEntity(
                url("/api/sessions/games/" + gid + "/html"), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        Map<String, Object> body = resp.getBody();
        assertEquals(Boolean.TRUE, body.get("success"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertEquals(gid, data.get("id"));
        String html = (String) data.get("html");
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"), "html 应含 DOCTYPE");

        // 不存在 → 404
        ResponseEntity<Map> nf = http.getForEntity(
                url("/api/sessions/games/nonexistent/html"), Map.class);
        assertEquals(HttpStatus.NOT_FOUND, nf.getStatusCode());
        assertEquals(Boolean.FALSE, nf.getBody().get("success"));
    }

    // -----------------------------------------------------------------------
    // 7. delete_session_cascades
    // -----------------------------------------------------------------------
    @Test
    void delete_session_cascades() {
        String sid = sessions.insert(newSession("delete-me", System.currentTimeMillis()));
        String mid = messages.insert(makeMsg(sid, "user", "x"));
        String gid = gameRuns.insert(makeGameRun(sid, mid, "g", 80, false));

        // 验证初始
        assertEquals(1, messages.listBySession(sid).size());
        assertEquals(1, gameRuns.listBySession(sid).size());

        // DELETE
        ResponseEntity<Map> resp = http.exchange(
                url("/api/sessions/" + sid),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Map.class
        );
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.TRUE, resp.getBody().get("success"));

        // DB 真值——messages / game_runs 全没了（FK CASCADE）
        assertTrue(sessions.findById(sid).isEmpty());
        Long ghostMsgs = jdbc.queryForObject(
                "SELECT count(*) FROM messages WHERE session_id = ?", Long.class, sid);
        Long ghostGames = jdbc.queryForObject(
                "SELECT count(*) FROM game_runs WHERE session_id = ?", Long.class, sid);
        assertEquals(0L, ghostMsgs);
        assertEquals(0L, ghostGames);

        // 删不存在的 → 404
        ResponseEntity<Map> nf = http.exchange(
                url("/api/sessions/" + sid),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Map.class
        );
        assertEquals(HttpStatus.NOT_FOUND, nf.getStatusCode());

        assertNotNull(gid); // 防止未使用警告
    }

    // -----------------------------------------------------------------------
    // 8. negative cases—missing session 不应 500
    // -----------------------------------------------------------------------
    @Test
    void negative_cases_no_500() {
        // /messages 对不存在 session → 200 空数组
        ResponseEntity<Map> msgs = http.getForEntity(
                url("/api/sessions/nonexistent/messages"), Map.class);
        assertEquals(HttpStatus.OK, msgs.getStatusCode());
        assertEquals(Boolean.TRUE, msgs.getBody().get("success"));
        assertEquals(0, msgs.getBody().get("count"));

        // /games 对不存在 session → 200 空数组
        ResponseEntity<Map> games = http.getForEntity(
                url("/api/sessions/nonexistent/games"), Map.class);
        assertEquals(HttpStatus.OK, games.getStatusCode());
        assertEquals(0, games.getBody().get("count"));

        // /api/sessions/{id} 对不存在 → 404
        ResponseEntity<Map> sessionGet = http.getForEntity(
                url("/api/sessions/nonexistent"), Map.class);
        assertEquals(HttpStatus.NOT_FOUND, sessionGet.getStatusCode());
        assertEquals(Boolean.FALSE, sessionGet.getBody().get("success"));
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static SessionEntity newSession(String title, long ts) {
        SessionEntity e = new SessionEntity();
        e.setTitle(title);
        e.setModelKey("dashscope");
        e.setCreatedAt(Instant.ofEpochMilli(ts));
        e.setUpdatedAt(Instant.ofEpochMilli(ts));
        e.setMessageCount(0);
        e.setGameCount(0);
        return e;
    }

    private static MessageEntity makeMsg(String sid, String role, String content) {
        MessageEntity m = new MessageEntity();
        m.setSessionId(sid);
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    private static GameRunEntity makeGameRun(String sid, String mid, String title, int score, boolean fav) {
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

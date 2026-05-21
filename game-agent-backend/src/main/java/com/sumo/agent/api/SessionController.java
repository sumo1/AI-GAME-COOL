package com.sumo.agent.api;

import com.sumo.agent.infra.db.GameRunEntity;
import com.sumo.agent.infra.db.GameRunRepository;
import com.sumo.agent.infra.db.MessageEntity;
import com.sumo.agent.infra.db.MessageRepository;
import com.sumo.agent.infra.db.SessionEntity;
import com.sumo.agent.infra.db.SessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会话与游戏产出物 API（任务 260521-game-storage-db Step 4a）。
 *
 * 端点设计原则：
 * - 列表接口不返回 html（避免大字段污染）；详情接口走 /games/{id}/html
 * - 时间字段统一毫秒 epoch（Long），前端 new Date(ms) 处理
 * - 错误响应统一 {success:false, error:"<message>"}
 * - 不引入分页，直接 limit；默认 20，上限 100
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "*")
public class SessionController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int FAVORITES_DEFAULT_LIMIT = 50;

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final GameRunRepository gameRunRepository;

    public SessionController(SessionRepository sessionRepository,
                             MessageRepository messageRepository,
                             GameRunRepository gameRunRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.gameRunRepository = gameRunRepository;
    }

    // ========================================================================
    // Sessions
    // ========================================================================

    @GetMapping
    public ResponseEntity<?> listSessions(@RequestParam(required = false) Integer limit) {
        int effective = clampLimit(limit, DEFAULT_LIMIT);
        try {
            List<SessionEntity> list = sessionRepository.listRecent(effective);
            List<Map<String, Object>> data = list.stream().map(SessionController::toSessionSummary).toList();
            return ResponseEntity.ok(success(data));
        } catch (Exception e) {
            log.error("列出会话失败", e);
            return internalError("请稍后重试");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSession(@PathVariable String id) {
        try {
            Optional<SessionEntity> got = sessionRepository.findById(id);
            if (got.isEmpty()) {
                return notFound("会话不存在");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", toSessionSummary(got.get()));
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("获取会话失败 id={}", id, e);
            return internalError("请稍后重试");
        }
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<?> listMessages(@PathVariable String id) {
        try {
            List<MessageEntity> list = messageRepository.listBySession(id);
            List<Map<String, Object>> data = list.stream().map(SessionController::toMessageDto).toList();
            return ResponseEntity.ok(success(data));
        } catch (Exception e) {
            log.error("列出消息失败 sessionId={}", id, e);
            return internalError("请稍后重试");
        }
    }

    @GetMapping("/{id}/games")
    public ResponseEntity<?> listSessionGames(@PathVariable String id) {
        try {
            List<GameRunEntity> list = gameRunRepository.listBySession(id);
            List<Map<String, Object>> data = list.stream().map(SessionController::toGameSummary).toList();
            return ResponseEntity.ok(success(data));
        } catch (Exception e) {
            log.error("列出会话游戏失败 sessionId={}", id, e);
            return internalError("请稍后重试");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSession(@PathVariable String id) {
        try {
            int affected = sessionRepository.deleteById(id);
            if (affected == 0) {
                return notFound("会话不存在");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("删除会话失败 id={}", id, e);
            return internalError("请稍后重试");
        }
    }

    /**
     * 复制一个历史会话：把 messages 整个搬到新 session 下；game_runs 不复制（避免大字段重复）。
     */
    @PostMapping("/{id}/clone")
    public ResponseEntity<?> cloneSession(@PathVariable String id) {
        try {
            Optional<SessionEntity> source = sessionRepository.findById(id);
            if (source.isEmpty()) {
                return notFound("会话不存在");
            }
            SessionEntity src = source.get();

            SessionEntity copy = new SessionEntity();
            copy.setTitle(src.getTitle());
            copy.setModelKey(src.getModelKey());
            copy.setMessageCount(0);
            copy.setGameCount(0);
            String newSessionId = sessionRepository.insert(copy);

            List<MessageEntity> srcMessages = messageRepository.listBySession(id);
            int copied = 0;
            long base = Instant.now().toEpochMilli();
            for (int i = 0; i < srcMessages.size(); i++) {
                MessageEntity src1 = srcMessages.get(i);
                MessageEntity dst = new MessageEntity();
                dst.setSessionId(newSessionId);
                dst.setRole(src1.getRole());
                dst.setContent(src1.getContent());
                dst.setIterations(src1.getIterations());
                dst.setEvalScore(src1.getEvalScore());
                // 保留时间顺序：用 base + i 毫秒作为 created_at，确保按 ASC 顺序与原一致
                dst.setCreatedAt(Instant.ofEpochMilli(base + i));
                messageRepository.insert(dst);
                copied++;
            }
            // 同步 message_count
            if (copied > 0) {
                sessionRepository.incrementCounters(newSessionId, copied, 0);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("newSessionId", newSessionId);
            data.put("sourceSessionId", id);
            data.put("copiedMessages", copied);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("复制会话失败 id={}", id, e);
            return internalError("请稍后重试");
        }
    }

    // ========================================================================
    // Game artifacts (sub-resource of sessions)
    // ========================================================================

    @GetMapping("/games/{id}/html")
    public ResponseEntity<?> getGameHtml(@PathVariable String id) {
        try {
            Optional<GameRunEntity> got = gameRunRepository.findHtmlById(id);
            if (got.isEmpty()) {
                return notFound("游戏不存在");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", got.get().getId());
            data.put("html", got.get().getHtml());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("获取游戏 HTML 失败 id={}", id, e);
            return internalError("请稍后重试");
        }
    }

    @PostMapping("/games/{id}/favorite")
    public ResponseEntity<?> favoriteGame(@PathVariable String id) {
        return setFavorited(id, true);
    }

    @PostMapping("/games/{id}/unfavorite")
    public ResponseEntity<?> unfavoriteGame(@PathVariable String id) {
        return setFavorited(id, false);
    }

    @GetMapping("/games/favorites")
    public ResponseEntity<?> listFavorites(@RequestParam(required = false) Integer limit) {
        int effective = clampLimit(limit, FAVORITES_DEFAULT_LIMIT);
        try {
            List<GameRunEntity> list = gameRunRepository.listFavorites(effective);
            List<Map<String, Object>> data = list.stream().map(SessionController::toGameSummary).toList();
            return ResponseEntity.ok(success(data));
        } catch (Exception e) {
            log.error("列出收藏失败", e);
            return internalError("请稍后重试");
        }
    }

    private ResponseEntity<?> setFavorited(String id, boolean favorited) {
        try {
            int affected = gameRunRepository.setFavorited(id, favorited);
            if (affected == 0) {
                return notFound("游戏不存在");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            data.put("favorited", favorited);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("更新收藏状态失败 id={} favorited={}", id, favorited, e);
            return internalError("请稍后重试");
        }
    }

    // ========================================================================
    // helpers
    // ========================================================================

    private static int clampLimit(Integer raw, int defaultValue) {
        if (raw == null || raw <= 0) return defaultValue;
        return Math.min(raw, MAX_LIMIT);
    }

    private static Map<String, Object> success(List<Map<String, Object>> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        body.put("count", data.size());
        return body;
    }

    private static ResponseEntity<Map<String, Object>> notFound(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", message);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    private static ResponseEntity<Map<String, Object>> internalError(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", message);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static Map<String, Object> toSessionSummary(SessionEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("title", e.getTitle());
        m.put("modelKey", e.getModelKey());
        m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toEpochMilli() : null);
        m.put("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toEpochMilli() : null);
        m.put("messageCount", e.getMessageCount());
        m.put("gameCount", e.getGameCount());
        return m;
    }

    private static Map<String, Object> toMessageDto(MessageEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("sessionId", e.getSessionId());
        m.put("role", e.getRole());
        m.put("content", e.getContent());
        m.put("iterations", e.getIterations());
        m.put("evalScore", e.getEvalScore());
        m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toEpochMilli() : null);
        return m;
    }

    private static Map<String, Object> toGameSummary(GameRunEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("sessionId", e.getSessionId());
        m.put("messageId", e.getMessageId());
        m.put("title", e.getTitle());
        m.put("evalScore", e.getEvalScore());
        m.put("iterations", e.getIterations());
        m.put("favorited", e.isFavorited());
        m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toEpochMilli() : null);
        return m;
    }
}

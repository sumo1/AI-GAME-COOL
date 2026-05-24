package com.sumo.agent.api;

import com.sumo.agent.infra.db.GameRunEntity;
import com.sumo.agent.infra.db.GameRunRepository;
import com.sumo.agent.infra.storage.SavedGame;
import com.sumo.agent.infra.storage.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 游戏存储控制器（DB 化后）
 *
 * 所有读写都走 SQLite 的 game_runs 表（通过 {@link GameRunRepository}）。
 * 老的 saved-games/ 文件目录路径已弃用，不再有任何端点写入它。
 */
@RestController
@RequestMapping("/api/game/storage")
@CrossOrigin(origins = "*")
public class GameStorageController {

    private static final Logger logger = LoggerFactory.getLogger(GameStorageController.class);

    @Autowired
    private GameRunRepository gameRunRepository;

    @Autowired
    private SessionService sessionService;

    /**
     * 保存游戏到服务器（写入 game_runs）。
     *
     * 通过此入口的保存是「用户主动保存」语义；底层走 SessionService.saveManualGame，
     * 复用占位 session 满足 schema 的 NOT NULL FK 约束。
     */
    @PostMapping("/save")
    public ResponseEntity<?> saveGame(@RequestBody SavedGame game) {
        try {
            if (game.getHtml() == null || game.getHtml().isBlank()) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("error", "html 字段不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
            }

            String title = game.getTitle() != null ? game.getTitle() : "未命名游戏";
            String id = sessionService.saveManualGame(title, game.getHtml());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("gameId", id);
            response.put("message", "游戏已保存到服务器");

            Map<String, Object> gameInfo = new HashMap<>();
            gameInfo.put("id", id);
            gameInfo.put("title", title);
            response.put("data", gameInfo);

            logger.info("保存游戏成功: {} (id={})", title, id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("保存游戏失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 获取所有保存的游戏列表（读 game_runs）。
     *
     * 响应 schema 与改造前对齐——前端 ServerGameHistory.tsx 不调整字段名也能渲染。
     * 旧字段（type/ageGroup/difficulty/theme/fileName/fileSize）填 null/0；
     * 时间戳保持 ISO 字符串格式（与老接口一致）；
     * 新增 evalScore / favorited 字段。
     */
    @GetMapping("/list")
    public ResponseEntity<?> listGames() {
        try {
            List<GameRunEntity> games = gameRunRepository.listRecent(100);

            List<Map<String, Object>> gamesList = new ArrayList<>();
            for (GameRunEntity game : games) {
                Map<String, Object> gameMap = new HashMap<>();
                gameMap.put("id", game.getId());
                gameMap.put("title", game.getTitle());
                gameMap.put("type", null);
                gameMap.put("ageGroup", null);
                gameMap.put("difficulty", null);
                gameMap.put("theme", null);
                gameMap.put("fileName", null);
                gameMap.put("fileSize", 0);
                if (game.getCreatedAt() != null) {
                    String iso = game.getCreatedAt().toString();
                    gameMap.put("createdAt", iso);
                    gameMap.put("updatedAt", iso);
                }
                gameMap.put("evalScore", game.getEvalScore());
                gameMap.put("favorited", game.isFavorited());
                gameMap.put("sessionId", game.getSessionId());
                gamesList.add(gameMap);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", gamesList);
            response.put("count", games.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取游戏列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "请稍后重试");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 根据 id 获取游戏详情（含 html）。读 game_runs。
     */
    @GetMapping("/{gameId}")
    public ResponseEntity<?> getGame(@PathVariable String gameId) {
        try {
            Optional<GameRunEntity> opt = gameRunRepository.findById(gameId);
            if (opt.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "游戏不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            GameRunEntity entity = opt.get();

            Map<String, Object> data = new HashMap<>();
            data.put("id", entity.getId());
            data.put("title", entity.getTitle());
            data.put("html", entity.getHtml());
            data.put("type", null);
            data.put("ageGroup", null);
            data.put("difficulty", null);
            data.put("theme", null);
            data.put("config", null);
            data.put("evalScore", entity.getEvalScore());
            data.put("favorited", entity.isFavorited());
            if (entity.getCreatedAt() != null) {
                String iso = entity.getCreatedAt().toString();
                data.put("createdAt", iso);
                data.put("updatedAt", iso);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取游戏详情失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 删除游戏（按 id 从 game_runs 中删）
     */
    @DeleteMapping("/{gameId}")
    public ResponseEntity<?> deleteGame(@PathVariable String gameId) {
        try {
            int affected = gameRunRepository.deleteById(gameId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", affected > 0);
            response.put("message", affected > 0 ? "游戏已删除" : "游戏不存在");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除游戏失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 获取存储统计信息（读 game_runs）
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStorageStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalGames", gameRunRepository.count());
            stats.put("totalSize", gameRunRepository.totalHtmlSize());
            stats.put("storagePath", "sqlite:game_runs");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", stats);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取统计信息失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 批量删除游戏
     */
    @DeleteMapping("/batch")
    public ResponseEntity<?> deleteGames(@RequestBody List<String> gameIds) {
        Map<String, Object> response = new HashMap<>();
        int successCount = 0;
        int failCount = 0;

        for (String gameId : gameIds) {
            try {
                int affected = gameRunRepository.deleteById(gameId);
                if (affected > 0) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                logger.error("删除游戏失败: {}", gameId, e);
                failCount++;
            }
        }

        response.put("success", true);
        response.put("successCount", successCount);
        response.put("failCount", failCount);
        response.put("message", String.format("成功删除 %d 个游戏，失败 %d 个", successCount, failCount));

        return ResponseEntity.ok(response);
    }
}

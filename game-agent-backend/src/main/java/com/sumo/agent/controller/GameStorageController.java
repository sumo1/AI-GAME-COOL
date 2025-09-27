package com.sumo.agent.controller;

import com.sumo.agent.model.SavedGame;
import com.sumo.agent.service.GameStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏存储控制器
 * 提供游戏保存、加载、删除等REST API
 */
@RestController
@RequestMapping("/api/game/storage")
@CrossOrigin(origins = "*")
public class GameStorageController {

    private static final Logger logger = LoggerFactory.getLogger(GameStorageController.class);

    @Autowired
    private GameStorageService gameStorageService;

    /**
     * 保存游戏到服务器
     */
    @PostMapping("/save")
    public ResponseEntity<?> saveGame(@RequestBody SavedGame game) {
        try {
            SavedGame savedGame = gameStorageService.saveGame(game);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("gameId", savedGame.getId());
            response.put("message", "游戏已保存到服务器");
            // 不返回完整的savedGame对象，只返回必要的信息
            Map<String, Object> gameInfo = new HashMap<>();
            gameInfo.put("id", savedGame.getId());
            gameInfo.put("title", savedGame.getTitle());
            gameInfo.put("fileName", savedGame.getFileName());
            response.put("data", gameInfo);

            logger.info("保存游戏成功: {} (ID: {})", game.getTitle(), savedGame.getId());

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("保存游戏失败", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 获取所有保存的游戏列表
     */
    @GetMapping("/list")
    public ResponseEntity<?> listGames() {
        try {
            List<SavedGame> games = gameStorageService.listGames();

            // 转换为不包含LocalDateTime的Map列表
            List<Map<String, Object>> gamesList = new ArrayList<>();
            for (SavedGame game : games) {
                Map<String, Object> gameMap = new HashMap<>();
                gameMap.put("id", game.getId());
                gameMap.put("title", game.getTitle());
                gameMap.put("type", game.getType());
                gameMap.put("ageGroup", game.getAgeGroup());
                gameMap.put("difficulty", game.getDifficulty());
                gameMap.put("theme", game.getTheme());
                gameMap.put("fileName", game.getFileName());
                gameMap.put("fileSize", game.getFileSize());
                // 将LocalDateTime转换为字符串
                if (game.getCreatedAt() != null) {
                    gameMap.put("createdAt", game.getCreatedAt().toString());
                }
                if (game.getUpdatedAt() != null) {
                    gameMap.put("updatedAt", game.getUpdatedAt().toString());
                }
                gamesList.add(gameMap);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", gamesList);
            response.put("count", games.size());

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("获取游戏列表失败", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 根据ID获取游戏详情
     */
    @GetMapping("/{gameId}")
    public ResponseEntity<?> getGame(@PathVariable String gameId) {
        try {
            SavedGame game = gameStorageService.getGame(gameId);

            if (game == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "游戏不存在");

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", game);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("获取游戏详情失败", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 删除游戏
     */
    @DeleteMapping("/{gameId}")
    public ResponseEntity<?> deleteGame(@PathVariable String gameId) {
        try {
            boolean deleted = gameStorageService.deleteGame(gameId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", deleted);
            response.put("message", deleted ? "游戏已删除" : "游戏不存在");

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("删除游戏失败", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 获取存储统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStorageStats() {
        try {
            Map<String, Object> stats = gameStorageService.getStorageStats();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", stats);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
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
                boolean deleted = gameStorageService.deleteGame(gameId);
                if (deleted) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (IOException e) {
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
package com.sumo.agent.service;

import com.sumo.agent.model.SavedGame;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏存储服务
 * 将游戏保存为文件系统中的HTML和JSON文件
 */
@Service
public class GameStorageService {

    private static final Logger logger = LoggerFactory.getLogger(GameStorageService.class);

    @Value("${game.storage.path:./saved-games}")
    private String storagePath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        // 创建存储目录
        try {
            Path path = Paths.get(storagePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("创建游戏存储目录: {}", storagePath);
            }
        } catch (IOException e) {
            logger.error("创建存储目录失败", e);
        }

        // 配置ObjectMapper以支持LocalDateTime
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 保存游戏
     */
    public SavedGame saveGame(SavedGame game) throws IOException {
        // 生成唯一ID
        if (game.getId() == null || game.getId().isEmpty()) {
            game.setId(generateGameId());
        }

        // 设置文件名
        String safeTitle = game.getTitle().replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "_");
        String htmlFileName = String.format("%s_%s.html", game.getId(), safeTitle);
        String metaFileName = String.format("%s_%s.json", game.getId(), safeTitle);

        // 保存HTML文件
        Path htmlPath = Paths.get(storagePath, htmlFileName);
        Files.write(htmlPath, game.getHtml().getBytes(StandardCharsets.UTF_8));

        // 设置文件信息
        game.setFileName(htmlFileName);
        game.setFileSize(Files.size(htmlPath));
        game.setUpdatedAt(LocalDateTime.now());

        // 保存元数据文件
        Path metaPath = Paths.get(storagePath, metaFileName);
        SavedGame metadata = new SavedGame();
        metadata.setId(game.getId());
        metadata.setTitle(game.getTitle());
        metadata.setType(game.getType());
        metadata.setAgeGroup(game.getAgeGroup());
        metadata.setDifficulty(game.getDifficulty());
        metadata.setTheme(game.getTheme());
        metadata.setConfig(game.getConfig());
        metadata.setFileName(htmlFileName);
        metadata.setFileSize(game.getFileSize());
        metadata.setCreatedAt(game.getCreatedAt());
        metadata.setUpdatedAt(game.getUpdatedAt());

        String jsonContent = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(metadata);
        Files.write(metaPath, jsonContent.getBytes(StandardCharsets.UTF_8));

        logger.info("游戏已保存: {} -> {}", game.getTitle(), htmlFileName);

        return game;
    }

    /**
     * 获取所有保存的游戏列表
     */
    public List<SavedGame> listGames() throws IOException {
        List<SavedGame> games = new ArrayList<>();

        Path dir = Paths.get(storagePath);
        if (!Files.exists(dir)) {
            return games;
        }

        // 读取所有JSON元数据文件
        Files.list(dir)
                .filter(path -> path.toString().endsWith(".json"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path, StandardCharsets.UTF_8);
                        SavedGame game = objectMapper.readValue(content, SavedGame.class);
                        games.add(game);
                    } catch (IOException e) {
                        logger.error("读取游戏元数据失败: {}", path, e);
                    }
                });

        // 按更新时间倒序排序
        games.sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));

        return games;
    }

    /**
     * 根据ID获取游戏
     */
    public SavedGame getGame(String gameId) throws IOException {
        Path dir = Paths.get(storagePath);

        // 查找对应的JSON文件
        Optional<Path> metaPath = Files.list(dir)
                .filter(path -> path.toString().endsWith(".json"))
                .filter(path -> path.getFileName().toString().startsWith(gameId + "_"))
                .findFirst();

        if (metaPath.isEmpty()) {
            return null;
        }

        // 读取元数据
        String metaContent = Files.readString(metaPath.get(), StandardCharsets.UTF_8);
        SavedGame game = objectMapper.readValue(metaContent, SavedGame.class);

        // 读取HTML内容
        Path htmlPath = Paths.get(storagePath, game.getFileName());
        if (Files.exists(htmlPath)) {
            String htmlContent = Files.readString(htmlPath, StandardCharsets.UTF_8);
            game.setHtml(htmlContent);
        }

        return game;
    }

    /**
     * 删除游戏
     */
    public boolean deleteGame(String gameId) throws IOException {
        Path dir = Paths.get(storagePath);

        // 查找并删除相关文件
        List<Path> filesToDelete = Files.list(dir)
                .filter(path -> path.getFileName().toString().startsWith(gameId + "_"))
                .collect(Collectors.toList());

        for (Path path : filesToDelete) {
            Files.deleteIfExists(path);
            logger.info("删除游戏文件: {}", path);
        }

        return !filesToDelete.isEmpty();
    }

    /**
     * 获取存储统计信息
     */
    public Map<String, Object> getStorageStats() throws IOException {
        Map<String, Object> stats = new HashMap<>();

        Path dir = Paths.get(storagePath);
        if (!Files.exists(dir)) {
            stats.put("totalGames", 0);
            stats.put("totalSize", 0);
            return stats;
        }

        List<Path> htmlFiles = Files.list(dir)
                .filter(path -> path.toString().endsWith(".html"))
                .collect(Collectors.toList());

        long totalSize = 0;
        for (Path path : htmlFiles) {
            totalSize += Files.size(path);
        }

        stats.put("totalGames", htmlFiles.size());
        stats.put("totalSize", totalSize);
        stats.put("storagePath", storagePath);

        return stats;
    }

    /**
     * 生成唯一的游戏ID
     */
    private String generateGameId() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = UUID.randomUUID().toString().substring(0, 8);
        return "game_" + timestamp + "_" + random;
    }
}
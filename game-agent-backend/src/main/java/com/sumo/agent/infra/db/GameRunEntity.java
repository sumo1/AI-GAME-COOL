package com.sumo.agent.infra.db;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

/**
 * 游戏运行记录——对应 game_runs 表。
 *
 * favorited 用 boolean（Repository 内部转 0/1 INTEGER）。
 * html 字段在 listRecent / listFavorites 等列表查询中保持 null（避免大字段污染）；
 * 详情请用 GameRunRepository.findHtmlById。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRunEntity {
    private String id;
    private String sessionId;
    private String messageId;
    private String title;
    private String html;
    private int evalScore;
    private int iterations;
    private boolean favorited;
    private Instant createdAt;
}

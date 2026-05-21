package com.sumo.agent.infra.db;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

/**
 * 会话实体——对应 sessions 表。
 *
 * 字段对齐 schema.sql（任务 260521-game-storage-db）：
 * - 时间戳用 Instant（毫秒精度，Repository 内部转 epochMs INTEGER）
 * - id 是 UUID 字符串
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionEntity {
    private String id;
    private String title;
    private String modelKey;
    private Instant createdAt;
    private Instant updatedAt;
    private int messageCount;
    private int gameCount;
}

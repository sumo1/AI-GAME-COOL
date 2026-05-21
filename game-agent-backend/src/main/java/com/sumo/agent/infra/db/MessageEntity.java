package com.sumo.agent.infra.db;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

/**
 * 消息实体——对应 messages 表。
 * role 用 String（不引入 enum 简化）：取值 user / assistant / system。
 * iterations / evalScore 在 user 消息上为 null；在 assistant 消息上为整数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageEntity {
    private String id;
    private String sessionId;
    private String role;
    private String content;
    private Integer iterations;
    private Integer evalScore;
    private Instant createdAt;
}

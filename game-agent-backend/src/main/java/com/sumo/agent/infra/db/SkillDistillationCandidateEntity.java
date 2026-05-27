package com.sumo.agent.infra.db;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 蒸馏候选——对应 skill_distillation_candidates 表。
 *
 * status 状态机：raw → candidate → accepted / rejected。
 * 默认值由 DDL 兜底（DEFAULT 'raw'）+ Repository.insert 兜底（防 null）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillDistillationCandidateEntity {
    private String id;
    private String evaluationId;
    private String skillName;
    private String status;        // raw / candidate / accepted / rejected
    private String note;
    private Instant createdAt;
    private Instant updatedAt;
}

package com.sumo.agent.infra.db;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 一次 AgentLoop 运行的结构化复盘——对应 game_run_evaluations 表。
 *
 * 无论 AgentLoop success/failure 都写一条；蒸馏候选挑选的源头。
 *
 * success / degraded 在 DB 层用 INTEGER 0/1（沿用 game_runs.favorited 的风格），
 * 在 entity 这里直接用 int 暴露给 Repository RowMapper（避免 boolean ↔ int 来回转）。
 *
 * *_json 字段在列表查询里**不读**——避免大字段污染列表（沿用 GameRunEntity.html 的处理）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRunEvaluationEntity {
    private String id;
    private String sessionId;
    private String gameRunId;
    private String skillName;
    private String modelKey;
    private int success;          // 0/1
    private String errorType;
    private int totalScore;
    private int degraded;         // 0/1
    private String degradedReason;
    private int iterationCount;
    private String finalIterationSummary;
    private String scoresJson;
    private String probeSummaryJson;
    private String classifiedIssuesJson;
    private String iterTracesJson;
    private Instant createdAt;
}

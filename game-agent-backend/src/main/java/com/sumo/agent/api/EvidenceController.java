package com.sumo.agent.api;

import com.sumo.agent.infra.storage.EvidenceQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 蒸馏候选只读 / 状态机推进端点。
 *
 * 风格沿用 {@link GameStorageController}：@CrossOrigin(origins = "*") + Map 包装的 ResponseEntity。
 */
@RestController
@RequestMapping("/api/evidence")
@CrossOrigin(origins = "*")
public class EvidenceController {

    private static final Logger logger = LoggerFactory.getLogger(EvidenceController.class);

    @Autowired
    private EvidenceQueryService queryService;

    @GetMapping("/candidates")
    public ResponseEntity<?> candidates(
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Integer maxScore,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Map<String, Object>> data = queryService.findCandidates(skill, minScore, maxScore, limit);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", data,
                    "count", data.size()
            ));
        } catch (Exception e) {
            logger.error("查询蒸馏候选列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", String.valueOf(e.getMessage())));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        try {
            return ResponseEntity.ok(Map.of("success", true, "data", queryService.stats()));
        } catch (Exception e) {
            logger.error("获取蒸馏候选统计失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", String.valueOf(e.getMessage())));
        }
    }

    @GetMapping("/{evaluationId}")
    public ResponseEntity<?> detail(@PathVariable String evaluationId) {
        try {
            return queryService.findDetail(evaluationId)
                    .<ResponseEntity<?>>map(d -> ResponseEntity.ok(Map.of("success", true, "data", d)))
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("success", false, "error", "evaluation not found")));
        } catch (Exception e) {
            logger.error("查询评估详情失败: {}", evaluationId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", String.valueOf(e.getMessage())));
        }
    }

    @PostMapping("/{evaluationId}/promote")
    public ResponseEntity<?> promote(@PathVariable String evaluationId,
                                     @RequestBody(required = false) Map<String, String> body) {
        try {
            String note = body != null ? body.get("note") : null;
            String id = queryService.promoteToCandidate(evaluationId, note);
            return ResponseEntity.ok(Map.of("success", true, "candidateId", id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", String.valueOf(e.getMessage())));
        } catch (Exception e) {
            logger.error("promote 候选失败: {}", evaluationId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", String.valueOf(e.getMessage())));
        }
    }

    @PostMapping("/candidates/{id}/accept")
    public ResponseEntity<?> accept(@PathVariable String id,
                                    @RequestBody(required = false) Map<String, String> body) {
        try {
            String note = body != null ? body.get("note") : null;
            queryService.accept(id, note);
            return ResponseEntity.ok(Map.of("success", true, "id", id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", String.valueOf(e.getMessage())));
        } catch (Exception e) {
            logger.error("accept 候选失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", String.valueOf(e.getMessage())));
        }
    }

    @PostMapping("/candidates/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable String id,
                                    @RequestBody(required = false) Map<String, String> body) {
        try {
            String note = body != null ? body.get("note") : null;
            queryService.reject(id, note);
            return ResponseEntity.ok(Map.of("success", true, "id", id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", String.valueOf(e.getMessage())));
        } catch (Exception e) {
            logger.error("reject 候选失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", String.valueOf(e.getMessage())));
        }
    }
}

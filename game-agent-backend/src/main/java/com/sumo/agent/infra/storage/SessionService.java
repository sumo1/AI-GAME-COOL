package com.sumo.agent.infra.storage;

import com.sumo.agent.agent.evaluation.EvaluationObservation;
import com.sumo.agent.agent.loop.AgentLoopResult;
import com.sumo.agent.infra.db.GameRunEntity;
import com.sumo.agent.infra.db.GameRunEvaluationEntity;
import com.sumo.agent.infra.db.GameRunEvaluationRepository;
import com.sumo.agent.infra.db.GameRunRepository;
import com.sumo.agent.infra.db.MessageEntity;
import com.sumo.agent.infra.db.MessageRepository;
import com.sumo.agent.infra.db.SessionEntity;
import com.sumo.agent.infra.db.SessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 会话写入编排——把"一次生成请求"原子地写入 sessions / messages / game_runs。
 *
 * 不在 AgentLoop 内部插钩；调用方在 controller 层依次调
 * ensureSession → agentLoop.run → recordRun。
 *
 * 任务 260521-game-storage-db Step 3。
 */
@Slf4j
@Service
public class SessionService {

    /** session 标题最长 40 字符；超过加 "..."（共 ≤ 43）。 */
    private static final int TITLE_MAX_LEN = 40;

    private final SessionRepository sessions;
    private final MessageRepository messages;
    private final GameRunRepository gameRuns;
    private final GameRunEvaluationRepository evaluations;

    public SessionService(SessionRepository sessions,
                          MessageRepository messages,
                          GameRunRepository gameRuns,
                          GameRunEvaluationRepository evaluations) {
        this.sessions = sessions;
        this.messages = messages;
        this.gameRuns = gameRuns;
        this.evaluations = evaluations;
    }

    /**
     * 创建或复用 session。
     * - sessionId 非空且在库中存在 → touch updatedAt 后返回原 entity
     * - 否则新建（id = UUID 或调用方传入），title 截断自 userInput
     */
    public synchronized SessionEntity ensureSession(String sessionId, String userInput, String modelKey) {
        if (sessionId != null && !sessionId.isBlank()) {
            Optional<SessionEntity> existing = sessions.findById(sessionId);
            if (existing.isPresent()) {
                long now = Instant.now().toEpochMilli();
                sessions.touch(sessionId, now);
                SessionEntity e = existing.get();
                e.setUpdatedAt(Instant.ofEpochMilli(now));
                return e;
            }
        }

        SessionEntity entity = new SessionEntity();
        // 若调用方传了非空但不存在的 sessionId，仍尊重它（前端可能持有 localStorage 里的旧 id）
        if (sessionId != null && !sessionId.isBlank()) {
            entity.setId(sessionId);
        }
        entity.setTitle(buildTitle(userInput));
        entity.setModelKey(modelKey);
        entity.setMessageCount(0);
        entity.setGameCount(0);
        sessions.insert(entity);
        return entity;
    }

    /**
     * 写入一次生成的 user + assistant 消息，以及（成功时）game_run，并更新 session 计数。
     *
     * 任意一步失败抛 RuntimeException——controller 层负责 catch 并保护用户响应。
     */
    public synchronized RecordResult recordRun(String sessionId,
                                               String userInput,
                                               AgentLoopResult result,
                                               String modelKey) {
        // 写 user 消息
        MessageEntity userMsg = new MessageEntity();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(userInput);
        // iterations / evalScore 保持 null
        String userMsgId = messages.insert(userMsg);

        // 写 assistant 消息
        MessageEntity asstMsg = new MessageEntity();
        asstMsg.setSessionId(sessionId);
        asstMsg.setRole("assistant");
        asstMsg.setContent(result.llmMessage() != null ? result.llmMessage() : safeError(result));
        asstMsg.setIterations(result.iterations());
        asstMsg.setEvalScore(result.evalScore());
        String asstMsgId = messages.insert(asstMsg);

        // 写 game_run（仅成功 + 有 html 才写）
        String gameRunId = null;
        if (result.success() && result.gameHtml() != null && !result.gameHtml().isBlank()) {
            String title = sessions.findById(sessionId).map(SessionEntity::getTitle).orElse(null);
            GameRunEntity gr = new GameRunEntity();
            gr.setSessionId(sessionId);
            gr.setMessageId(asstMsgId);
            gr.setTitle(title);
            gr.setHtml(result.gameHtml());
            gr.setEvalScore(result.evalScore());
            gr.setIterations(result.iterations());
            gr.setFavorited(false);
            gameRunId = gameRuns.insert(gr);
        }

        // 更新 session 计数
        int gameDelta = (gameRunId != null) ? 1 : 0;
        sessions.incrementCounters(sessionId, 2, gameDelta);

        return new RecordResult(userMsgId, asstMsgId, gameRunId);
    }

    /**
     * 写入一次 AgentLoop 运行的结构化复盘（无论成败都写一条）——任务 260524 Step 4。
     * <p>
     * 在 {@link #recordRun} 之后由 controller 调用。失败由 controller 层 catch，
     * 这里只负责把 {@link AgentLoopResult} 投影到 {@link GameRunEvaluationEntity}。
     *
     * @param sessionId  必填——已 ensureSession 拿到的 session id
     * @param gameRunId  可空——recordRun 返回的 gameRunId，失败/无 html 时为 null
     * @param modelKey   本次请求选用的模型 key（可空）
     * @param result     AgentLoop 结果（必须含 evidence 字段，由新工厂 successWithEvidence/failureWithEvidence 构造）
     * @return 新写入的 evaluation id
     */
    public synchronized String recordEvidence(String sessionId,
                                              String gameRunId,
                                              String modelKey,
                                              AgentLoopResult result) {
        GameRunEvaluationEntity e = new GameRunEvaluationEntity();
        e.setSessionId(sessionId);
        e.setGameRunId(gameRunId);
        e.setSkillName(result.activeSkillName());
        e.setModelKey(modelKey);
        e.setSuccess(result.success() ? 1 : 0);
        e.setErrorType(result.errorType());
        e.setTotalScore(result.evalScore());

        EvaluationObservation obs = result.lastEvaluationObservation();
        e.setDegraded(obs != null && obs.isDegraded() ? 1 : 0);
        e.setDegradedReason(obs != null ? obs.getDegradedReason() : null);

        e.setIterationCount(result.iterations());
        if (result.runTrace() != null && result.runTrace().last() != null) {
            e.setFinalIterationSummary(result.runTrace().last().getSummary());
        }

        e.setScoresJson(EvidenceMapper.toScoresJson(obs));
        e.setProbeSummaryJson(EvidenceMapper.toProbeSummaryJson(obs));
        e.setClassifiedIssuesJson(EvidenceMapper.toClassifiedIssuesJson(obs));
        e.setIterTracesJson(EvidenceMapper.toIterTracesJson(result.runTrace()));

        return evaluations.insert(e);
    }

    private static String buildTitle(String userInput) {
        if (userInput == null || userInput.isBlank()) return "未命名会话";
        String trimmed = userInput.strip();
        if (trimmed.length() <= TITLE_MAX_LEN) return trimmed;
        return trimmed.substring(0, TITLE_MAX_LEN) + "...";
    }

    private static String safeError(AgentLoopResult result) {
        return result.error() != null ? "[失败] " + result.error() : "[无 LLM 输出]";
    }

    /**
     * 用户手动「保存到服务器」入口：把一份 (title, html) 直接写进 game_runs。
     *
     * schema 强制 game_runs.session_id / message_id NOT NULL（任务 260521 不动 schema），
     * 所以这里复用固定的 MANUAL_SAVES_SESSION_ID 当占位 session，
     * 每次保存补一条 system 消息满足 message FK，再插 game_run。
     *
     * 与 AgentLoop 自动写入的记录通过 session_id 区分（前者写真实 sessionId，
     * 后者写 MANUAL_SAVES_SESSION_ID）；列表 / 详情接口一视同仁。
     */
    public synchronized String saveManualGame(String title, String html) {
        ensureManualSavesSession();

        MessageEntity placeholder = new MessageEntity();
        placeholder.setSessionId(MANUAL_SAVES_SESSION_ID);
        placeholder.setRole("system");
        placeholder.setContent("[manual-save] " + (title != null ? title : "未命名游戏"));
        String messageId = messages.insert(placeholder);

        GameRunEntity gr = new GameRunEntity();
        gr.setSessionId(MANUAL_SAVES_SESSION_ID);
        gr.setMessageId(messageId);
        gr.setTitle(title != null ? title : "未命名游戏");
        gr.setHtml(html);
        gr.setEvalScore(0);
        gr.setIterations(0);
        gr.setFavorited(false);
        return gameRuns.insert(gr);
        // 不更新 sessions 计数器：占位 session 不在「会话历史」抽屉中显示，计数无意义
    }

    /**
     * 占位 session：所有「手动保存」的 game_run 共享这一行 sessions 记录。
     * 与 {@link com.sumo.agent.infra.db.SessionRepository#MANUAL_SAVES_SESSION_ID} 必须保持一致。
     */
    private static final String MANUAL_SAVES_SESSION_ID =
            com.sumo.agent.infra.db.SessionRepository.MANUAL_SAVES_SESSION_ID;

    private void ensureManualSavesSession() {
        if (sessions.findById(MANUAL_SAVES_SESSION_ID).isPresent()) return;
        SessionEntity holder = new SessionEntity();
        holder.setId(MANUAL_SAVES_SESSION_ID);
        holder.setTitle("手动保存（用户主动保存的游戏）");
        holder.setModelKey(null);
        holder.setMessageCount(0);
        holder.setGameCount(0);
        sessions.insert(holder);
    }

    /** 一次 recordRun 写入的标识符。 */
    public record RecordResult(String userMessageId, String assistantMessageId, String gameRunId) {}
}

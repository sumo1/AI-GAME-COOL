-- AI-GAME 持久化 schema（任务 260521-game-storage-db）
-- 时间戳统一用 INTEGER（毫秒 epoch）；boolean 用 INTEGER 0/1
-- 必须幂等：所有建表/索引语句带 IF NOT EXISTS

-- 会话：用户与系统的一次对话上下文
CREATE TABLE IF NOT EXISTS sessions (
    id              TEXT PRIMARY KEY,
    title           TEXT NOT NULL,
    model_key       TEXT,
    created_at      INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL,
    message_count   INTEGER NOT NULL DEFAULT 0,
    game_count      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sessions_updated_at ON sessions(updated_at DESC);

-- 消息：会话内的一条用户输入或 LLM 响应
CREATE TABLE IF NOT EXISTS messages (
    id              TEXT PRIMARY KEY,
    session_id      TEXT NOT NULL,
    role            TEXT NOT NULL,             -- user | assistant | system
    content         TEXT NOT NULL,
    iterations      INTEGER,
    eval_score      INTEGER,
    created_at      INTEGER NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_messages_session_created ON messages(session_id, created_at);

-- 游戏运行记录：一条 assistant message 对应一次成功生成的游戏 HTML
CREATE TABLE IF NOT EXISTS game_runs (
    id              TEXT PRIMARY KEY,
    session_id      TEXT NOT NULL,
    message_id      TEXT NOT NULL,
    title           TEXT,
    html            TEXT NOT NULL,
    eval_score      INTEGER NOT NULL DEFAULT 0,
    iterations      INTEGER NOT NULL DEFAULT 0,
    favorited       INTEGER NOT NULL DEFAULT 0,    -- 0/1
    created_at      INTEGER NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_game_runs_session ON game_runs(session_id);
CREATE INDEX IF NOT EXISTS idx_game_runs_favorited ON game_runs(favorited, eval_score DESC);

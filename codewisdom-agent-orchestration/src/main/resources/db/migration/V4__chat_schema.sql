-- =====================================================================
-- CodeWisdom AI — 项目 AI 会话（每项目多 session）
-- =====================================================================

CREATE TABLE IF NOT EXISTS t_chat_session
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    project_id BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL DEFAULT 0,
    title      VARCHAR(128) NOT NULL,
    created_at DATETIME(3)  NOT NULL,
    updated_at DATETIME(3)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_chat_session_project ON t_chat_session (project_id);

CREATE TABLE IF NOT EXISTS t_chat_message
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    session_id BIGINT       NOT NULL,
    role       VARCHAR(16)  NOT NULL,
    content    MEDIUMTEXT   NOT NULL,
    created_at DATETIME(3)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_chat_message_session ON t_chat_message (session_id);

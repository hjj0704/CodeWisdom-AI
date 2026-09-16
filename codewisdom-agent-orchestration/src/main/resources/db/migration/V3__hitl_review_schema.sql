-- =====================================================================
-- CodeWisdom AI — 智能体编排服务 · HITL 审核表（T-703 / T-105）
-- =====================================================================

CREATE TABLE IF NOT EXISTS t_hitl_review
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    project_id      BIGINT       NULL,
    session_key     VARCHAR(64)  NOT NULL,
    fix_record_id   BIGINT       NULL,
    round           INT          NOT NULL DEFAULT 0,
    decision_status VARCHAR(32)  NOT NULL,
    approved        TINYINT      NOT NULL DEFAULT 0,
    is_terminated   TINYINT      NOT NULL DEFAULT 0,
    created_at      DATETIME(3)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_hitl_review_project ON t_hitl_review (project_id);
CREATE INDEX idx_hitl_review_session ON t_hitl_review (session_key);
CREATE INDEX idx_hitl_review_session_round ON t_hitl_review (session_key, round);
CREATE INDEX idx_hitl_review_created ON t_hitl_review (created_at);

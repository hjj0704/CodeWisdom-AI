-- =====================================================================
-- CodeWisdom AI — 智能体编排服务 · 修复建议表（T-701~702 / T-105）
-- =====================================================================

CREATE TABLE IF NOT EXISTS t_fix_record
(
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    project_id   BIGINT        NULL,
    issue_key    VARCHAR(256)  NOT NULL,
    rule_id      VARCHAR(64)   NOT NULL,
    file_path    VARCHAR(1024) NOT NULL,
    line_no      INT           NOT NULL DEFAULT 0,
    risk_level   VARCHAR(16)   NOT NULL,
    suggestion   VARCHAR(2048) NOT NULL,
    rationale    VARCHAR(2048) NOT NULL,
    diff_changed TINYINT       NOT NULL DEFAULT 0,
    diff_json    LONGTEXT      NULL,
    created_at   DATETIME(3)   NOT NULL,
    updated_at   DATETIME(3)   NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_fix_record_project ON t_fix_record (project_id);
CREATE INDEX idx_fix_record_issue_key ON t_fix_record (issue_key);
CREATE INDEX idx_fix_record_rule ON t_fix_record (rule_id);
CREATE INDEX idx_fix_record_created ON t_fix_record (created_at);

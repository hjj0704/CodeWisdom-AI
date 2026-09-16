-- =====================================================================
-- CodeWisdom AI — 评测与导出服务 · 评测报告表（T-904 / T-105）
-- 目标库：MySQL 8.0；测试库：H2(MODE=MySQL)
-- Flyway 历史表：flyway_schema_history_evaluation_export
-- =====================================================================

-- ---------------------------------------------------------------------
-- 量化评测报告（指标 JSON + 溯源 JSON + Markdown 正文）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_eval_report
(
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    project_id      BIGINT        NULL,
    dataset_version VARCHAR(32)   NOT NULL,
    sample_id       VARCHAR(64)   NULL,
    status          VARCHAR(32)   NOT NULL,
    metrics_json    LONGTEXT      NOT NULL,
    trace_json      LONGTEXT      NULL,
    report_markdown LONGTEXT      NULL,
    created_at      DATETIME(3)   NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_eval_report_project ON t_eval_report (project_id);
CREATE INDEX idx_eval_report_dataset ON t_eval_report (dataset_version);
CREATE INDEX idx_eval_report_status ON t_eval_report (status);
CREATE INDEX idx_eval_report_created ON t_eval_report (created_at);

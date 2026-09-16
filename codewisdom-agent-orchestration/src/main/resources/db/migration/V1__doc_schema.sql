-- =====================================================================
-- CodeWisdom AI — 智能体编排服务 · 文档产物记录表（T-604 / T-105）
-- 目标库：MySQL 8.0；测试库：H2(MODE=MySQL)
-- Flyway 历史表：flyway_schema_history_agent_orchestration（与 sibling 服务隔离）
-- =====================================================================

CREATE TABLE IF NOT EXISTS t_doc_record
(
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    project_id   BIGINT        NULL,
    doc_type     VARCHAR(32)   NOT NULL,
    bucket       VARCHAR(64)   NOT NULL,
    object_key   VARCHAR(512)  NOT NULL,
    file_name    VARCHAR(256)  NOT NULL,
    content_size BIGINT        NOT NULL,
    created_at   DATETIME(3)   NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_doc_record_project ON t_doc_record (project_id);
CREATE INDEX idx_doc_record_type ON t_doc_record (doc_type);
CREATE INDEX idx_doc_record_created ON t_doc_record (created_at);

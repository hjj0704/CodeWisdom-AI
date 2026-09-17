-- T-305：解析结果入库（按项目 + 文件路径唯一）
CREATE TABLE IF NOT EXISTS t_parse_result
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    project_id     BIGINT        NOT NULL,
    file_path      VARCHAR(1024) NOT NULL,
    checksum       VARCHAR(64)   NOT NULL,
    type_count     INT           NOT NULL DEFAULT 0,
    method_count   INT           NOT NULL DEFAULT 0,
    structure_json LONGTEXT      NOT NULL,
    created_at     DATETIME(3)   NOT NULL,
    updated_at     DATETIME(3)   NOT NULL,
    PRIMARY KEY (id)
);

-- file_path 全列唯一索引在 utf8mb4 下超 InnoDB 3072 字节上限，用前缀索引
CREATE UNIQUE INDEX uk_parse_result_project_file ON t_parse_result (project_id, file_path(255));
CREATE INDEX idx_parse_result_project ON t_parse_result (project_id);
CREATE INDEX idx_parse_result_checksum ON t_parse_result (checksum);

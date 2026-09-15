-- =====================================================================
-- CodeWisdom AI — 项目资源服务初始表结构
--
-- 目标库：MySQL 8.0
-- 测试库：H2 2.3.x（MODE=MySQL）—— 为兼容两者，索引一律用独立
--         CREATE INDEX 语句，不使用 MySQL 特有的内联 KEY 语法。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 项目
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_project
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(128) NOT NULL,
    source_type    VARCHAR(32)  NOT NULL,
    source_url     VARCHAR(512) NULL,
    default_branch VARCHAR(128) NULL,
    status         VARCHAR(32)  NOT NULL,
    storage_bucket VARCHAR(64)  NOT NULL,
    storage_prefix VARCHAR(512) NOT NULL,
    file_count     INT          NOT NULL DEFAULT 0,
    total_size     BIGINT       NOT NULL DEFAULT 0,
    created_at     DATETIME(3)  NOT NULL,
    updated_at     DATETIME(3)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_project_status ON t_project (status);
CREATE INDEX idx_project_created_at ON t_project (created_at);

-- ---------------------------------------------------------------------
-- 导入任务（一个项目可有多条：重试、重新导入）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_import_task
(
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    project_id  BIGINT        NOT NULL,
    status      VARCHAR(32)   NOT NULL,
    progress    INT           NOT NULL DEFAULT 0,
    message     VARCHAR(1024) NULL,
    error_code  VARCHAR(64)   NULL,
    started_at  DATETIME(3)   NULL,
    finished_at DATETIME(3)   NULL,
    created_at  DATETIME(3)   NOT NULL,
    updated_at  DATETIME(3)   NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_import_task_project_id ON t_import_task (project_id);
CREATE INDEX idx_import_task_status ON t_import_task (status);

-- ---------------------------------------------------------------------
-- 文件树节点（自引用树，parent_id 为空表示根）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_file_node
(
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    project_id BIGINT        NOT NULL,
    parent_id  BIGINT        NULL,
    path       VARCHAR(1024) NOT NULL,
    name       VARCHAR(255)  NOT NULL,
    node_type  VARCHAR(16)   NOT NULL,
    language   VARCHAR(32)   NULL,
    category   VARCHAR(32)   NOT NULL,
    size       BIGINT        NOT NULL DEFAULT 0,
    checksum   VARCHAR(64)   NULL,
    created_at DATETIME(3)   NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_file_node_project_id ON t_file_node (project_id);
CREATE INDEX idx_file_node_parent_id ON t_file_node (parent_id);
CREATE INDEX idx_file_node_category ON t_file_node (project_id, category);

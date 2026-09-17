-- 项目归属用户（按账号隔离列表与访问）
ALTER TABLE t_project
    ADD COLUMN owner_user_id BIGINT NULL COMMENT '创建者用户 id，NULL 表示历史数据';

CREATE INDEX idx_project_owner_user ON t_project (owner_user_id, status);

-- =====================================================================
-- CodeWisdom AI — 代码分析服务 · 审计问题表（T-105，接手 T-505 移入的「问题模型入库」）
--
-- 目标库：MySQL 8.0
-- 测试库：H2 2.3.x（MODE=MySQL）
--
-- 两库兼容约定（见 docs/architecture.md §9）：
--   · 索引用独立 CREATE INDEX，禁用内联 KEY / ENGINE= / CHARSET=
--   · 一律 CREATE TABLE IF NOT EXISTS
--
-- ⚠️ **Flyway 历史表按服务隔离**：本服务与 project-resource 共用同一个库
--    `codewisdom`，两者都有 V1__ 开头的脚本。Flyway 的版本号在**单个历史表内**
--    才唯一，共用默认的 `flyway_schema_history` 会直接报「版本重复 / 校验和不匹配」。
--    因此 application-local.yml 里为每个服务指定了独立的 `spring.flyway.table`。
--    新增服务时**必须**照做，否则会把已有服务的迁移记录搞乱。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 审计问题
--
-- 一次审计跑出的每条问题一行。字段与 domain.AuditIssue 一一对应——
-- 模型侧的六个必填字段在这里都有对应列，且都 NOT NULL，
-- 让「问题必须带齐文件、行号、描述、风险说明、触发场景」这条约束在**库层面**也成立。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_audit_issue
(
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    -- 所属项目。审计是按项目跑的，但 MVP 阶段流水线还没接项目上下文，先允许为空。
    project_id       BIGINT        NULL,
    -- 规则 ID，如 CW-NULL-001 / CW-DEP-001 / CW-ARCH-001
    rule_id          VARCHAR(64)   NOT NULL,
    -- 问题分类：NULL_SAFETY / UNCAUGHT_EXCEPTION / HARDCODED / DEAD_CODE / DEPENDENCY / ARCHITECTURE
    category         VARCHAR(32)   NOT NULL,
    -- 风险等级，仅 HIGH / MEDIUM / LOW 三值（模型侧由枚举保证，这里不做 CHECK——
    -- H2 与 MySQL 对 CHECK 的支持范围不一致，加了会破坏「同一份脚本两边都能跑」）
    risk_level       VARCHAR(16)   NOT NULL,
    file_path        VARCHAR(1024) NOT NULL,
    -- 行号，1-based；0 表示「确实没有具体行」（对应 AuditIssue.NO_LINE）
    line_no          INT           NOT NULL DEFAULT 0,
    description      VARCHAR(512)  NOT NULL,
    -- ⚠️ 列名不是 trigger：`TRIGGER` 是 MySQL 保留字，直接用它建表会语法错误
    trigger_snippet  VARCHAR(1024) NOT NULL,
    risk_description VARCHAR(1024) NOT NULL,
    created_at       DATETIME(3)   NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_audit_issue_project ON t_audit_issue (project_id);
CREATE INDEX idx_audit_issue_risk ON t_audit_issue (risk_level);
CREATE INDEX idx_audit_issue_rule ON t_audit_issue (rule_id);
CREATE INDEX idx_audit_issue_created ON t_audit_issue (created_at);

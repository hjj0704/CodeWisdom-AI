-- =====================================================================
-- CodeWisdom AI — 用户账号表（Web 登录 / 注册）
-- =====================================================================

CREATE TABLE IF NOT EXISTS t_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    nickname      VARCHAR(64)  NOT NULL,
    created_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_user_username ON t_user (username);

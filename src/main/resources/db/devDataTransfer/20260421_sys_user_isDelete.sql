-- 1) 新增字段
ALTER TABLE sys_user
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除：0-否 1-是',
    ADD COLUMN deleted_time DATETIME NULL COMMENT '删除时间',
    ADD COLUMN delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '删除占位token，活跃=0，已删=唯一值';

-- 2) 调整唯一约束（允许复用）
ALTER TABLE sys_user DROP INDEX uk_username;
ALTER TABLE sys_user ADD UNIQUE KEY uk_username_token (username, delete_token);

-- 3) 常用查询索引
CREATE INDEX idx_sys_user_deleted_enabled ON sys_user(is_deleted, is_enabled);

-- 2026-04-21
-- 目标：为 project / project_group_info / reviewer_group 增加软删除字段

-- 1) project
ALTER TABLE project
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除：0-否 1-是';

CREATE INDEX idx_project_is_deleted ON project(is_deleted);

-- 2) project_group_info
ALTER TABLE project_group_info
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除：0-否 1-是';

CREATE INDEX idx_group_info_is_deleted ON project_group_info(is_deleted);

-- 3) reviewer_group
ALTER TABLE reviewer_group
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除：0-否 1-是';

CREATE INDEX idx_reviewer_group_is_deleted ON reviewer_group(is_deleted);

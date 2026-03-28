

-------------------------2026/3/12 拆分project_group表到project_group_info表的数据迁移操作------------------------------
-- ==========================================
-- 第一步：备份关键数据（重要！防止数据丢失）
-- ==========================================
-- 备份 scoring_record 表（打分记录）
CREATE TABLE temp_scoring_record_backup AS SELECT * FROM scoring_record;
-- 备份原 project_group 表
CREATE TABLE temp_project_group_backup AS SELECT * FROM project_group;

-- ==========================================
-- 第二步：临时禁用外键检查（允许删除被引用的表）
-- ==========================================
SET FOREIGN_KEY_CHECKS = 0;

-- ==========================================
-- 第三步：创建小组信息主表并迁移数据
-- ==========================================
-- 1. 创建新的小组信息主表（如果已创建可跳过）
CREATE TABLE IF NOT EXISTS `project_group_info` (
                                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '小组ID (主键)',
                                                    `name` varchar(100) NOT NULL COMMENT '小组名称',
    `description` varchar(500) DEFAULT '' COMMENT '小组描述',
    `is_enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用：1-是 0-否',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小组信息主表';

-- 2. 迁移原 project_group 的小组基础信息到新表
INSERT INTO `project_group_info` (`id`, `name`, `create_time`, `update_time`)
SELECT DISTINCT `id`, `name`, `create_time`, `update_time` FROM temp_project_group_backup;

-- ==========================================
-- 第四步：重建 project_group 关联表
-- ==========================================
-- 1. 删除旧的 project_group 表（此时外键检查已禁用）
DROP TABLE IF EXISTS `project_group`;

-- 2. 创建新的 project_group 关联表
CREATE TABLE `project_group` (
                                 `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
                                 `project_id` bigint NOT NULL COMMENT '项目ID',
                                 `group_info_id` bigint NOT NULL COMMENT '小组信息ID',
                                 `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_project_group` (`project_id`, `group_info_id`), -- 防止重复关联
                                 KEY `idx_project_id` (`project_id`),
                                 KEY `idx_group_info_id` (`group_info_id`),
                                 CONSTRAINT `fk_rel_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE,
                                 CONSTRAINT `fk_rel_group_info` FOREIGN KEY (`group_info_id`) REFERENCES `project_group_info` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目-小组关联表';

-- 3. 恢复项目与小组的关联关系
INSERT INTO `project_group` (`project_id`, `group_info_id`)
SELECT `project_id`, `id` FROM temp_project_group_backup;

-- ==========================================
-- 第五步：修复 scoring_record 表的外键关联
-- ==========================================
-- 1. 先删除旧的外键约束
ALTER TABLE `scoring_record` DROP FOREIGN KEY `fk_record_group`;

-- 2. 先按旧关系ID修正 project_id，避免历史脏数据导致复合外键失败
UPDATE scoring_record sr
    JOIN project_group pg ON sr.group_id = pg.id
SET sr.project_id = pg.project_id;

-- 3. 将 scoring_record.group_id 更名为 group_info_id
ALTER TABLE `scoring_record`
    CHANGE COLUMN `group_id` `group_info_id` bigint NOT NULL COMMENT '被打分小组信息ID';

-- 4. 重建唯一索引与普通索引
ALTER TABLE `scoring_record` DROP INDEX `uk_project_group_user`;
ALTER TABLE `scoring_record` DROP INDEX `idx_group_id`;
ALTER TABLE `scoring_record`
    ADD UNIQUE KEY `uk_project_group_user` (`project_id`,`group_info_id`,`user_id`),
    ADD KEY `idx_group_info_id` (`group_info_id`),
    ADD KEY `idx_project_group_info` (`project_id`,`group_info_id`);

-- 5. 约束前校验，必须为0
SELECT COUNT(*) AS orphan_pair_count
FROM scoring_record sr
         LEFT JOIN project_group pg
                   ON sr.project_id = pg.project_id
                       AND sr.group_info_id = pg.group_info_id
WHERE pg.id IS NULL;

-- 6. 新增复合外键，强约束打分记录必须属于 project_group 关系
ALTER TABLE `scoring_record`
    ADD CONSTRAINT `fk_record_project_group_info`
        FOREIGN KEY (`project_id`, `group_info_id`) REFERENCES `project_group` (`project_id`, `group_info_id`) ON DELETE CASCADE;

-- ==========================================
-- 第六步：恢复外键检查并清理临时表
-- ==========================================
-- 启用外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- 删除临时表（确认数据无误后执行）
-- 如需保留备份，可删除以下两行
DROP TABLE IF EXISTS temp_scoring_record_backup;
DROP TABLE IF EXISTS temp_project_group_backup;

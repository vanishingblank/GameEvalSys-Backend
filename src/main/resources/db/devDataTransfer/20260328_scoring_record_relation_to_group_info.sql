-- 2026/03/28
-- 目标：将 scoring_record 从「project_group.id 关系ID」迁移为「project_id + group_info_id」语义
-- 适用场景：当前库中 scoring_record 使用 group_id 并外键到 project_group(id)

-- 0) 备份（建议保留）
CREATE TABLE IF NOT EXISTS backup_scoring_record_20260328 AS
SELECT * FROM scoring_record;

-- 1) 新增过渡列 group_info_id，并根据 project_group 关系回填
--    注意：这里同时把 sr.project_id 对齐为 pg.project_id，避免历史脏数据导致后续复合外键失败
ALTER TABLE `scoring_record`
    ADD COLUMN `group_info_id` bigint NULL COMMENT '被打分小组信息ID';

UPDATE `scoring_record` sr
    JOIN `project_group` pg ON sr.group_id = pg.id
SET sr.group_info_id = pg.group_info_id,
    sr.project_id = pg.project_id;

-- 2) 数据校验：若 unmapped_count > 0，请先处理异常数据后再继续
SELECT COUNT(*) AS unmapped_count
FROM scoring_record
WHERE group_info_id IS NULL;

-- 2.1) 可选：查看无法映射的异常记录（通常是历史禁用外键后产生的数据）
SELECT sr.*
FROM scoring_record sr
WHERE sr.group_info_id IS NULL;

-- 3) 移除旧约束/索引
ALTER TABLE `scoring_record` DROP FOREIGN KEY `fk_record_group`;
ALTER TABLE `scoring_record` DROP INDEX `uk_project_group_user`;
ALTER TABLE `scoring_record` DROP INDEX `idx_group_id`;

-- 4) 删除旧列并切换到新列
ALTER TABLE `scoring_record`
    DROP COLUMN `group_id`;

ALTER TABLE `scoring_record`
    MODIFY COLUMN `group_info_id` bigint NOT NULL COMMENT '被打分小组信息ID';

-- 5) 重建索引与新约束
ALTER TABLE `scoring_record`
    ADD UNIQUE KEY `uk_project_group_user` (`project_id`,`group_info_id`,`user_id`),
    ADD KEY `idx_group_info_id` (`group_info_id`),
    ADD KEY `idx_project_group_info` (`project_id`,`group_info_id`);

-- 5.1) 约束前校验：必须为 0，否则会在下一步 ADD CONSTRAINT 报 1452
SELECT COUNT(*) AS orphan_pair_count
FROM scoring_record sr
         LEFT JOIN project_group pg
                   ON sr.project_id = pg.project_id
                       AND sr.group_info_id = pg.group_info_id
WHERE pg.id IS NULL;

ALTER TABLE `scoring_record`
    ADD CONSTRAINT `fk_record_project_group_info`
        FOREIGN KEY (`project_id`, `group_info_id`) REFERENCES `project_group` (`project_id`, `group_info_id`) ON DELETE CASCADE;

-- 6) 迁移后抽检
SELECT id, project_id, group_info_id, user_id, total_score
FROM scoring_record
ORDER BY id DESC
LIMIT 20;

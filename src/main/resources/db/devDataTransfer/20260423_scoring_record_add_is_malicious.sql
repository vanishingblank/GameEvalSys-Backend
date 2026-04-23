-- 为 scoring_record 增加恶意打分标记字段
ALTER TABLE `scoring_record`
    ADD COLUMN IF NOT EXISTS `is_malicious` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否恶意打分：0-否 1-是' AFTER `total_score`;

-- 为用户分页查询 + 恶意筛选补充索引
ALTER TABLE `scoring_record`
    ADD INDEX IF NOT EXISTS `idx_project_user_malicious` (`project_id`, `user_id`, `is_malicious`);

-- 打分标准分类化改造迁移脚本
-- 执行前请先备份数据库

-- 1) 新增指标分类表
CREATE TABLE `scoring_indicator_category` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `standard_id` bigint NOT NULL COMMENT '所属打分标准ID',
    `name` varchar(100) NOT NULL COMMENT '分类名称',
    `description` varchar(500) DEFAULT '' COMMENT '分类说明',
    `sort` int DEFAULT 0 COMMENT '分类排序号',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_standard_id` (`standard_id`),
    CONSTRAINT `fk_indicator_category_standard` FOREIGN KEY (`standard_id`) REFERENCES `scoring_standard` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打分指标分类表';

-- 2) 为指标表增加分类字段和外键
ALTER TABLE `scoring_indicator`
    ADD COLUMN `category_id` bigint DEFAULT NULL COMMENT '分类ID' AFTER `standard_id`,
    ADD KEY `idx_category_id` (`category_id`),
    ADD CONSTRAINT `fk_indicator_category` FOREIGN KEY (`category_id`) REFERENCES `scoring_indicator_category` (`id`) ON DELETE SET NULL;

-- 3) 可选：为历史数据创建默认分类并回填
INSERT INTO `scoring_indicator_category` (`standard_id`, `name`, `description`, `sort`, `create_time`, `update_time`)
SELECT s.`id`, '默认分类', '历史数据迁移默认分类', 0, NOW(), NOW()
FROM `scoring_standard` s;

UPDATE `scoring_indicator` i
JOIN `scoring_indicator_category` c
    ON c.`standard_id` = i.`standard_id`
   AND c.`name` = '默认分类'
SET i.`category_id` = c.`id`
WHERE i.`category_id` IS NULL;

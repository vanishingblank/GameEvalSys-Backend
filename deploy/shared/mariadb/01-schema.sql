CREATE DATABASE IF NOT EXISTS gameeval
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE gameeval;
-- 系统用户表
CREATE TABLE `sys_user` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                            `username` varchar(50) NOT NULL COMMENT '用户名（唯一）',
                            `password` varchar(100) NOT NULL COMMENT '密码（加密存储）',
                            `name` varchar(50) NOT NULL COMMENT '真实姓名',
                            `role` varchar(20) NOT NULL COMMENT '角色：super_admin/管理员 admin/打分用户 scorer/普通用户 normal',
                            `is_enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用：1-是 0-否',
                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_username` (`username`),
                            KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 打分标准主表
CREATE TABLE `scoring_standard` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标准ID',
                                    `creator_id` bigint NOT NULL COMMENT '创建人ID',
                                    `name` varchar(100) NOT NULL COMMENT '打分标准名称',
                                    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    PRIMARY KEY (`id`),
                                    KEY `idx_creator_id` (`creator_id`),
                                    CONSTRAINT `fk_standard_creator` FOREIGN KEY (`creator_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打分标准主表';

-- 打分指标表
CREATE TABLE `scoring_indicator` (
                                     `id` bigint NOT NULL AUTO_INCREMENT COMMENT '指标ID',
                                     `standard_id` bigint NOT NULL COMMENT '关联标准ID',
                                     `name` varchar(100) NOT NULL COMMENT '指标名称',
                                     `description` varchar(500) DEFAULT '' COMMENT '指标说明',
                                     `min_score` int NOT NULL COMMENT '分值最小值',
                                     `max_score` int NOT NULL COMMENT '分值最大值',
                                     `sort` int DEFAULT 0 COMMENT '排序号',
                                     `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     PRIMARY KEY (`id`),
                                     KEY `idx_standard_id` (`standard_id`),
                                     CONSTRAINT `fk_indicator_standard` FOREIGN KEY (`standard_id`) REFERENCES `scoring_standard` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打分指标表';

-- 课题项目表
CREATE TABLE `project` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目ID',
                           `name` varchar(200) NOT NULL COMMENT '项目名称',
                           `description` varchar(1000) DEFAULT '' COMMENT '项目描述',
                           `start_date` date NOT NULL COMMENT '起始日期',
                           `end_date` date NOT NULL COMMENT '结束日期',
                           `status` varchar(20) DEFAULT 'not_started' COMMENT '项目状态：not_started-未开始/ongoing-进行中/ended-已结束',
                           `is_enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用：1-是 0-否',
                           `standard_id` bigint NOT NULL COMMENT '关联打分标准ID',
                           `creator_id` bigint NOT NULL COMMENT '创建人ID',
                           `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           KEY `idx_status` (`status`),
                           KEY `idx_standard_id` (`standard_id`),
                           KEY `idx_creator_id` (`creator_id`),
                           CONSTRAINT `fk_project_standard` FOREIGN KEY (`standard_id`) REFERENCES `scoring_standard` (`id`) ON DELETE RESTRICT,
                           CONSTRAINT `fk_project_creator` FOREIGN KEY (`creator_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课题项目表';

-- ==========================================
-- 【调整 1】小组信息主表 (独立存在的小组库)
-- ==========================================
CREATE TABLE `project_group_info` (
                                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '小组ID (主键)',
                                      `name` varchar(100) NOT NULL COMMENT '小组名称',
                                      `description` varchar(500) DEFAULT '' COMMENT '小组描述',
                                      `is_enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用：1-是 0-否',
                                      `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小组信息主表';

-- ==========================================
-- 【调整 2】项目-小组关联表 (关系表)
-- ==========================================
CREATE TABLE `project_group` (
                                 `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
                                 `project_id` bigint NOT NULL COMMENT '项目ID',
                                 `group_info_id` bigint NOT NULL COMMENT '小组信息ID (关联 project_group_info)',
                                 `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_project_group` (`project_id`, `group_info_id`), -- 防止同一个小组在同一个项目中被重复添加
                                 KEY `idx_project_id` (`project_id`),
                                 KEY `idx_group_info_id` (`group_info_id`),
                                 CONSTRAINT `fk_rel_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE,
                                 CONSTRAINT `fk_rel_group_info` FOREIGN KEY (`group_info_id`) REFERENCES `project_group_info` (`id`) ON DELETE RESTRICT -- 限制：如果小组还在项目中使用，不允许直接删除小组信息
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目-小组关联表';

-- 项目-打分用户关联表
CREATE TABLE `project_scorer` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
                                  `project_id` bigint NOT NULL COMMENT '项目ID',
                                  `user_id` bigint NOT NULL COMMENT '打分用户ID',
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_project_user` (`project_id`,`user_id`),
                                  KEY `idx_user_id` (`user_id`),
                                  CONSTRAINT `fk_scorer_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE,
                                  CONSTRAINT `fk_scorer_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目-打分用户关联表';

-- 打分记录主表
CREATE TABLE `scoring_record` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
                                  `project_id` bigint NOT NULL COMMENT '项目ID',
                                  `group_info_id` bigint NOT NULL COMMENT '被打分小组信息ID',
                                  `user_id` bigint NOT NULL COMMENT '打分用户ID',
                                  `total_score` decimal(10,2) NOT NULL COMMENT '总分',
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '打分时间',
                                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_project_group_user` (`project_id`,`group_info_id`,`user_id`),
                                  KEY `idx_group_info_id` (`group_info_id`),
                                  KEY `idx_project_group_info` (`project_id`,`group_info_id`),
                                  KEY `idx_user_id` (`user_id`),
                                  CONSTRAINT `fk_record_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE,
                                  CONSTRAINT `fk_record_project_group_info` FOREIGN KEY (`project_id`, `group_info_id`)
                                      REFERENCES `project_group` (`project_id`, `group_info_id`) ON DELETE CASCADE,
                                  CONSTRAINT `fk_record_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打分记录主表';

-- 打分记录明细表
CREATE TABLE `scoring_record_detail` (
                                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
                                         `record_id` bigint NOT NULL COMMENT '关联打分记录ID',
                                         `indicator_id` bigint NOT NULL COMMENT '指标ID',
                                         `score` decimal(10,2) NOT NULL COMMENT '该指标的打分值',
                                         PRIMARY KEY (`id`),
                                         UNIQUE KEY `uk_record_indicator` (`record_id`,`indicator_id`),
                                         KEY `idx_indicator_id` (`indicator_id`),
                                         CONSTRAINT `fk_detail_record` FOREIGN KEY (`record_id`) REFERENCES `scoring_record` (`id`) ON DELETE CASCADE,
                                         CONSTRAINT `fk_detail_indicator` FOREIGN KEY (`indicator_id`) REFERENCES `scoring_indicator` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打分记录明细表';

-- 评审组主表
CREATE TABLE `reviewer_group` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评审组ID',
                                  `name` varchar(100) NOT NULL COMMENT '评审组名称',
                                  `description` varchar(500) DEFAULT '' COMMENT '评审组描述',
                                  `creator_id` bigint NOT NULL COMMENT '创建人ID',
                                  `is_enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用：1-是 0-否',
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  KEY `idx_creator_id` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评审组主表';

-- 评审组成员关联表
CREATE TABLE `reviewer_group_member` (
                                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
                                         `group_id` bigint NOT NULL COMMENT '评审组ID',
                                         `user_id` bigint NOT NULL COMMENT '用户ID',
                                         `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
                                         PRIMARY KEY (`id`),
                                         UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
                                         KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评审组成员关联表';

--在 MariaDB 中创建一个事件，定期（如每小时或每天）执行 UPDATE 语句，将 status 同步为正确的值。

-- 1.启用事件调度器
SET GLOBAL event_scheduler = ON;

-- 2.先删除旧的事件（如果存在）
DROP EVENT IF EXISTS update_project_status;

-- 3. 创建新事件
DELIMITER $$

CREATE EVENT IF NOT EXISTS update_project_status
ON SCHEDULE EVERY 1 DAY
STARTS TIMESTAMP(CURRENT_DATE, '00:01:00')
DO
BEGIN
    UPDATE project
    SET status = CASE
        WHEN start_date > CURDATE() THEN 'not_started'
        WHEN start_date <= CURDATE() AND end_date >= CURDATE() THEN 'ongoing'
        WHEN end_date < CURDATE() THEN 'ended'
    END
    WHERE is_enabled = 1
        AND status <> CASE
            WHEN start_date > CURDATE() THEN 'not_started'
            WHEN start_date <= CURDATE() AND end_date >= CURDATE() THEN 'ongoing'
            WHEN end_date < CURDATE() THEN 'ended'
    END;
END$$

DELIMITER ;
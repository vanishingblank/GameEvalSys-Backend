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

CREATE TABLE `scoring_standard` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标准ID',
                                    `creator_id` bigint NOT NULL COMMENT '创建人ID',
                                    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    PRIMARY KEY (`id`),
                                    KEY `idx_creator_id` (`creator_id`),
                                    CONSTRAINT `fk_standard_creator` FOREIGN KEY (`creator_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打分标准主表';

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

CREATE TABLE `project_group` (
                                 `id` bigint NOT NULL AUTO_INCREMENT COMMENT '小组ID',
                                 `project_id` bigint NOT NULL COMMENT '关联项目ID',
                                 `name` varchar(100) NOT NULL COMMENT '小组名称',
                                 `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 KEY `idx_project_id` (`project_id`),
                                 CONSTRAINT `fk_group_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目参与小组表';

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

CREATE TABLE `scoring_record` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
                                  `project_id` bigint NOT NULL COMMENT '项目ID',
                                  `group_id` bigint NOT NULL COMMENT '被打分组ID',
                                  `user_id` bigint NOT NULL COMMENT '打分用户ID',
                                  `total_score` decimal(10,2) NOT NULL COMMENT '总分',
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '打分时间',
                                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_project_group_user` (`project_id`,`group_id`,`user_id`),
                                  KEY `idx_group_id` (`group_id`),
                                  KEY `idx_user_id` (`user_id`),
                                  CONSTRAINT `fk_record_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE,
                                  CONSTRAINT `fk_record_group` FOREIGN KEY (`group_id`) REFERENCES `project_group` (`id`) ON DELETE CASCADE,
                                  CONSTRAINT `fk_record_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打分记录主表';

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


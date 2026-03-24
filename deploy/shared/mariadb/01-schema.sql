CREATE DATABASE IF NOT EXISTS gameeval
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE gameeval;

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'user id',
  `username` varchar(50) NOT NULL COMMENT 'unique username',
  `password` varchar(100) NOT NULL COMMENT 'bcrypt password hash',
  `name` varchar(50) NOT NULL COMMENT 'display name',
  `role` varchar(20) NOT NULL COMMENT 'super_admin/admin/scorer/normal',
  `is_enabled` tinyint(1) DEFAULT 1 COMMENT '1 enabled, 0 disabled',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='system users';

CREATE TABLE IF NOT EXISTS `scoring_standard` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'standard id',
  `creator_id` bigint NOT NULL COMMENT 'creator user id',
  `name` varchar(100) NOT NULL COMMENT 'standard name',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
  PRIMARY KEY (`id`),
  KEY `idx_creator_id` (`creator_id`),
  CONSTRAINT `fk_standard_creator` FOREIGN KEY (`creator_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='scoring standards';

CREATE TABLE IF NOT EXISTS `scoring_indicator` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'indicator id',
  `standard_id` bigint NOT NULL COMMENT 'scoring standard id',
  `name` varchar(100) NOT NULL COMMENT 'indicator name',
  `description` varchar(500) DEFAULT '' COMMENT 'indicator description',
  `min_score` int NOT NULL COMMENT 'min score',
  `max_score` int NOT NULL COMMENT 'max score',
  `sort` int DEFAULT 0 COMMENT 'sort order',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  PRIMARY KEY (`id`),
  KEY `idx_standard_id` (`standard_id`),
  CONSTRAINT `fk_indicator_standard` FOREIGN KEY (`standard_id`) REFERENCES `scoring_standard` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='scoring indicators';

CREATE TABLE IF NOT EXISTS `project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'project id',
  `name` varchar(200) NOT NULL COMMENT 'project name',
  `description` varchar(1000) DEFAULT '' COMMENT 'project description',
  `start_date` date NOT NULL COMMENT 'start date',
  `end_date` date NOT NULL COMMENT 'end date',
  `status` varchar(20) DEFAULT 'not_started' COMMENT 'not_started/ongoing/ended',
  `is_enabled` tinyint(1) DEFAULT 1 COMMENT '1 enabled, 0 disabled',
  `standard_id` bigint NOT NULL COMMENT 'scoring standard id',
  `creator_id` bigint NOT NULL COMMENT 'creator user id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_standard_id` (`standard_id`),
  KEY `idx_creator_id` (`creator_id`),
  CONSTRAINT `fk_project_standard` FOREIGN KEY (`standard_id`) REFERENCES `scoring_standard` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_creator` FOREIGN KEY (`creator_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='projects';

CREATE TABLE IF NOT EXISTS `project_group_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'group info id',
  `name` varchar(100) NOT NULL COMMENT 'group name',
  `description` varchar(500) DEFAULT '' COMMENT 'group description',
  `is_enabled` tinyint(1) DEFAULT 1 COMMENT '1 enabled, 0 disabled',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='group master table';

CREATE TABLE IF NOT EXISTS `project_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'relation id',
  `project_id` bigint NOT NULL COMMENT 'project id',
  `group_info_id` bigint NOT NULL COMMENT 'project_group_info id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_group` (`project_id`, `group_info_id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_group_info_id` (`group_info_id`),
  CONSTRAINT `fk_rel_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rel_group_info` FOREIGN KEY (`group_info_id`) REFERENCES `project_group_info` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='project-group relation';

CREATE TABLE IF NOT EXISTS `project_scorer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'relation id',
  `project_id` bigint NOT NULL COMMENT 'project id',
  `user_id` bigint NOT NULL COMMENT 'scorer user id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_user` (`project_id`, `user_id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_scorer_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_scorer_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='project-scorer relation';

CREATE TABLE IF NOT EXISTS `scoring_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'record id',
  `project_id` bigint NOT NULL COMMENT 'project id',
  `group_id` bigint NOT NULL COMMENT 'project_group id',
  `user_id` bigint NOT NULL COMMENT 'scorer user id',
  `total_score` decimal(10, 2) NOT NULL COMMENT 'total score',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_group_user` (`project_id`, `group_id`, `user_id`),
  KEY `idx_group_id` (`group_id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_record_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_record_group` FOREIGN KEY (`group_id`) REFERENCES `project_group` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_record_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='scoring records';

CREATE TABLE IF NOT EXISTS `scoring_record_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'detail id',
  `record_id` bigint NOT NULL COMMENT 'scoring_record id',
  `indicator_id` bigint NOT NULL COMMENT 'scoring_indicator id',
  `score` decimal(10, 2) NOT NULL COMMENT 'score value',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_indicator` (`record_id`, `indicator_id`),
  KEY `idx_indicator_id` (`indicator_id`),
  CONSTRAINT `fk_detail_record` FOREIGN KEY (`record_id`) REFERENCES `scoring_record` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_detail_indicator` FOREIGN KEY (`indicator_id`) REFERENCES `scoring_indicator` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='scoring record details';

CREATE TABLE IF NOT EXISTS `reviewer_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'reviewer group id',
  `name` varchar(100) NOT NULL COMMENT 'group name',
  `description` varchar(500) DEFAULT '' COMMENT 'group description',
  `creator_id` bigint NOT NULL COMMENT 'creator user id',
  `is_enabled` tinyint(1) DEFAULT 1 COMMENT '1 enabled, 0 disabled',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
  PRIMARY KEY (`id`),
  KEY `idx_creator_id` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='reviewer groups';

CREATE TABLE IF NOT EXISTS `reviewer_group_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'relation id',
  `group_id` bigint NOT NULL COMMENT 'reviewer group id',
  `user_id` bigint NOT NULL COMMENT 'user id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='reviewer group members';

-- 系统用户表
CREATE TABLE `sys_user` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                            `username` varchar(50) NOT NULL COMMENT '用户名（唯一）',
                            `password` varchar(100) NOT NULL COMMENT '密码（加密存储）',
                            `name` varchar(50) NOT NULL COMMENT '真实姓名',
                            `role` varchar(20) NOT NULL COMMENT '角色：super_admin/管理员 admin/打分用户 scorer/普通用户 normal',
                            `is_enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用：1-是 0-否',
                            `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '软删除：0-否 1-是',
                            `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
                            `delete_token` bigint NOT NULL DEFAULT 0 COMMENT '删除占位token，活跃=0，已删=唯一值',
                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_username_token` (`username`,`delete_token`),
                            KEY `idx_role` (`role`),
                            KEY `idx_is_deleted_enabled` (`is_deleted`,`is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 菜单路由表
CREATE TABLE `sys_menu` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
                           `parent_id` bigint DEFAULT 0 COMMENT '父级菜单ID，顶级为0',
                           `menu_code` varchar(64) NOT NULL COMMENT '菜单编码',
                           `menu_type` varchar(20) NOT NULL COMMENT '菜单类型：dir/menu/button',
                           `title` varchar(100) NOT NULL COMMENT '标题',
                           `path` varchar(255) NOT NULL COMMENT '路由路径',
                           `route_name` varchar(64) NOT NULL COMMENT '路由名称',
                           `icon` varchar(64) DEFAULT '' COMMENT '图标',
                           `hidden` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否隐藏',
                           `component_code` varchar(64) DEFAULT '' COMMENT '前端组件映射编码',
                           `sort_num` int NOT NULL DEFAULT 0 COMMENT '排序号',
                           `is_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                           `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
                           `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           UNIQUE KEY `uk_menu_code` (`menu_code`),
                           KEY `idx_parent_id` (`parent_id`),
                           KEY `idx_sort_num` (`sort_num`),
                           KEY `idx_enabled_deleted` (`is_enabled`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单路由表';

-- 角色菜单关联表
CREATE TABLE `sys_role_menu` (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                `role_code` varchar(32) NOT NULL COMMENT '角色编码',
                                `menu_code` varchar(64) NOT NULL COMMENT '菜单编码',
                                `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`),
                                UNIQUE KEY `uk_role_menu` (`role_code`, `menu_code`),
                                KEY `idx_role_code` (`role_code`),
                                KEY `idx_menu_code` (`menu_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_type`, `title`, `path`, `route_name`, `icon`, `hidden`, `component_code`, `sort_num`, `is_enabled`, `is_deleted`) VALUE
    (1, 0, 'home', 'menu', '首页', '/home', 'home', 'HomeFilled', 0, 'normal-home', 1, 1, 0),
    (2, 0, 'scoring-list', 'menu', '打分项目列表', '/scoring', 'scoringRoot', 'Edit', 0, 'normal-scoring-list', 2, 1, 0),
    (6, 0, 'admin', 'dir', '管理面板', '/admin', 'adminRoot', 'Setting', 0, '', 3, 1, 0),
    (21, 0, 'statistic', 'dir', '数据统计', '/admin/statistic', 'statisticRoot', 'Histogram', 0, '', 4, 1, 0),
    (17, 0, 'super-admin', 'dir', '后台管理', '/super-admin', '/super-admin', 'Grid', 0, '', 5, 1, 0),
    (3, 2, 'scoring-project', 'menu', '项目打分', '/scoring/:projectId', 'projectScoring', '', 1, 'normal-project-scoring', 2, 1, 0),
    (7, 6, 'admin-project', 'menu', '项目管理', '/admin/project', 'projectList', 'Management', 0, 'admin-project-list', 5, 1, 0),
    (20, 6, 'admin-project-group', 'menu', '项目受审队伍管理', '/admin/project-groups', 'projectGroupList', 'User', 0, 'admin-project-group', 4, 1, 0),
    (11, 6, 'admin-reviewer-group', 'menu', '评审队伍管理', '/admin/reviewer-group', 'reviewerGroupList', 'UserFilled', 0, 'admin-reviewer-group', 1, 1, 0),
    (14, 6, 'admin-scoring-stds', 'menu', '打分标准', '/admin/scoring-stds', 'scoringStdList', 'Checked', 0, 'admin-scoring-stds', 3, 1, 0),
    (15, 6, 'admin-user', 'menu', '用户管理', '/admin/user', 'userList', 'User', 0, 'admin-user', 2, 1, 0),
    (8, 7, 'admin-project-edit', 'menu', '编辑项目', '/admin/project/edit/:id', 'projectEdit', 'Management', 1, 'admin-project-edit', 1, 1, 0),
    (12, 11, 'admin-reviewer-group-add', 'menu', '评审队伍添加', '/admin/reviewer-groups/add', 'reviewerGroupAdd', 'OfficeBuilding', 1, 'admin-reviewer-group-upsert', 1, 1, 0),
    (13, 11, 'admin-reviewer-group-edit', 'menu', '评审队伍编辑', '/admin/reviewer-groups/edit/:id', 'reviewerGroupEdit', 'OfficeBuilding', 1, 'admin-reviewer-group-upsert', 2, 1, 0),
    (16, 21, 'admin-statistic', 'menu', '平台统计', '/admin/statistic/platform', 'adminStatistic', 'Histogram', 0, 'admin-statistic', 1, 1, 0),
    (9, 21, 'admin-project-statistic', 'menu', '项目打分统计', '/admin/project/statistic', 'projectStatisticList', 'DataAnalysis', 0, 'admin-project-statistic', 2, 1, 0),
    (10, 9, 'admin-project-statistic-detail', 'menu', '打分统计详情', '/admin/project/statistic/:projectId', 'projectStatisticDetail', 'DataAnalysis', 1, 'admin-project-statistic-detail', 1, 1, 0),
    (18, 17, 'super-monitor-online', 'menu', '用户在线管理', '/super-admin/monitor/online', 'monitorOnline', 'UserFilled', 0, 'super-monitor-online', 3, 1, 0),
    (19, 17, 'super-monitor-server', 'menu', '服务器面板', '/super-admin/monitor/server', 'monitorServer', 'DataLine', 0, 'super-monitor-server', 2, 1, 0),
    (23, 17, 'super-admin-menu-management', 'menu', '菜单管理', '/super-admin/menu', 'menuManagement', 'Grid', 0, 'super-menu-management', 1, 1, 0);

INSERT INTO `sys_role_menu` (`role_code`, `menu_code`) VALUES
('normal', 'home'),

('scorer', 'home'),
('scorer', 'scoring-list'),
('scorer', 'scoring-project'),

('admin', 'home'),
('admin', 'admin'),
('admin', 'admin-project'),
('admin', 'admin-project-edit'),
('admin', 'admin-project-statistic'),
('admin', 'admin-project-statistic-detail'),
('admin', 'admin-reviewer-group'),
('admin', 'admin-reviewer-group-add'),
('admin', 'admin-reviewer-group-edit'),
('admin', 'admin-scoring-stds'),
('admin', 'admin-user'),
('admin', 'admin-statistic'),
('admin', 'admin-project-group'),

('super_admin', 'home'),
('super_admin', 'scoring-list'),
('super_admin', 'scoring-project'),
('super_admin', 'admin'),
('super_admin', 'admin-project'),
('super_admin', 'admin-project-edit'),
('super_admin', 'admin-project-statistic'),
('super_admin', 'admin-project-statistic-detail'),
('super_admin', 'admin-reviewer-group'),
('super_admin', 'admin-reviewer-group-add'),
('super_admin', 'admin-reviewer-group-edit'),
('super_admin', 'admin-scoring-stds'),
('super_admin', 'admin-user'),
('super_admin', 'admin-statistic'),
('super_admin', 'admin-project-group'),

('super_admin', 'super-admin-menu-management'),
('super_admin', 'super-monitor-online'),
('super_admin', 'super-monitor-server');

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

-- 打分指标分类表
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

-- 打分指标表
CREATE TABLE `scoring_indicator` (
                                     `id` bigint NOT NULL AUTO_INCREMENT COMMENT '指标ID',
                                     `standard_id` bigint NOT NULL COMMENT '关联标准ID',
                                     `category_id` bigint DEFAULT NULL COMMENT '分类ID',
                                     `name` varchar(100) NOT NULL COMMENT '指标名称',
                                     `description` varchar(500) DEFAULT '' COMMENT '指标说明',
                                     `min_score` int NOT NULL COMMENT '分值最小值',
                                     `max_score` int NOT NULL COMMENT '分值最大值',
                                     `sort` int DEFAULT 0 COMMENT '排序号',
                                     `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     PRIMARY KEY (`id`),
                                     KEY `idx_standard_id` (`standard_id`),
                                     KEY `idx_category_id` (`category_id`),
                                     CONSTRAINT `fk_indicator_standard` FOREIGN KEY (`standard_id`) REFERENCES `scoring_standard` (`id`) ON DELETE CASCADE,
                                     CONSTRAINT `fk_indicator_category` FOREIGN KEY (`category_id`) REFERENCES `scoring_indicator_category` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打分指标表';

-- 课题项目表
CREATE TABLE `project` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目ID',
                           `name` varchar(200) NOT NULL COMMENT '项目名称',
                           `description` varchar(1000) DEFAULT '' COMMENT '项目描述',
                           `start_date` datetime NOT NULL COMMENT '起始时间',
                           `end_date` datetime NOT NULL COMMENT '结束时间',
                           `status` varchar(20) DEFAULT 'not_started' COMMENT '项目状态：not_started-未开始/ongoing-进行中/ended-已结束',
                           `is_enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用：1-是 0-否',
                           `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '软删除：0-否 1-是',
                           `standard_id` bigint NOT NULL COMMENT '关联打分标准ID',
                           `malicious_rule_type` varchar(20) NOT NULL DEFAULT 'AUTO' COMMENT '恶意打分判定规则：AUTO/THRESHOLD',
                           `malicious_score_lower` decimal(10,2) DEFAULT NULL COMMENT '恶意阈值下限（THRESHOLD模式）',
                           `malicious_score_upper` decimal(10,2) DEFAULT NULL COMMENT '恶意阈值上限（THRESHOLD模式）',
                           `creator_id` bigint NOT NULL COMMENT '创建人ID',
                           `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           KEY `idx_status` (`status`),
                           KEY `idx_project_is_deleted` (`is_deleted`),
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
                                      `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '软删除：0-否 1-是',
                                      `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      PRIMARY KEY (`id`),
                                      KEY `idx_group_info_is_deleted` (`is_deleted`)
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
                                  `is_malicious` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否恶意打分：0-否 1-是',
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '打分时间',
                                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_project_group_user` (`project_id`,`group_info_id`,`user_id`),
                                  KEY `idx_group_info_id` (`group_info_id`),
                                  KEY `idx_project_group_info` (`project_id`,`group_info_id`),
                                  KEY `idx_user_id` (`user_id`),
                                  KEY `idx_project_user_malicious` (`project_id`,`user_id`,`is_malicious`),
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

-- 项目统计汇总表（项目维度）
CREATE TABLE `project_statistics_project_summary` (
                                                    `project_id` bigint NOT NULL COMMENT '项目ID',
                                                    `raw_average_score` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '原始平均分',
                                                    `normalized_average_score` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '标准化后平均分',
                                                    `processed_average_score` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '处理后平均分',
                                                    `abnormal_count` int NOT NULL DEFAULT 0 COMMENT '异常评分数',
                                                    `sample_size` int NOT NULL DEFAULT 0 COMMENT '总样本数',
                                                    `valid_sample_size` int NOT NULL DEFAULT 0 COMMENT '有效样本数',
                                                    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                    PRIMARY KEY (`project_id`),
                                                    CONSTRAINT `fk_psps_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目统计汇总表（项目维度）';

-- 项目统计汇总表（小组维度）
CREATE TABLE `project_statistics_group_summary` (
                                                   `project_id` bigint NOT NULL COMMENT '项目ID',
                                                   `group_id` bigint NOT NULL COMMENT '小组ID',
                                                   `group_name` varchar(100) NOT NULL COMMENT '小组名称',
                                                   `raw_average_score` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '原始平均分',
                                                   `normalized_average_score` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '标准化后平均分',
                                                   `processed_average_score` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '处理后平均分',
                                                   `abnormal_count` int NOT NULL DEFAULT 0 COMMENT '异常评分数',
                                                   `sample_size` int NOT NULL DEFAULT 0 COMMENT '总样本数',
                                                   `valid_sample_size` int NOT NULL DEFAULT 0 COMMENT '有效样本数',
                                                   `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                   PRIMARY KEY (`project_id`, `group_id`),
                                                   KEY `idx_psgs_project_id` (`project_id`),
                                                   CONSTRAINT `fk_psgs_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目统计汇总表（小组维度）';

-- 项目统计汇总表（指标维度）
CREATE TABLE `project_statistics_indicator_summary` (
                                                      `project_id` bigint NOT NULL COMMENT '项目ID',
                                                      `indicator_id` bigint NOT NULL COMMENT '指标ID',
                                                      `indicator_name` varchar(100) NOT NULL COMMENT '指标名称',
                                                      `raw_average_score` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '原始平均分',
                                                      `normalized_average_score` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '标准化后平均分',
                                                      `processed_average_score` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '处理后平均分',
                                                      `abnormal_count` int NOT NULL DEFAULT 0 COMMENT '异常评分数',
                                                      `total_abnormal_count` int NOT NULL DEFAULT 0 COMMENT '总异常评分数',
                                                      `sample_size` int NOT NULL DEFAULT 0 COMMENT '总样本数',
                                                      `valid_sample_size` int NOT NULL DEFAULT 0 COMMENT '有效样本数',
                                                      `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                      PRIMARY KEY (`project_id`, `indicator_id`),
                                                      KEY `idx_psis_project_id` (`project_id`),
                                                      CONSTRAINT `fk_psis_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目统计汇总表（指标维度）';

-- 项目统计汇总表（评委分布）
CREATE TABLE `project_statistics_scorer_distribution_summary` (
                                                                `project_id` bigint NOT NULL COMMENT '项目ID',
                                                                `user_id` bigint NOT NULL COMMENT '打分用户ID',
                                                                `user_name` varchar(100) NOT NULL COMMENT '打分用户姓名',
                                                                `score_range` varchar(20) NOT NULL COMMENT '分数区间',
                                                                `count` int NOT NULL DEFAULT 0 COMMENT '数量',
                                                                `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                PRIMARY KEY (`project_id`, `user_id`, `score_range`),
                                                                KEY `idx_pssds_project_id` (`project_id`),
                                                                CONSTRAINT `fk_pssds_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目统计汇总表（评委分布）';

-- 评审组主表
CREATE TABLE `reviewer_group` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评审组ID',
                                  `name` varchar(100) NOT NULL COMMENT '评审组名称',
                                  `description` varchar(500) DEFAULT '' COMMENT '评审组描述',
                                  `creator_id` bigint NOT NULL COMMENT '创建人ID',
                                  `is_enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用：1-是 0-否',
                                  `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '软删除：0-否 1-是',
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  KEY `idx_creator_id` (`creator_id`),
                                  KEY `idx_reviewer_group_is_deleted` (`is_deleted`)
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

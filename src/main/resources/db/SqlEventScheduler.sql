
-- 每日凌晨更新项目状态
CREATE TABLE IF NOT EXISTS `project_status_log` (
                                                    `id` bigint NOT NULL AUTO_INCREMENT,
                                                    `execute_time` datetime NOT NULL,
                                                    `step` varchar(50) NOT NULL,
    `affected_rows` int DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_execute_time` (`execute_time`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目状态更新日志';

-- 1. 确保事件调度器已开启
SET GLOBAL event_scheduler = ON;

-- 2. 删除旧事件（如果存在）
DROP EVENT IF EXISTS `evt_daily_update_project_status`;

DELIMITER $$

CREATE EVENT `evt_daily_update_project_status`
ON SCHEDULE EVERY 1 DAY
STARTS '2024-01-01 00:00:00'  -- 首次执行时间
ON COMPLETION PRESERVE
ENABLE
COMMENT '每日凌晨自动更新项目状态'
DO
BEGIN


    -- 1. 将符合开始日期条件的 'not_started' 更新为 'ongoing'
UPDATE project
SET status = 'ongoing',
    update_time = NOW()
WHERE status = 'not_started'
  AND start_date <= CURDATE()
  AND end_date >= CURDATE();


-- 2. 将符合结束日期条件的更新为 'ended'
UPDATE project
SET status = 'ended',
    update_time = NOW()
WHERE status IN ('not_started', 'ongoing')
  AND end_date < CURDATE();

END$$

DELIMITER ;

--创建一个事件，定期（如每小时或每天）执行 UPDATE 语句，将 status 同步为正确的值。

-- 1.启用事件调度器
SET GLOBAL event_scheduler = ON;

-- 2.先删除旧的事件（如果存在）
DROP EVENT IF EXISTS update_project_status;

--  创建新事件（MySql版）
CREATE EVENT update_project_status
ON SCHEDULE EVERY 1 DAY
STARTS CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 01:00:00')  -- 明天凌晨1点开始
ON COMPLETION PRESERVE  -- 事件执行后保留，不删除
ENABLE  -- 启用事件
COMMENT '每天定时更新项目状态'
DO
BEGIN
    UPDATE project
    SET status = CASE
                 WHEN CURDATE() < start_date THEN 'not_started'
                 WHEN CURDATE() BETWEEN start_date AND end_date THEN 'ongoing'
                 WHEN CURDATE() > end_date THEN 'ended'
    END
    WHERE status != CASE
        WHEN CURDATE() < start_date THEN 'not_started'
        WHEN CURDATE() BETWEEN start_date AND end_date THEN 'ongoing'
        WHEN CURDATE() > end_date THEN 'ended'
    END;

    -- 可选：记录执行日志
    -- INSERT INTO event_log (event_name, execute_time, affected_rows)
    -- VALUES ('update_project_status', NOW(), ROW_COUNT());
END;

-- madiaDB版
DELIMITER $$

CREATE EVENT update_project_status
ON SCHEDULE EVERY 1 DAY
STARTS DATE_ADD(CURDATE(), INTERVAL 1 DAY)
ON COMPLETION PRESERVE  
ENABLE  
COMMENT '每天定时更新项目状态'
DO
BEGIN
    UPDATE project
    SET status = CASE
        WHEN CURDATE() < start_date THEN 'not_started'
        WHEN CURDATE() BETWEEN start_date AND end_date THEN 'ongoing'
        WHEN CURDATE() > end_date THEN 'ended'
    END
    WHERE status != CASE
        WHEN CURDATE() < start_date THEN 'not_started'
        WHEN CURDATE() BETWEEN start_date AND end_date THEN 'ongoing'
        WHEN CURDATE() > end_date THEN 'ended'
    END;
END$$

DELIMITER ;
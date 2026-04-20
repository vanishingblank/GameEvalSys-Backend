-- 2026-04-19
-- 目标：统一项目时间类型与状态计算口径（DATETIME + NOW）

-- 1) 将项目起止时间从 DATE 升级为 DATETIME
ALTER TABLE project
    MODIFY COLUMN start_date DATETIME NOT NULL COMMENT '起始时间',
    MODIFY COLUMN end_date   DATETIME NOT NULL COMMENT '结束时间';

-- 2) 基于统一口径补齐一次项目状态
UPDATE project
SET status = CASE
    WHEN NOW() < start_date THEN 'not_started'
    WHEN NOW() > end_date THEN 'ended'
    ELSE 'ongoing'
END
WHERE status <> CASE
    WHEN NOW() < start_date THEN 'not_started'
    WHEN NOW() > end_date THEN 'ended'
    ELSE 'ongoing'
END;

-- 事件调度器更新
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
        WHEN NOW() < start_date THEN 'not_started'
        WHEN NOW() > end_date THEN 'ended'
        ELSE 'ongoing'
    END
    WHERE status <> CASE
            WHEN NOW() < start_date THEN 'not_started'
            WHEN NOW() > end_date THEN 'ended'
            ELSE 'ongoing'
    END;
END$$

DELIMITER ;
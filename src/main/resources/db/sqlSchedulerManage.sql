-- 查看所有事件
SHOW EVENTS;

-- 查看事件详细信息
SHOW CREATE EVENT update_project_status;

-- 查看事件状态
SELECT
    EVENT_NAME,
    EVENT_SCHEMA,
    STATUS,
    EVENT_TYPE,
    EXECUTE_AT,
    INTERVAL_VALUE,
    INTERVAL_FIELD,
    STARTS,
    ENDS,
    LAST_EXECUTED,
    EVENT_DEFINITION
FROM information_schema.EVENTS
WHERE EVENT_NAME = 'update_project_status';

-- 暂停事件
ALTER EVENT update_project_status DISABLE;

-- 恢复事件
ALTER EVENT update_project_status ENABLE;

-- 修改事件执行时间
ALTER EVENT update_project_status
ON SCHEDULE EVERY 1 DAY
STARTS CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 03:00:00');
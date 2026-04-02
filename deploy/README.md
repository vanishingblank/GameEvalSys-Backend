# Deployment Assets

该目录包含面向生产环境的部署模板，不会修改 `src/main/resources` 中现有的开发配置

## 结构

- `docker-single/`: single Linux host deployment with three containers (`backend`, `mariadb`, `redis`)
- `lxc-multi-ct/`: multi-CT deployment plan and files for LXC/Proxmox style environments
- `shared/`: shared database and cache initialization assets

## 数据库EVENT事件管理
部署成功后进入数据库手动查看是否创建成功事件(若无则在`shared/mariadb/01-schema.sql`的文件尾部手动添加自动更新项目状态的数据库事件)
~~~sql
USE gameeval;
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
~~~
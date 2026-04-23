# gameeval\-mariadb 数据库备份操作文档

## 一、文档说明

本文档基于提供的 docker\-compose 配置（MariaDB 11\.4 容器化部署）编写，针对 `sh: 1: exec: mysqldump: not found` 错误已完成修复，包含手动备份、定时自动备份、备份恢复、备份清理及常见问题排查全套操作，确保数据安全可追溯，所有命令/脚本可直接复制执行，无需额外修改（仅需替换数据库名）。

## 二、基础信息确认（与compose配置一致）

- 容器名称：`gameeval\-mariadb`（固定，compose中已配置）

- 数据库名称：`$\{DB\_NAME\}`（实际以 \.env 文件中配置为准，下文统一用 `gameeval\_db` 代指，需根据实际情况替换）

- 数据挂载目录：宿主机 `\.\./shared/mariadb/`（初始化脚本目录）、`mariadb\_data`（数据卷）

- 备份建议目录：宿主机 `/data/backup/mariadb`（可自定义，需提前创建）

- MariaDB 11\.4 镜像无 `mysqldump`，统一使用 `mariadb\-dump` 工具执行备份

## 三、前置准备（宿主机执行）

### 3\.1 创建备份目录

创建备份文件存储目录，并赋予合适权限，避免备份时出现权限报错：

```bash
# 创建备份根目录（按需修改路径，建议保持默认）
mkdir -p /data/backup/mariadb

# 赋予目录权限（避免权限不足）
chmod 755 /data/backup/mariadb
```

### 3\.2 确认容器运行状态

备份前需确保 MariaDB 容器正常运行，执行以下命令校验：

```bash
# 查看容器运行状态
docker ps | grep gameeval-mariadb
```

✅ 输出包含 `gameeval\-mariadb` 且状态为 `Up`，即为正常；若未运行，执行 `docker\-compose up \-d` 启动容器。

## 四、手动备份（立即执行，适合临时备份）

提供两种备份方式，均已修复命令错误，直接复制执行即可，无需手动输入数据库密码（自动读取容器环境变量）。

### 4 纯净SQL备份（兼容mysql的备份模式）
~~~bash
docker exec gameeval-mariadb mariadb-dump -u root -p"$(docker exec gameeval-mariadb printenv MARIADB_ROOT_PASSWORD)" --skip-comments gameeval > /data/backup/mariadb/clean_backup_gameeval_db_$(date +%Y%m%d_%H%M%S).sql
~~~
- 直接备份到当前目录下
~~~bash
docker exec gameeval-mariadb mariadb-dump -u root -p"$(docker exec gameeval-mariadb printenv MARIADB_ROOT_PASSWORD)" --skip-comments gameeval > clean_backup_gameeval_db_$(date +%Y%m%d_%H%M%S).sql
~~~

### 4\.1 普通SQL备份（含结构\+数据，易查看）

```bash
# 单条命令执行备份（替换 gameeval_db 为你的实际数据库名）
docker exec gameeval-mariadb mariadb-dump -u root -p"$(docker exec gameeval-mariadb printenv MARIADB_ROOT_PASSWORD)" gameeval_db > /data/backup/mariadb/gameeval_db_$(date +%Y%m%d_%H%M%S).sql
```

### 4\.2 压缩备份（节省磁盘空间，生产环境推荐）

```bash
# 备份并压缩为 .sql.gz 格式（替换 gameeval_db 为你的实际数据库名）
docker exec gameeval-mariadb mariadb-dump -u root -p"$(docker exec gameeval-mariadb printenv MARIADB_ROOT_PASSWORD)" gameeval_db | gzip > /data/backup/mariadb/gameeval_db_$(date +%Y%m%d_%H%M%S).sql.gz
```

### 4\.3 备份验证

执行备份命令后，进入备份目录查看是否生成备份文件：

```bash
ls -l /data/backup/mariadb/
```

✅ 出现以 `gameeval\_db\_` 开头、带时间戳的 \.sql 或 \.sql\.gz 文件，即为备份成功。

## 五、定时自动备份（生产环境必备，无需人工干预）

通过 Linux crontab 定时任务，结合备份脚本，实现每日自动备份、压缩，同时自动清理旧备份，避免磁盘占满。

### 5\.1 编写备份脚本

创建备份脚本文件，内容已优化，仅需替换数据库名即可：

```bash
# 创建脚本文件
vi /data/backup/mariadb/backup_mariadb.sh
```

脚本内容（直接复制粘贴，修改 `DB\_NAME` 为你的实际数据库名）：

```bash
#!/bin/bash
# 配置信息（仅需修改 DB_NAME 为你的实际数据库名）
CONTAINER_NAME="gameeval-mariadb"  # 容器名称（固定，无需修改）
DB_NAME="gameeval_db"              # 替换为 .env 文件中的 DB_NAME
BACKUP_DIR="/data/backup/mariadb"  # 备份目录（与前置准备一致，无需修改）
BACKUP_TIME=$(date +%Y%m%d_%H%M%S) # 自动生成时间戳，避免文件覆盖
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_${BACKUP_TIME}.sql.gz"

# 自动获取容器内 MariaDB root 密码（无需手动填写，避免泄露）
DB_ROOT_PWD=$(docker exec ${CONTAINER_NAME} printenv MARIADB_ROOT_PASSWORD)

# 执行备份（使用 mariadb-dump，修复报错问题）
docker exec ${CONTAINER_NAME} mariadb-dump -u root -p"${DB_ROOT_PWD}" ${DB_NAME} | gzip > ${BACKUP_FILE}

# 自动清理7天前的旧备份（可修改 -mtime +7 调整保留天数，如 +30 保留30天）
find ${BACKUP_DIR} -name "${DB_NAME}_*.sql.gz" -mtime +7 -delete

# 打印备份完成信息（便于排查问题）
echo "$(date +%Y-%m-%d %H:%M:%S) - MariaDB 备份完成：${BACKUP_FILE}"
```

### 5\.2 赋予脚本执行权限

脚本创建后，需赋予执行权限，否则无法运行：

```bash
chmod +x /data/backup/mariadb/backup_mariadb.sh
```

### 5\.3 测试脚本（验证可用性）

手动执行一次脚本，确认备份正常，避免定时任务配置后无法运行：

```bash
/data/backup/mariadb/backup_mariadb.sh
```

✅ 执行后输出 “备份完成” 提示，且备份目录生成 \.sql\.gz 文件，即为脚本正常。

### 5\.4 添加定时任务（每日凌晨2点自动备份）

通过 crontab 配置定时任务，设置每日凌晨2点执行备份（避开业务高峰）：

```bash
# 编辑 crontab 定时任务
crontab -e
```

在打开的文件中，添加以下内容（直接复制，无需修改）：

```bash
# 每日凌晨2点执行 MariaDB 自动备份，日志写入备份目录
0 2 * * * /data/backup/mariadb/backup_mariadb.sh >> /data/backup/mariadb/backup_log_$(date +%Y%m%d).log 2>&1
```

### 5\.5 查看定时任务

配置完成后，查看定时任务是否添加成功：

```bash
crontab -l
```

✅ 输出包含上述添加的定时任务内容，即为配置成功。

## 六、数据库恢复（备份文件还原，紧急场景使用）

当数据库出现异常、数据丢失时，可通过备份文件恢复，以下命令已修复，支持普通SQL和压缩文件两种场景，恢复前请确认无未备份的重要数据（恢复会覆盖现有数据）。

### 6\.1 恢复普通 \.sql 备份文件

```bash
# 1. 进入备份目录（替换为你的备份目录）
cd /data/backup/mariadb

# 2. 执行恢复命令（替换 备份文件名.sql 为实际备份文件名，替换 gameeval_db 为实际数据库名）
docker exec -i gameeval-mariadb mysql -u root -p"$(docker exec gameeval-mariadb printenv MARIADB_ROOT_PASSWORD)" gameeval_db < 备份文件名.sql
```

### 6\.2 恢复压缩 \.sql\.gz 备份文件

无需手动解压，直接解压并恢复，效率更高：

```bash
# 进入备份目录
cd /data/backup/mariadb

# 执行恢复命令（替换 备份文件名.sql.gz 为实际备份文件名，替换 gameeval_db 为实际数据库名）
gunzip < 备份文件名.sql.gz | docker exec -i gameeval-mariadb mysql -u root -p"$(docker exec gameeval-mariadb printenv MARIADB_ROOT_PASSWORD)" gameeval_db
```

### 6\.3 恢复注意事项

- 恢复前建议停止连接数据库的应用服务（执行 `docker\-compose stop 应用服务名`），避免恢复过程中数据写入冲突。

- 恢复完成后，重启应用服务（`docker\-compose start 应用服务名`），验证数据是否正常。

- 若恢复失败，查看备份文件是否完整，可重新执行备份后再尝试恢复。

## 七、备份文件管理

1. 备份保留策略：脚本默认保留最近7天的备份，可修改脚本中 `\-mtime \+7` 调整（如 `\-mtime \+30` 表示保留30天，超过30天的自动删除）。

2. 备份日志管理：定时任务的备份日志存储在 `/data/backup/mariadb/` 目录，命名格式为 `backup\_log\_日期\.log`，可通过日志排查备份失败原因。

3. 异地备份：重要业务数据，建议定期将备份文件拷贝到外部存储（如U盘、云存储），避免宿主机故障导致备份丢失。

4. 备份校验：每月至少一次，随机选择一个备份文件执行恢复测试，确保备份文件有效，避免紧急情况下无法恢复。

## 八、常见问题排查（含修复后可能遇到的问题）

### 8\.1 备份/恢复命令报错：权限 denied

原因：备份目录权限不足，解决方案：

```bash
# 赋予备份目录最高权限（临时排查，后续可改回755）
chmod 777 /data/backup/mariadb

# 若仍报错，重启 MariaDB 容器
docker restart gameeval-mariadb
```

### 8\.2 定时任务不执行

排查步骤：

1. 查看备份日志，确认报错原因：`cat /data/backup/mariadb/backup\_log\_当前日期\.log`

2. 检查 crontab 服务状态：`systemctl status crond`（CentOS）或 `systemctl status cron`（Ubuntu），若未运行，执行 `systemctl start crond` 启动。

3. 确认脚本路径、数据库名是否正确，重新测试脚本是否能正常运行。

### 8\.3 恢复后数据不显示

原因：数据库名输入错误、应用服务未重启，解决方案：

- 确认恢复命令中的 `gameeval\_db` 与实际数据库名一致（查看 \.env 文件中的 DB\_NAME）。

- 重启应用服务：`docker\-compose restart`，再重新查看数据。

### 8\.4 备份命令提示 “Access denied”

原因：root 密码获取失败，解决方案：

```bash
# 手动查看容器内 root 密码
docker exec gameeval-mariadb printenv MARIADB_ROOT_PASSWORD

# 手动输入密码执行备份（替换 密码 为实际密码）
docker exec gameeval-mariadb mariadb-dump -u root -p密码 gameeval_db > /data/backup/mariadb/备份文件名.sql
```


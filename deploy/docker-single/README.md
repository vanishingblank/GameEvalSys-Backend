# Single-host Docker deployment

此文件夹用于在一台Linux服务器部署三个docker容器：

1. `mariadb`
2. `redis`
3. `backend`

## Quick start

1. 复制env 模板:
   - `cp .env.example .env`
2. 设置redis、mariaDb、平台初始管理员密码等参数在： `.env`.
3. 选择一个平台初始管理员的初始密码加密模式:
   - 明文方式: 取消 `APP_ADMIN_INIT_PASSWORD` 的注释并且赋值
   - 哈希加密方式 (推荐): 取消 `APP_ADMIN_INIT_PASSWORD_HASH`的注释并且赋值

***4. 启动服务:***
   - 在`deploy/docker-single` 路径下运行： 
   ``` bash
   docker compose --env-file .env up -d --build
   ```
### 若出现镜像拉取速度慢的情况
- 单独分别拉取以下镜像
~~~bash
docker pull eclipse-temurin:17-jre-alpine-3.22
docker pull  maven:3.9.9-eclipse-temurin-17
~~~

## 代码更改后docker镜像操作
- 修改了src 或 pom.xml
- 需要：重新 build
~~~ shell
docker compose -f deploy/docker-single/docker-compose.yml up -d --build backend
~~~

## 若只更改spring boot配置文件
- 不需要 build
~~~shell
docker compose -f deploy/docker-single/docker-compose.yml restart backend
~~~


## Notes

- MariaDB schema runs automatically from `../shared/mariadb/01-schema.sql` on first database initialization.
- Redis bootstrap runs once via `../shared/redis/redis-init.sh`.
- Backend loads external config from `./backend/application-docker.yml`, so your `src/main/resources/application.yml` remains unchanged.

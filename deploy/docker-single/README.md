# Single-host Docker deployment

此文件夹用于在一台Linux服务器部署三个docker容器：

1. `mariadb`
2. `redis`
3. `backend`

## Quick start

1. 复制env 模板:
   - `cp .env.example .env`
2. Update secrets in `.env`.
3. Choose one first-admin password mode:
   - plaintext: set `APP_ADMIN_INIT_PASSWORD`
   - bcrypt hash (recommended): set `APP_ADMIN_INIT_PASSWORD_HASH`
4. Start services:
   - `docker compose --env-file .env up -d --build`

## Notes

- MariaDB schema runs automatically from `../shared/mariadb/01-schema.sql` on first database initialization.
- Redis bootstrap runs once via `../shared/redis/redis-init.sh`.
- Backend loads external config from `./backend/application-docker.yml`, so your `src/main/resources/application.yml` remains unchanged.

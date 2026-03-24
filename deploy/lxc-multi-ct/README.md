# LXC multi-CT deployment

Recommended production split: 3 CTs.

1. `CT-DB` (MariaDB only)
2. `CT-REDIS` (Redis only)
3. `CT-APP` (backend only)

This split is recommended because DB and Redis have different IO and memory profiles.

## Suggested CT resource baseline

- CT-DB: 2 vCPU, 4-8 GB RAM, SSD-backed disk
- CT-REDIS: 1 vCPU, 1-2 GB RAM
- CT-APP: 2 vCPU, 2-4 GB RAM

## CT-DB setup (MariaDB)

1. Install MariaDB server.
2. Create db and schema:
   - `mariadb -uroot -p < /path/to/01-schema.sql`
3. Create app user with least privilege:
   - `CREATE USER 'gameeval'@'10.10.10.%' IDENTIFIED BY 'strong_password';`
   - `GRANT ALL PRIVILEGES ON gameeval.* TO 'gameeval'@'10.10.10.%';`
   - `FLUSH PRIVILEGES;`

## CT-REDIS setup

1. Install Redis 7.
2. Use provided `redis.conf` and set `requirepass` in service start command or config.
3. Run bootstrap script once:
   - `REDIS_HOST=127.0.0.1 REDIS_PORT=6379 REDIS_PASSWORD=... sh redis-init.sh`

## CT-APP setup (Docker in CT)

1. Go to `ct-backend`.
2. Copy env template:
   - `cp .env.example .env`
3. Edit `.env` to point `DB_HOST` and `REDIS_HOST` to CT-DB and CT-REDIS IPs.
4. Start backend:
   - `docker compose --env-file .env up -d --build`

## Optional compact mode

If resources are tight, you can merge Redis into CT-APP and keep MariaDB isolated.
Do not merge MariaDB with backend in production unless this is a low-traffic environment.

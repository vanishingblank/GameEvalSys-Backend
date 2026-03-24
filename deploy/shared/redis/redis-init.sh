#!/bin/sh
set -eu

REDIS_HOST="${REDIS_HOST:-redis}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
APP_REDIS_USER="${APP_REDIS_USER:-gameeval}"
APP_REDIS_USER_PASSWORD="${APP_REDIS_USER_PASSWORD:-}"

run_redis_cli() {
  if [ -n "$REDIS_PASSWORD" ]; then
    redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -a "$REDIS_PASSWORD" "$@"
  else
    redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" "$@"
  fi
}

i=0
until [ "$i" -ge 30 ]
do
  if run_redis_cli PING >/dev/null 2>&1; then
    break
  fi
  i=$((i + 1))
  sleep 1
done

if [ "$i" -ge 30 ]; then
  echo "Redis bootstrap failed: redis is unreachable."
  exit 1
fi

if [ -n "$APP_REDIS_USER_PASSWORD" ]; then
  run_redis_cli ACL SETUSER "$APP_REDIS_USER" on ">$APP_REDIS_USER_PASSWORD" "~*" "+@all"
  echo "Redis ACL user initialized: $APP_REDIS_USER"
fi

run_redis_cli SET system:bootstrap:timestamp "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "Redis bootstrap completed."

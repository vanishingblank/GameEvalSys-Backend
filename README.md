MySQL & Redis 部署
```
services:
  mysql:
    image: mysql:8.0
    container_name: mysql
    restart: always
    ports:
      - "3314:3306"
    environment:
      MYSQL_ROOT_PASSWORD: 123456
      MYSQL_DATABASE: gameeval
      MYSQL_USER: ytuoj
      MYSQL_PASSWORD: 123456
      TZ: Asia/Shanghai
    volumes:
      - ./data/mysql:/var/lib/mysql
      - ./conf/mysql/my.cnf:/etc/mysql/conf.d/my.cnf
    command:
      --default-authentication-plugin=mysql_native_password
    networks:
      - backend

  redis:
    image: redis:7.2-alpine
    container_name: redis
    restart: always
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes --user default on >redis123456 allcommands allkeys
    networks:
      - backend

networks:
  backend:
    driver: bridge

```

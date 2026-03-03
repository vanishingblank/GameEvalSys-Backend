## 此分支的配置是基于Windows系统下
```yml
spring:
  # 数据源配置
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/gameeval?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 30000

  # Redis配置
  redis:
    host: localhost
    port: 6379
    password: redis123456
    database: 0
    timeout: 5000ms
    lettuce:
      client-options:
        protocol-version: RESP2
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        max-wait: -1ms

# MyBatis Plus配置
mybatis-plus:
  type-aliases-package: com.eval.gameeval.models.entity
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: is_deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

logging:
  level:
    com.example.log: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{50} - %msg%n"

# 项目配置
server:
  port: 8080
```
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

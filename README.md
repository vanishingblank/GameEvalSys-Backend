# GameEvalSys Backend

项目评分平台后端服务，提供用户管理、项目管理、评审分组、打分记录、统计导出等 API。

前端仓库：<https://github.com/AleFlyX/GameEvalSys-Frontend>

## 技术栈

- Java 17
- Spring Boot 3.2.5
- Spring Security（基于 Token + Redis）
- MyBatis / MyBatis-Plus
- MariaDB / MySQL
- Redis
- Maven

## 核心能力

- 认证与会话：`/auth/login`、`/auth/logout`
- 用户管理：`/users`
- 项目管理：`/projects`
- 小组管理：`/groups`
- 评审组管理：`/reviewer-groups`
- 打分标准：`/scoring-standards`
- 打分记录与总览：`/scoring/records`、`/scoring/overview`
- 统计与导出：`/projects/{projectId}/statistics`、`/projects/{projectId}/export`、`/statistics/platform`

## 项目结构

```text
.
├─ src/main/java/com/eval/gameeval
│  ├─ controller      # REST API
│  ├─ service         # 业务接口与实现
│  ├─ mapper          # MyBatis Mapper
│  ├─ models          # DTO / VO / Entity
│  ├─ config          # Security、CORS 等配置
│  ├─ interceptor      # 鉴权过滤器、异常入口
│  └─ util            # Redis Key、Token、缓存工具
├─ src/main/resources
│  ├─ application.yml # 本地开发默认配置
│  └─ db              # SQL脚本
└─ deploy             # Docker 与 LXC 部署模板
```

## 快速开始（本地开发）

### 1. 环境要求

- JDK 17+
- Maven 3.9+（或直接使用仓库自带 `mvnw`）
- MariaDB 10.6+ 或 MySQL 8+
- Redis 7+

### 2. 初始化数据库

执行 SQL：

- 推荐：`deploy/shared/mariadb/01-schema.sql`
- 备选：`src/main/resources/db/sql.sql`

该脚本会创建 `gameeval` 库及主要业务表。

### 3. 配置应用

编辑 `src/main/resources/application.yml`：

- `spring.datasource.*`（数据库连接）
- `spring.data.redis.*`（Redis 连接）
- `app.admin.init.*`（首次部署初始化管理员账号）
- `app.cors.allowed-origins`（允许跨域来源）

说明：初始化管理员只会在 `sys_user` 为空时触发。

### 4. 启动服务

```bash
# Windows
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

默认端口：`8080`

## 后端逻辑快速上手
- 阅读[项目逻辑概览](./docs/project-logic-overview.md)

## 鉴权说明

- 登录接口：`POST /auth/login`
- 除 `/auth/login`、`/auth/logout` 外，其余接口默认需鉴权
- 请求头使用：`Authorization: Bearer <token>`
- Token 存储于 Redis，默认过期时间 4 小时（`RedisToken.TOKEN_EXPIRE = 14400s`）

登录请求示例：

```http
POST /auth/login
Content-Type: application/json

{
  "username": "superadmin",
  "password": "admin123"
}
```

## 部署说明

生产部署资源在 `deploy/`：

- `deploy/docker-single/`：单机三容器（backend + mariadb + redis）
- `deploy/lxc-multi-ct/`：LXC 多 CT 拆分部署
- `deploy/shared/`：数据库与 Redis 初始化脚本

推荐先阅读：

- `deploy/README.md`
- `deploy/docker-single/README.md`
- `deploy/lxc-multi-ct/README.md`

## 相关文档

- `API_TEST.md`：接口测试示例（curl / Postman）
- `GROUP_API_REFACTOR.md`：小组接口重构说明
- `REDIS_CACHE_DEBUG.md`：缓存调试记录
- `BUG_FIX_REPORT.md`：问题修复记录
- `REFACTOR_CHANGES.md`：阶段性重构记录

## 开发建议

- 新增接口优先复用统一响应模型 `ResponseVO<T>`
- 涉及权限逻辑时同步检查 `SecurityConfig` 和 `TokenAuthenticationFilter`
- 修改 Redis Key 策略时建议同步更新 `RedisKeyUtil` 与相关缓存工具

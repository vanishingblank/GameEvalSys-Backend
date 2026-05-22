# Spring Boot 后端动态路由落地计划

## 1. 目标

当前前端已经完成“登录后按角色动态注入路由”和“硬刷新预注入路由”的基础能力。下一步需要把“用户可访问哪些页面”的决策收口到后端，由 Spring Boot 统一产出可访问路由或菜单数据，前端只负责渲染与跳转。

本计划的目标是：

- 后端成为权限与路由数据的单一来源
- 支持按角色返回动态路由树或菜单树
- 支持刷新、跨端登录、权限变更后即时生效
- 降低前端静态路由和菜单配置的维护成本

## 2. 推荐实现方案

建议采用“后端返回路由元数据 + 前端本地组件映射”的模式，而不是直接由后端下发组件路径。

## 2.3 基于当前仓库的可行性评估

简要评估基于本仓库（Spring Boot 后端）的落地可行性：

- 当前仓库已有认证与会话基础设施：存在 `AuthController`（含 `/auth/login`、`/auth/logout`、`/auth/refresh`、`/auth/sessions/me`）、`SecurityConfig` 与 `TokenAuthenticationFilter`，以及 `RedisToken` / `AuthSessionStore` 用于 JWT 解析与会话管理，可复用现有的 `tokenVersion` 保持和会话失效逻辑。
- 仓库中未发现菜单/路由的持久化实现（未发现 `sys_menu` 等表或对应实体），因此需要新增菜单数据表、实体、仓库与服务层代码。
- 可行性结论：可行。主要工作量为新增菜单持久层与路由生成服务，以及管理端的菜单维护接口；鉴于现有认证会话设计，权限变更可以通过更新 `tokenVersion` / 会话存储实现即时生效。

实现建议（针对本项目）：
- 新增 `sys_menu` / `sys_role_menu` 等表并编写对应的 SQL 迁移脚本（或在现有迁移工具中添加）；
- 在后端实现 `Menu` 实体、`MenuRepository`、`MenuService` 与 `MenuController`；对外暴露最小接口 `GET /auth/me`（可选，用于返回用户基本信息与角色）、`GET /auth/routes`（返回当前用户路由树）、以及管理端的 `GET/POST/PUT/DELETE /admin/menus`；
- 复用 `RedisToken` / `AuthSessionStore` 中的 `tokenVersion` 与会话黑名单逻辑，确保角色/菜单变更后会话失效或在刷新时生效；
- 前端仍通过本地 `routeMap` 完成 `routeCode -> component` 的映射，后端仅提供白名单级的 `componentCode`/`menuCode` 等元数据。


### 2.1 为什么不直接下发前端组件路径

- 后端直接返回 `@/pages/...` 这类路径会让前后端耦合过紧
- 组件路径一旦重构，后端也要跟着改
- 直接下发完整前端路径不利于做白名单控制

### 2.2 推荐的数据模型

后端返回的不是“页面文件路径”，而是“路由编码/菜单编码”。

前端维护一个稳定的本地映射表：

- `routeCode -> route component`
- `routeCode -> icon/title/default meta`

后端只负责返回用户可见的：

- `routeCode`
- `path`
- `name`
- `parentCode`
- `title`
- `icon`
- `hidden`
- `sort`
- `permissionCodes`

## 3. 后端职责边界

### 3.1 Spring Boot 侧负责

- 用户登录态校验
- 角色与权限校验
- 生成当前用户可访问菜单/路由树
- 管理角色-菜单关系
- 在用户改密、封禁、角色变更后使旧权限失效

### 3.2 前端侧负责

- 接收后端返回的菜单/路由元数据
- 根据本地 routeMap 进行组件挂载
- 登录后注入动态路由
- 刷新时从本地会话恢复并预注入路由

## 4. 数据表设计建议

下面是适合 Spring Boot 落地的最小表结构。

### 4.1 菜单路由表 `sys_menu`

| 字段           | 类型         | 约束             | 说明                                       |
| -------------- | ------------ | ---------------- | ------------------------------------------ |
| id             | bigint       | PK               | 主键                                       |
| parent_id      | bigint       | NULLABLE         | 父级菜单 ID，顶级为 0 或 NULL              |
| menu_code      | varchar(64)  | UNIQUE, NOT NULL | 菜单唯一编码，例如 `home`、`admin-project` |
| menu_type      | varchar(20)  | NOT NULL         | `dir` / `menu` / `button`                  |
| title          | varchar(100) | NOT NULL         | 页面标题或菜单标题                         |
| path           | varchar(255) | NOT NULL         | 路由路径，例如 `/home`、`/admin/project`   |
| route_name     | varchar(64)  | NOT NULL         | Vue Router name，例如 `home`               |
| icon           | varchar(64)  | DEFAULT ''       | 图标标识                                   |
| hidden         | tinyint(1)   | DEFAULT 0        | 是否在侧边栏隐藏                           |
| component_code | varchar(64)  | DEFAULT ''       | 前端本地组件映射编码                       |
| sort_num       | int          | DEFAULT 0        | 排序                                       |
| is_enabled     | tinyint(1)   | DEFAULT 1        | 是否启用                                   |
| create_time    | datetime     | 自动             | 创建时间                                   |
| update_time    | datetime     | 自动             | 更新时间                                   |

### 4.2 角色表 `sys_role`

| 字段       | 类型        | 约束             | 说明                         |
| ---------- | ----------- | ---------------- | ---------------------------- |
| id         | bigint      | PK               | 主键                         |
| role_code  | varchar(32) | UNIQUE, NOT NULL | 角色编码，例如 `super_admin` |
| role_name  | varchar(64) | NOT NULL         | 角色名称                     |
| is_enabled | tinyint(1)  | DEFAULT 1        | 是否启用                     |

### 4.3 用户角色关联表 `sys_user_role`

| 字段    | 类型   | 约束     | 说明    |
| ------- | ------ | -------- | ------- |
| id      | bigint | PK       | 主键    |
| user_id | bigint | NOT NULL | 用户 ID |
| role_id | bigint | NOT NULL | 角色 ID |

### 4.4 角色菜单关联表 `sys_role_menu`

| 字段    | 类型   | 约束     | 说明    |
| ------- | ------ | -------- | ------- |
| id      | bigint | PK       | 主键    |
| role_id | bigint | NOT NULL | 角色 ID |
| menu_id | bigint | NOT NULL | 菜单 ID |

### 4.5 权限表 `sys_permission`（可选）

如果后续需要按钮级、接口级权限，可以再增加权限表，并与菜单解耦：

- `permission_code`
- `permission_name`
- `permission_type`
- `resource`

## 5. API 设计建议

### 5.1 获取当前用户信息

`GET /auth/me`

返回示例：

```json
{
  "id": 1,
  "username": "admin",
  "name": "系统管理员",
  "role": "super_admin",
  "roles": ["super_admin"],
  "permissions": ["menu:home:view", "menu:admin:project:view"]
}
```

### 5.2 获取当前用户菜单树

`GET /auth/routes`

返回示例：

```json
[
  {
    "menuCode": "home",
    "path": "/home",
    "routeName": "home",
    "title": "首页",
    "icon": "HomeFilled",
    "hidden": false,
    "componentCode": "normal-home",
    "children": []
  },
  {
    "menuCode": "admin",
    "path": "/admin",
    "routeName": "adminRoot",
    "title": "管理面板",
    "icon": "Setting",
    "hidden": false,
    "children": [
      {
        "menuCode": "admin-project",
        "path": "/admin/project",
        "routeName": "projectList",
        "title": "项目管理",
        "componentCode": "admin-project-list",
        "hidden": false
      }
    ]
  }
]
```

### 5.3 管理端菜单配置

`GET /admin/menus`

`POST /admin/menus`

`PUT /admin/menus/{id}`

`DELETE /admin/menus/{id}`

用于维护菜单树、排序、显示状态和角色绑定。

## 6. Spring Boot 模块划分建议

### 6.1 包结构

```text
com.example.project
├─ controller
│  ├─ auth
│  ├─ admin
│  └─ menu
├─ service
│  ├─ auth
│  ├─ menu
│  └─ role
├─ repository
├─ domain
│  ├─ entity
│  ├─ dto
│  └─ vo
├─ security
│  ├─ jwt
│  ├─ filter
│  └─ handler
└─ support
   ├─ cache
   └─ tree
```

### 6.2 关键服务

- `MenuService`：根据角色生成菜单树
- `RoleService`：维护角色与菜单关系
- `AuthService`：登录、刷新、会话校验
- `PermissionService`：后续按钮级/接口级权限扩展

## 7. 权限生成流程

### 7.1 登录后

1. 用户登录成功
2. 后端返回 `token`、`sid`、用户信息
3. 前端调用 `GET /auth/routes`
4. 后端根据当前用户角色查询可访问菜单
5. 前端将返回的菜单树映射成本地路由并注入

### 7.2 刷新页面

1. 前端从本地恢复 `token` 与 `userInfo`
2. 进入应用时先调用 `GET /auth/me` 或直接使用本地 `userInfo`
3. 调用 `GET /auth/routes`
4. 预注入路由后再执行首个导航

### 7.3 角色变更

1. 管理员修改用户角色或菜单权限
2. 后端提升 `tokenVersion` 或更新会话版本
3. 旧 token / sid 在刷新时失效
4. 前端重新登录后加载新路由

## 8. 与当前前端的对接方式

当前前端已经具备动态注入能力，后端落地后只需要把数据源从“本地按角色过滤”切换为“后端返回路由树”。

建议的前端适配点：

- `src/router/permission.js`
  - 从本地数组生成，改为接收后端 `/auth/routes` 返回值
- `src/router/index.js`
  - 登录后和刷新时都先拉取路由树，再注入
- `src/layouts/components/side-bar/index.vue`
  - 改为基于注入后的路由或同一份路由树生成菜单

## 9. 安全要求

- 不能只依赖前端隐藏菜单，后端必须对所有敏感接口做权限校验
- 路由树只返回当前用户允许访问的节点，不返回全量菜单再由前端过滤
- `componentCode` 必须使用白名单映射，不能让后端直接控制任意组件加载
- 管理端菜单配置接口必须限制为管理员或超级管理员

## 10. 推荐实施阶段

### 阶段 1：后端输出路由树

- 建 `sys_menu`、`sys_role_menu`、`sys_user_role`
- 实现 `GET /auth/routes`
- 前端按返回树动态注入

### 阶段 2：菜单管理后台

- 新增菜单树维护页面
- 支持增删改查、排序、启用禁用
- 支持角色绑定菜单

### 阶段 3：权限细化

- 增加按钮级权限
- 增加接口级权限
- 增加缓存与失效策略

### 阶段 4：审计与运维

- 记录用户最后一次路由加载结果
- 记录权限变更日志
- 增加异常菜单树诊断接口

## 附：实现 TODO（基于当前仓库）

下面的 TODO 是结合当前项目代码（已有 `AuthController`/`SecurityConfig`/`TokenAuthenticationFilter` 等）给出的优先级与执行建议：

1. 数据库与迁移
  - 新增 `sys_menu`、`sys_role_menu`、`sys_user_role` 的建表 SQL，并在项目的迁移脚本或部署流程中加入（MIGRATION）。

2. 后端模型与仓库
  - 新增 `Menu` 实体、`MenuRepository`（使用 Spring Data JPA 或 MyBatis），并编写基本的 CRUD 方法。

3. 服务层与路由生成
  - 实现 `MenuService`，提供 `List<MenuVO> buildMenuTreeForUser(Long userId)` 方法；该方法应基于用户角色查询 `sys_role_menu` 并返回树形结构。

4. 控制器与 API
  - 新增 `GET /auth/routes`：返回当前用户可访问的路由树（无须返回前端组件路径，只返回 `menuCode/componentCode/path/title/permissionCodes` 等元数据）。
  - 新增管理 API：`/admin/menus` 的 `GET/POST/PUT/DELETE`，并在 `SecurityConfig` 中仅允许 `ROLE_admin`/`ROLE_super_admin` 访问。
  - （可选）新增 `GET /auth/me` 返回当前用户基本信息与角色数组，便于前端在启动时恢复状态。

5. 会话失效与一致性
  - 复用现有 `AuthSessionStore` / `tokenVersion` 机制：在角色或菜单权限变更时更新 `tokenVersion` 或删除相关会话，以保证旧 token 在刷新时失效。

6. 前端对接文档
  - 在仓库 `docs/` 中补充前端对接说明，示例：如何将后端返回的 `menuCode/componentCode` 映射到本地 `routeMap`。

7. 测试与验收
  - 为 `MenuService` 和 `MenuController` 编写单元测试与集成测试，覆盖权限过滤与路由树生成逻辑。

优先级建议：先做 1-4 达成最小闭环（MVP），再推进 5-7。


## 11. 验收标准

- 登录后，前端只展示当前角色允许访问的菜单
- 刷新 `/home`、`/admin/project` 等受保护页面不再跳 404
- 角色变更后，旧会话在重新加载路由时自动失效
- 后端菜单配置变更后，前端无需发版即可生效

## 12. 落地建议

如果当前希望尽快上线，建议先做最小闭环：

1. 后端先实现 `GET /auth/routes`
2. 路由树只支持一级/二级菜单
3. 前端继续保留本地 routeMap
4. 菜单管理页后置到第二阶段

这样可以先把“谁能看到哪些页面”统一收口，再逐步演进到完整的菜单权限体系。

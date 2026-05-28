# 缓存策略清单 v1（接口级）

## 1. 目标

- 统一缓存分层：全局热键采用主动预热 + 定时刷新，个性化高基数键采用被动缓存。
- 保证状态一致性：项目状态变化时，授权项目、打分概览、平台统计缓存形成失效闭环。
- 控制成本：避免对高基数接口做全量预热。
- 菜单路由缓存采用“按角色缓存 + 全局版本号失效”，兼顾命中率和配置实时性。

## 2. 缓存分层策略表

| 分层 | 典型数据 | 键基数 | 读取频率 | 推荐策略 | 预热 | 定时刷新 | 失效方式 |
|---|---|---:|---:|---|---|---|---|
| L1 全局热键 | 平台统计、标准列表、后台首页项目首屏列表 | 低 | 高 | 主动预热 + 周期刷新 | 是 | 是 | 写操作事件驱动清理 |
| L2 业务聚合键 | 项目详情、项目小组、项目统计 | 中 | 中-高 | 被动缓存 + 定向预热热门对象 | 选择性 | 低频可选 | 关联实体变更时定向清理 |
| L3 个性化键 | 授权项目、用户概览、用户项目打分页记录 | 高 | 高 | 被动缓存为主（登录后首屏可异步预热） | 否（仅首屏可选） | 否 | 用户/项目事件驱动清理 |
| L4 防穿透键 | 空值缓存（not found） | 中 | 中 | 被动缓存 | 否 | 否 | 正向写入时删除 |

## 3. 接口级缓存策略清单 v1

说明：
- 预热：是否建议在系统启动、发布后或登录后主动构建缓存。
- 刷新周期：主动刷新建议，不是 TTL。
- TTL 建议：可配置目标值（秒）。

| 接口 | 缓存键 | 是否预热 | 刷新周期 | 失效触发器 | TTL 建议 |
|---|---|---|---|---|---|
| GET /statistics/platform | statistics:platform:overview | 是 | 5 分钟 | 项目创建/编辑/结束、项目状态兜底同步、打分写入后可选 | 300 |
| GET /scoring-standards | scoring:standard:list | 是 | 10 分钟 | 创建/编辑打分标准 | 1800 |
| GET /scoring-standards/{id} | scoring:standard:{id} | 选择性（热门标准） | 无（按 TTL） | 编辑该标准 | 3600 |
| GET /projects?page=1&size=10&默认筛选 | project:list:* | 是（仅首页组合） | 1-5 分钟 | 项目创建/编辑/结束、项目状态兜底同步 | 300 |
| GET /projects（其他分页/筛选） | project:list:* | 否 | 无 | 同上 | 300 |
| GET /projects/{projectId} | project:detail:{id} | 否（可对热点项目预热） | 无 | 项目编辑/结束、项目状态兜底同步 | 300-600（建议由 3600 下调） |
| GET /projects/authorized | project:authorized:{userId}:* | 否（仅登录后首屏可选） | 无 | 项目创建/编辑/结束、项目状态兜底同步、项目-用户授权变更 | 600 |
| GET /scoring/overview | scoring:overview:user:{userId} | 否 | 无 | 打分提交/修改、项目状态兜底同步、授权变更 | 120 |
| GET /projects/{projectId}/groups | project:groups:{projectId} | 否 | 无 | 小组关联到项目、编辑小组、项目编辑（groupIds 变更） | 1800 |
| GET /projects/{projectId}/records | scoring:record:page:{projectId}:{userId}:* | 否 | 无 | 打分提交/修改 | 300 |
| GET /projects/{projectId}/statistics | （当前无）建议新增 project:statistics:{projectId} | 否（热点可选） | 2-5 分钟（可选） | 打分提交/修改 | 60-180 |
| GET /projects/{projectId}/statistics/groups/{groupId} | （当前无）建议新增 project:statistics:{projectId}:group:{groupId} | 否 | 2-5 分钟（可选） | 打分提交/修改 | 60-180 |
| GET /auth/routes | auth:routes:role:{roleCode}:v{menuVersion} | 是（登录后首屏可选） | 无（按版本号失效） | 菜单新增/编辑/删除、角色菜单绑定变化 | 3600 |

## 4. 项目状态变化链路（闭环规则）

统一状态口径：
- now < startDate => not_started
- now > endDate => ended
- 其他 => ongoing

状态变化触发的缓存失效闭环（必须同时覆盖）：
1. project:list:*（项目列表）
2. project:detail:{projectId}（项目详情）
3. project:authorized:{userId}:*（授权项目）
4. scoring:overview:user:{userId}（用户概览）
5. statistics:platform:overview（平台统计）

## 4.1 菜单路由缓存闭环

菜单管理接口变更时，采用版本号失效而不是批量删 key：

1. 菜单新增、更新、删除，或 `sys_role_menu` 绑定发生变化时，递增 `auth:menu:version`。
2. `/auth/routes` 读取时先获取当前版本号，再按 `auth:routes:role:{roleCode}:v{menuVersion}` 查缓存。
3. 版本号变化后，旧缓存 key 自动失去访问入口，不需要扫描 Redis。

触发入口：
- `POST /admin/menus`
- `PUT /admin/menus/{id}`
- `DELETE /admin/menus/{id}`
- 菜单角色绑定变更接口或脚本

触发入口（v1 已落地）：
- 项目写操作：createProject、updateProject、endProject。
- 项目读兜底：getProjectList、getProjectDetail、getAuthorizedProjects 在进入主流程前执行状态纠偏；若发现状态修正，立即触发上述 1~5 失效。

## 5. 预热执行建议（v1）

系统启动/发布后预热：
1. statistics:platform:overview
2. scoring:standard:list
3. project:list 的后台首页默认组合（page=1,size=10, status=all, isEnabled=true, keyWords=空）

用户登录后异步预热（仅当前用户）：
1. project:authorized:{userId}:1:10（默认筛选）
2. scoring:overview:user:{userId}
3. auth:routes:role:{roleCode}:v{menuVersion}（可选，仅对高频后台角色预热）

## 6. 观测指标（建议接入）

- 命中率：按接口统计 hit/miss。
- 回源耗时：DB 查询平均耗时及 P95。
- 失效事件计数：按触发源分类（项目写、状态纠偏、打分写入）。
- 失效事件计数：按触发源分类（项目写、状态纠偏、打分写入、菜单管理变更）。
- 热键监控：Top N key 与 TTL 分布。
- 状态纠偏统计：每次纠偏的 project 数量与影响 user 数量。
- 菜单路由版本号：监控 `auth:menu:version` 是否持续增长，避免写接口未触发版本递增。

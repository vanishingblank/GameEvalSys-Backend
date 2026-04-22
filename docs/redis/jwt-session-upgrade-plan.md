# JWT + Redis 会话中心升级方案（支持在线管理与强制下线）

## 1. 背景与目标

当前项目登录后返回的是 UUID token（依赖 Redis 做 token -> userId 映射）。该方案可以满足基础登录态校验，但在以下方面存在不足：

- token 本身不包含声明信息，跨系统传递与审计能力较弱
- 缺少标准化的签名与过期声明
- 在线管理能力有限，难以精细化做“多端在线 + 踢指定设备”

本次升级目标：

- 将 access token 从 UUID 升级为 JWT
- 保留 Redis 作为会话中心，以支持在线态与强制下线
- 支持 **多端在线**（同一用户可多个会话同时存在）
- 支持 **按会话（sid）强制下线**（可踢单端）
- 保持改造可灰度、可回滚，优先最小侵入

---

## 2. 总体方案

采用“**JWT（身份声明） + Redis（会话真值）**”双层校验模式，而不是纯 JWT。

- **Access Token（JWT，短期）**：用于每次请求认证，携带 `userId/role/sid/jti/exp` 等声明
- **Refresh Token（长效，存 Redis）**：用于换发新的 access token
- **Session Registry（Redis）**：维护在线会话（sid 维度），支持会话查询、踢下线
- **Access 黑名单（Redis，按 jti）**：解决 access token 未过期但被主动失效的问题

这样可以同时满足：

- JWT 的标准化与可扩展性
- 在线管理和强制下线的可控性
- 兼顾安全性与服务端可撤销能力

---

## 3. Token 与会话模型设计

## 3.1 Access JWT 载荷建议

建议 claims：

- `sub`：用户 ID（字符串）
- `username`：用户名
- `role`：用户角色
- `sid`：会话 ID（一次登录一个 sid）
- `jti`：JWT ID（唯一，用于黑名单）
- `type`：`access`
- `iat`：签发时间
- `exp`：过期时间（建议 15~30 分钟）
- `iss`：签发方（可选）

## 3.2 Refresh Token 建议

- 可使用随机高强度字符串（或 JWT refresh）
- 服务端 Redis 保存 refresh 元数据并绑定 `sid`
- 有效期建议 7~30 天（按业务风险调整）

## 3.3 Redis Key 设计（核心）

- `auth:session:{sid}`
  - 值：`userId、username、role、device、ip、loginAt、lastActiveAt、status`
  - TTL：与 refresh 生命周期一致

- `auth:user:sessions:{userId}`
  - 值：该用户当前所有 `sid` 集合（Set）

- `auth:refresh:{sid}`
  - 值：refresh token 摘要或 refresh 映射信息
  - TTL：refresh 有效期

- `auth:blacklist:access:{jti}`
  - 值：1
  - TTL：`access_token_expire - now`

---

## 4. 请求认证链路（过滤器）

每次请求进入 `TokenAuthenticationFilter`：

1. 从 `Authorization: Bearer <accessToken>` 提取 JWT
2. 校验 JWT 签名、`exp`、`type=access`
3. 读取 `sid/jti/sub`
4. 校验 `jti` 不在黑名单
5. 校验 `auth:session:{sid}` 存在且状态有效
6. 校验 session 的 `userId` 与 `sub` 一致
7. 通过后写入 `SecurityContext`

> 关键点：**JWT 通过并不代表最终有效**，必须同时通过 Redis 会话校验。

---

## 5. 登录、刷新、登出、踢下线流程

## 5.1 登录 `/auth/login`

- 用户名密码校验通过后：
  1. 生成 `sid`
  2. 生成 access JWT（含 sid/jti）
  3. 生成 refresh token
  4. 写入 `auth:session:{sid}`、`auth:user:sessions:{userId}`、`auth:refresh:{sid}`
  5. 返回 `accessToken + refreshToken + expireTime + sid`

## 5.2 刷新 `/auth/refresh`

- 校验 refresh token 与 `sid` 关系
- 通过后签发新 access JWT（新 jti）
- 可选：refresh 轮换（rotate）
- 若 refresh 异常，删除 session 并要求重新登录

## 5.3 主动登出 `/auth/logout`

- 从当前 access token 读取 `sid/jti`
- 将当前 `jti` 放入黑名单（TTL = access 剩余生命周期）
- 删除 `auth:session:{sid}` 与 `auth:refresh:{sid}`
- 从 `auth:user:sessions:{userId}` 移除 sid

## 5.4 强制下线（管理员）

- **踢单端**：按 `sid` 删除会话 + 拉黑当前 access jti（如可获取）
- **踢全端**：遍历 `auth:user:sessions:{userId}` 下所有 sid，逐个删除

---

## 6. 接口建议

用户侧：

- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /auth/sessions/me`（查看自己的在线会话）

管理侧（需管理员权限）：

- `GET /admin/sessions?userId=...`（查看指定用户在线会话）
- `POST /admin/sessions/{sid}/kick`（踢指定会话）
- `POST /admin/users/{userId}/kick-all`（踢该用户全部会话）

---

## 7. 基于当前项目的改造建议（分阶段）

## 阶段一：兼容改造（低风险，先跑通）

目标：不大改现有 Controller/Service 传参习惯，先完成 JWT 化与会话中心。

- 新增 JWT 工具类（签发/解析/校验）
- `AuthServiceImpl` 登录逻辑改为“签发 access + refresh + sid”
- `TokenAuthenticationFilter` 增加 JWT 解析 + Redis session 联合校验
- `SecurityConfig` 增加 `SessionCreationPolicy.STATELESS`
- 保留现有“从请求头取 token”的兼容逻辑，减少一次性改动范围

## 阶段二：标准化改造（体验与维护优化）

目标：逐步改为统一从 `SecurityContext` 获取当前用户，收敛重复 token 解析。

- 业务层统一通过认证上下文获取 userId
- 补齐会话管理接口与管理端权限控制
- 增加审计日志（登录、刷新、登出、踢下线）

---

## 8. 安全建议

- access token 短有效期（15~30 分钟）
- refresh token 存储摘要（避免明文）
- JWT 密钥使用环境变量并定期轮换
- 限制 refresh 重放（启用 rotate + 旧 refresh 立即失效）
- 关键接口增加风控：限流、失败次数控制、异常告警
- 管理员踢下线动作记录审计日志

---

## 9. 与现状兼容与风险说明

兼容策略：

- 过渡期允许旧 token 与新 token 并行（可选，取决于上线窗口）
- 新老逻辑通过配置开关控制，便于灰度发布

风险点：

- Redis key 迁移与 TTL 策略需统一，避免“幽灵在线”
- 多实例部署时要保证时钟同步与密钥一致
- 踢下线后短时间内可能存在并发请求窗口（通过 jti 黑名单可缓解）

---

## 10. 验收清单

- 登录成功返回 access/refresh/sid
- access 过期后 refresh 可换发新 access
- 单设备登出后该 sid 立即失效
- 管理员踢指定 sid 后该端请求立即 401
- 用户多端在线互不影响，踢单端不影响其他端
- 踢全端后该用户所有 sid 均失效

---

## 11. 实施优先级建议

1. 先完成登录、过滤器、refresh、logout 主链路
2. 再补会话查询与管理员踢下线接口
3. 最后做审计、风控、灰度开关与监控告警

该顺序可在最短时间内先交付“可用且安全”的认证升级，再迭代完善运维与治理能力。

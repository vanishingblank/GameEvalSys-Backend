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

传输与使用约定：

- 前端通过 `Authorization: Bearer <accessToken>` 传递，不使用 Cookie
- 当前仅单系统认证，无跨系统 `aud` 兼容需求

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
- `exp`：过期时间（实际配置：4 小时）
- `iss`：签发方（单系统固定值，建议强校验）

## 3.2 Refresh Token 建议

- 可使用随机高强度字符串（或 JWT refresh）
- 服务端 Redis 保存 refresh 元数据并绑定 `sid`
- 有效期建议 7~30 天（按业务风险调整）
- 实际配置：7 天
- 建议存储 refresh 的摘要（如 `SHA-256(refreshToken)`），避免明文落库
- 建议启用 refresh 复用检测：旧 refresh 被再次使用即判定为泄漏并踢下线

## 3.3 Redis Key 设计（核心）

- `auth:session:{sid}`

  - 值：`userId、username、role、device、ip、loginAt、lastActiveAt、status、loginLocation、accessJti、accessExp`
  - TTL：与 refresh 生命周期一致

- `auth:user:sessions:{userId}`
  - 值：该用户当前所有 `sid` 集合（Set）

- `auth:refresh:{sid}`
  - 值：refresh token 摘要 + `tokenId` + 绑定信息（device/ip/ua）
  - TTL：refresh 有效期

- `auth:blacklist:access:{jti}`
  - 值：1
  - TTL：`access_token_expire - now`

扩展键（可选，支撑更大规模与强一致控制）：

- `auth:user:tokenVersion:{userId}`
  - 值：整数版本号
  - 用途：密码修改/角色变更/封禁时递增，强制旧 token 失效
  - TTL：可不设（持久）

---

## 4. 请求认证链路（过滤器）

每次请求进入 `TokenAuthenticationFilter`：

1. 从 `Authorization: Bearer <accessToken>` 提取 JWT
2. 校验 JWT 签名、`exp`、`type=access`
  - 明确算法白名单，仅允许指定算法
  - 校验 `iss` 固定值，拒绝其他签发方
3. 读取 `sid/jti/sub`
4. 校验 `jti` 不在黑名单
5. 校验 `auth:session:{sid}` 存在且状态有效
6. 校验 session 的 `userId` 与 `sub` 一致
7. 如启用 `tokenVersion`，校验 token 版本与 `auth:user:tokenVersion:{userId}` 一致
8. 通过后写入 `SecurityContext`

> 关键点：**JWT 通过并不代表最终有效**，必须同时通过 Redis 会话校验。

---

## 5. 登录、刷新、登出、踢下线流程

## 5.1 登录 `/auth/login`

- 用户名密码校验通过后：
  1. 生成 `sid`
  2. 生成 access JWT（含 sid/jti）
  3. 生成 refresh token
  4. 在 Controller 层采集 `ip、device、loginLocation`，写入 `auth:session:{sid}`
  5. 写入 `auth:session:{sid}`、`auth:user:sessions:{userId}`、`auth:refresh:{sid}`
  5. 返回 `accessToken + refreshToken + expireTime + sid`

## 5.2 刷新 `/auth/refresh`

- 校验 refresh token 与 `sid` 关系 + 绑定信息（device/ip/ua）
- 通过后签发新 access JWT（新 jti）
- 建议：refresh 轮换（rotate）并记录 `tokenId`
- 若 refresh 复用或异常，删除 session 并要求重新登录

## 5.3 主动登出 `/auth/logout`

- 从当前 access token 读取 `sid/jti`
- 将当前 `jti` 放入黑名单（TTL = access 剩余生命周期）
- 删除 `auth:session:{sid}` 与 `auth:refresh:{sid}`
- 从 `auth:user:sessions:{userId}` 移除 sid

## 5.4 强制下线（管理员）

- **踢单端**：按 `sid` 删除会话 + 拉黑当前 access jti（如可获取）
- **踢全端**：遍历 `auth:user:sessions:{userId}` 下所有 sid，逐个删除
- **密码修改/角色变更/封禁**：递增 `auth:user:tokenVersion:{userId}` 并清理会话

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
- 日志与监控脱敏：严禁输出完整 token

---

## 9. 与现状兼容与风险说明

兼容策略：

- 过渡期允许旧 token 与新 token 并行（可选，取决于上线窗口）
- 新老逻辑通过配置开关控制，便于灰度发布

风险点：

- Redis key 迁移与 TTL 策略需统一，避免“幽灵在线”
- 多实例部署时要保证时钟同步与密钥一致
- 踢下线后短时间内可能存在并发请求窗口（通过 jti 黑名单可缓解）
- 大并发下黑名单与会话读写压力上升，需评估 Redis QPS 与内存

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

---

## 12. 扩展与规模化建议

- 会话规模：当前约 150 活跃用户，按 10x~100x 规模设计 Redis 容量与 QPS
- `auth:session:{sid}` 可拆成 Hash，降低字段更新成本
- `lastActiveAt` 建议采用滑动窗口更新（例如每 5~10 分钟一次），避免高频写
- 黑名单仅用于“主动失效”场景，常规访问以 `auth:session:{sid}` 为准
- 设定单用户会话上限（如 5~10），超限踢最旧会话


### 12.1 Redis 容量估算公式

定义：

- `U`: 活跃用户数（预计 10,000）
- `S`: 人均在线会话数（例如 1.2）
- `K_s`: 单个 session 记录平均大小（字节），含 key + value + 结构开销
- `K_r`: 单个 refresh 记录平均大小（字节）
- `K_j`: 单个 jti 黑名单平均大小（字节）
- `J`: 平均每会话产生的黑名单条目数（通常 0~1）
- `K_index`: 单个用户会话索引平均大小（字节）
- `M`: Redis 内存冗余系数（建议 1.5~2）

估算：

- 会话数 `N = U * S`
- Session 内存 `Mem_session = N * K_s`
- Refresh 内存 `Mem_refresh = N * K_r`
- 黑名单内存 `Mem_black = N * J * K_j`
- 额外结构（用户会话 Set 等）`Mem_index = U * K_index`

总内存：

$$
Mem_{total} = (Mem_{session} + Mem_{refresh} + Mem_{black} + Mem_{index}) * M
$$

参考取值（仅做量级估算，需压测校准）：

- `K_s` 0.6~1.2 KB（Hash 8~12 个字段）
- `K_r` 0.3~0.8 KB
- `K_j` 0.1~0.3 KB

示例（10,000 活跃用户，`S=1.2`，`J=0.2`，`M=1.8`）：

- `N = 12,000`
- `Mem_session` ~ 7.2~14.4 MB
- `Mem_refresh` ~ 3.6~9.6 MB
- `Mem_black` ~ 0.2~0.7 MB
- `Mem_index` ~ 1~3 MB
- `Mem_total` 约 25~50 MB（实际按 2x 预留更稳妥）

### 12.2 QPS 预估表（10,000 活跃用户）

假设：

- 高峰并发请求中，约 80% 为带 token 的业务请求
- 每次请求会读取 session，黑名单查询可选（仅在主动失效时或开关打开）
- `lastActiveAt` 采用 5~10 分钟滑动更新

| 场景 | 假设 | Redis 读 QPS | Redis 写 QPS |
| --- | --- | --- | --- |
| 业务请求认证 | 500 RPS | 500 (session) + 0~500 (blacklist) | 0~10 (lastActiveAt 滑动更新) |
| 登录/刷新/登出 | 50 RPS | 50~150 | 100~250 |
| 高峰合计 | 550 RPS | 600~1,200 | 100~260 |

注：以上为估算范围，最终以压测结果为准。建议对 Redis 读写分离或集群扩容预留 2x 余量。


---

## 13. 2026/5/5 登录信息详细字段升级（Controller 层采集 + 离线库）

### 13.1 目标与字段范围

- 目标：让会话接口返回更详细的登录信息（IP、设备、IP 所在地）。
- 新增字段（Session 维度）：
  - `ip`：登录 IP
  - `device`：登录设备（由 User-Agent 解析得到的简要描述）
  - `loginLocation`：登录 IP 所在地（离线库解析）

### 13.2 数据采集位置（Controller 层）

在 `AuthController` 的登录接口里采集，避免 Service 层依赖 HTTP 请求对象。

- IP 取值优先级：
  1) `X-Forwarded-For`（取第一个非空 IP）
  2) `X-Real-IP`
  3) `request.getRemoteAddr()`
- 设备信息：
  - 读取 `User-Agent` 头
  - 解析后取简要描述（例如 `Windows/Chrome`, `iOS/Safari`）

### 13.3 IP 所在地离线库

- 推荐选型：
  - MaxMind GeoLite2（建议城市库）
  - ip2region（速度快，数据体积小）
- 解析策略：
  - 登录时解析一次，结果写入 `loginLocation`
  - 以城市级别为主（例如 `CN/Guangdong/Shenzhen`）
- 缓存策略：
  - 可按 IP 缓存解析结果，降低重复查询成本

### 13.4 Redis 会话字段写入

- 写入位置：`auth:session:{sid}`
- 字段示例：
  - `ip`: `203.0.113.5`
  - `device`: `Windows/Chrome`
  - `loginLocation`: `CN/Guangdong/Shenzhen`
  - `accessJti`: `7d2f7b72a6d7467e8b1d2e9b46b9e1a1`
  - `accessExp`: `1772890123`

### 13.5 响应字段扩展

- `/auth/sessions/me` 与 `/admin/sessions`：
  - 在 `SessionInfoVO` 中增加 `ip、device、loginLocation`
- `/admin/online-users`：
  - 如需要展示最近登录设备/位置，可在 `OnlineUserVO` 中增加同名字段
  - 默认建议展示最近一次登录会话的 `device/loginLocation`

### 13.6 强制下线策略（保持一致）

- 依旧以 Redis session 为真值：
  - 删除 `auth:session:{sid}` + `auth:refresh:{sid}` 为核心动作
  - 如需更强即时性，可补充拉黑当前 `jti`
- 踢全端：
  - 清除所有 `sid` 后可选 `tokenVersion` 递增，防止残留 token

### 13.7 验收清单（本次改造）

- 登录后会话里包含 `ip/device/loginLocation`
- `/auth/sessions/me` 返回新增字段
- `/admin/online-users` 可展示最近登录设备与所在地
- 踢指定会话后，该会话立即不可用

---

## 14. JTI 黑名单落地实施

### 14.1 目标

- 主动失效 access token（登出/踢下线）时立即生效。
- 避免并发窗口导致“被踢后仍可用”的短暂情况。

### 14.2 Redis Key 与 TTL

- Key：`auth:blacklist:access:{jti}`
- Value：`1`
- TTL：`access_expire_at - now`

会话字段说明：

- `accessJti`：当前 access token 的 `jti`，用于踢下线时快速拉黑。
- `accessExp`：当前 access token 的过期时间（秒级时间戳），用于计算黑名单 TTL。

### 14.3 认证链路校验

在 `TokenAuthenticationFilter` 中增加校验：

1. 解析 JWT，取 `jti`、`exp`。
2. 查询 `auth:blacklist:access:{jti}` 是否存在。
3. 若存在直接拒绝（401）。

### 14.4 何时写入黑名单

- `/auth/logout`：当前 `jti` 入黑名单。
- `/admin/sessions/{sid}/kick`：若能从被踢会话的 token 获取 `jti`，则入黑名单。
- 异常 refresh（复用/泄漏）：删除 session + refresh，并将当前 `jti` 入黑名单。

### 14.5 TTL 计算建议

- 从 JWT 中解析 `exp`（秒级时间戳）。
- `ttlSeconds = exp - now`，若 `ttlSeconds <= 0` 则不写入。

### 14.6 兼容与注意事项

- 黑名单只用于主动失效场景，常规请求仍以 `auth:session:{sid}` 为真值。
- access 过期后黑名单会自然过期，避免长期膨胀。
- 如存在多实例，请确保 Redis 是共享的。

### 14.7 验收清单（黑名单）

- 登出后同一 access token 立即失效
- 踢下线后 token 立即 401
- 黑名单条目在 access 过期后自动清理

---

## 15. accessJti/accessExp 代码改造清单

### 15.1 写入（登录/刷新）

- 位置：`AuthServiceImpl.login()`
  - 生成 `jti` 后计算 `accessExpEpochSeconds`。
  - 在 `saveSession` 之后调用 `authSessionStore.updateAccessInfo(sid, jti, accessExpEpochSeconds)`。
- 位置：`AuthServiceImpl.refresh()`
  - 生成新 `jti` 后计算 `accessExpEpochSeconds`。
  - 调用 `authSessionStore.updateAccessInfo(sid, jti, accessExpEpochSeconds)`。

### 15.2 读取（踢下线）

- 位置：`AuthServiceImpl.kickSession()`
  - 调用 `authSessionStore.getSession(sid)` 获取 `accessJti/accessExp`。
  - 计算 `ttlSeconds = accessExp - now` 并调用 `authSessionStore.blacklistAccess(jti, ttlSeconds)`。
- 位置：`AuthServiceImpl.kickAllSessions()`
  - 遍历 `sid` 时同样读取 `accessJti/accessExp` 并写入黑名单。

### 15.3 存取位置与字段

- Redis Hash：`auth:session:{sid}`
  - `accessJti`：当前 access token 的 `jti`。
  - `accessExp`：当前 access token 的过期时间（秒级时间戳）。

### 15.4 辅助方法

- `AuthSessionStore.updateAccessInfo(String sid, String accessJti, long accessExpEpochSeconds)`
  - 写入 `accessJti/accessExp` 到 `auth:session:{sid}`。
- `AuthServiceImpl` 内部辅助：
  - 计算 `accessExpEpochSeconds`（`now + jwt.accessSeconds`）。
  - 统一的黑名单写入方法（读取 session -> 计算 TTL -> `blacklistAccess`）。

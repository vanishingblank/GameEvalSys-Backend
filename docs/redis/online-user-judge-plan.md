# 在线用户判定逻辑优化方案

## 1. 背景

当前项目的“在线用户”列表，主要依赖 Redis 中是否还存在会话记录来判断。现有实现里：

- 登录时会创建 `auth:session:{sid}` 和 `auth:user:sessions:{userId}`
- refresh token 的 TTL 与 session TTL 保持一致，默认都是 `jwt.refresh-seconds`
- 管理端“在线用户”列表通过 `authSessionStore.getOnlineUserIds()` 获取在线用户 ID
- `lastActiveAt` 只是记录最近活跃时间，并没有参与在线判断

这会导致一个问题：

- 只要 refresh token 还没过期，session 记录还在，用户就会被继续标记为在线
- 即使 access token 已过期、用户长时间没有操作，页面仍可能显示在线

因此，当前的“在线用户”更接近“仍保留登录会话的用户”，而不是“当前活跃用户”。

---

## 2. 目标

把“在线”拆成两个清晰口径，避免语义混淆：

1. **登录会话有效**：用户仍持有未失效的登录态，会话记录存在于 Redis
2. **当前活跃在线**：用户在最近一段时间内确实有请求或心跳行为

管理端列表应优先展示“当前活跃在线”，并保留“登录会话有效”的辅助能力，方便运维与审计。

---

## 3. 现状问题

### 3.1 在线判断过于依赖 refresh 生命周期

当前 session 的 TTL 是由 `jwt.refresh-seconds` 控制的：

- `saveSession()` 会设置 `auth:session:{sid}` 的 TTL
- `saveRefresh()` 也会设置 `auth:refresh:{sid}` 的 TTL
- `refreshSessionTtl()` 会在刷新时继续延长 session TTL

结果是，只要 refresh 还在，会话通常就还在，用户会一直被统计为在线。

### 3.2 `lastActiveAt` 没有作为在线门槛

代码里虽然会在请求经过时更新 `lastActiveAt`，但当前在线列表和会话统计只是读取该字段用于展示，并没有用它来筛掉“长时间未活跃”的会话。

### 3.3 管理端展示容易误导

`/admin/online-users` 目前更像是“在线会话用户列表”，如果前端文案仍显示“在线用户”，就会给出错误预期。

---

## 4. 建议方案

## 4.1 在线判定改成双层口径

建议统一采用下面的定义：

- **登录会话有效**：Redis 中 `auth:session:{sid}` 存在，且未过期
- **当前活跃在线**：session 存在，且 `lastActiveAt` 距今不超过一个活跃窗口

活跃窗口建议独立于 refresh 生命周期配置，例如：

- `jwt.online-active-window-seconds` = 300 或 600

这样可以把“登录有效期”和“活跃在线窗口”解耦。

## 4.2 管理端列表优先展示活跃在线

`/admin/online-users` 建议只返回满足以下条件的用户：

- session 存在
- `lastActiveAt` 在活跃窗口内
- session 没有被明确踢下线或 tokenVersion 作废

如果希望保留“已登录但当前不活跃”的视图，可以新增一个查询开关：

- `onlineOnly=true`：只看当前活跃在线
- `onlineOnly=false`：看所有登录会话有效用户

或者更直接一点：把接口拆成两个标签页。

## 4.3 `lastActiveAt` 只做活跃判定，不做 TTL 续命

建议保持以下原则：

- `lastActiveAt` 只表示最近一次活跃时间
- 只在访问链路里按节流策略更新，例如 5 分钟一次
- 不要因为更新 `lastActiveAt` 就无限延长 session TTL

这样可避免用户长期挂机但 session 一直被续命，造成在线状态失真。

## 4.4 session TTL 与 refresh TTL 分离

建议把两者职责拆开：

- `auth:session:{sid}`：表示登录会话存在，TTL 可以略长于 access token，但不应无限延长
- `auth:refresh:{sid}`：表示 refresh 轮换能力，TTL 由 refresh 生命周期决定

如果业务希望“登录会话有效”也有固定上限，可以增加一个最大会话生存时间，例如 24 小时或 7 天，而不是完全跟 refresh 绑定。

---

## 5. 推荐实现方式

## 5.1 增加在线窗口配置

建议新增配置项：

```yaml
jwt:
  online-active-window-seconds: 300
```

含义：

- 最近 5 分钟内有请求，才算在线

## 5.2 修改 `AuthSessionStore`

建议新增一个判断方法，而不是直接复用 `getOnlineUserIds()`：

- `getActiveOnlineUserIds()`：返回最近活跃窗口内的用户 ID
- `getLoggedInUserIds()`：返回仍存在 session 的用户 ID

这样管理端可以按需选择口径。

## 5.3 修改管理端查询逻辑

`AuthServiceImpl.getOnlineUsers()` 建议：

1. 先取在线口径集合
2. 再按角色、关键字、启用状态过滤
3. 再分页
4. 列表中展示 `onlineCount / lastActiveAt / lastLoginAt`

其中：

- `onlineCount` 表示活跃 session 数
- `lastActiveAt` 表示最近活跃时间
- `lastLoginAt` 表示最近登录时间

## 5.4 下线动作要直接影响在线状态

踢下线、登出、封禁时，应立即：

- 删除 `auth:session:{sid}`
- 删除 `auth:refresh:{sid}`
- 必要时把 access jti 加入黑名单
- 从 `auth:user:sessions:{userId}` 中移除 sid

这样在线列表会立刻更新，而不是等待 refresh 过期。

---

## 6. 对当前代码的落地建议

### 6.1 现在就应该修的点

- 不要把“session 存在”直接等同于“活跃在线”
- 在线用户列表应增加 `lastActiveAt` 过滤
- 前端文案建议改成“在线会话”或“活跃用户”，二选一，避免歧义

### 6.2 可以后续再做的点

- 新增“已登录但不活跃”标签页
- 增加心跳或更精细的活跃窗口
- 增加会话刷新策略和审计日志

---

## 7. 建议修改清单

### 后端

- `AuthSessionStore`
  - 新增活跃在线判定方法
  - 增加在线窗口配置读取
- `AuthServiceImpl`
  - 管理端在线用户查询改为按活跃窗口过滤
  - 会话摘要保留 `lastActiveAt` 和 `lastLoginAt`
- `application.yml`
  - 新增 `jwt.online-active-window-seconds`

### 前端

- 管理端在线用户页面文案调整
- 如果保留两个口径，增加“活跃在线 / 登录会话”切换

---

## 8. 验收标准

- 用户最近 5 分钟内有请求时，能被统计为在线
- 用户长时间无操作，即使 refresh token 还没过期，也不会继续显示为“活跃在线”
- 用户登出或被踢下线后，列表立即消失
- 管理端能够区分“活跃在线”和“仍登录但不活跃”

---

## 9. 结论

如果项目里的“在线用户”是给管理端看实时状态的，当前实现确实不合理，因为它更接近“登录态仍存活”。

更合理的做法是把“在线”定义为“最近一段时间内有请求活动”，并把 refresh 生命周期和在线判定拆开。这样页面才不会因为长效 refresh token 而持续显示不准确的在线状态。
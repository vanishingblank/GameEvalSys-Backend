# Redis 缓存调试指南

## 问题症状
添加小组后，前端无法立即看到新添加的小组，需要等待或刷新。

## 根本原因排查

已添加详细日志到以下文件，编译后运行可以看到完整的 Redis 操作日志：

### 1. RedisBaseUtil.java - Redis 基础操作层
现在会输出以下日志类型：

#### 设置缓存 (set)
```
【Redis设置成功】key=project:groups:100, expireSeconds=1800
【Redis设置失败】key=project:groups:100, 异常信息: Connection refused
【Redis错误】key或value为null
```

#### 获取缓存 (get)
```
【Redis获取成功】key=project:groups:100, value类型=ArrayList
【Redis获取】key=project:groups:100 不存在
【Redis错误】获取时key为null
```

#### 删除缓存 (delete)
```
【Redis删除】key=project:groups:100, 结果=成功
【Redis删除】key=project:groups:100, 结果=未找到
【Redis错误】删除时key为null
```

#### 检查缓存 (hasKey)
```
【Redis检查】key=project:groups:100, 存在=true
【Redis检查】key=project:groups:100, 存在=false
```

### 2. ProjectCacheUtil.java - 项目缓存操作层
现在会输出以下日志：

#### 缓存小组
```
【项目小组缓存】设置成功: key=project:groups:100, ttl=1800s
【项目小组缓存】设置失败: key=project:groups:100, projectId=100
```

#### 获取小组缓存
```
【项目小组缓存】命中: key=project:groups:100
【项目小组缓存】未命中: key=project:groups:100
```

#### 清除小组缓存
```
【项目小组缓存】清除成功: key=project:groups:100
【项目小组缓存】清除(key不存在或失败): key=project:groups:100
```

## 排查步骤

### Step 1: 检查 Redis 连接
运行添加小组操作，查找这些日志：
```
【Redis设置成功】key=project:groups:* 
```

**如果看不到这条日志**，说明 Redis 连接或序列化有问题。查找：
```
【Redis设置失败】
【Redis错误】
```

### Step 2: 检查缓存设置是否成功
查找 ProjectCacheUtil 的日志：
```
【项目小组缓存】设置成功
```

**如果看到**：
```
【项目小组缓存】设置失败
```
则说明 `RedisBaseUtil.set()` 返回了 `false`。需要查看其下一行的 `【Redis设置失败】` 来了解具体原因。

### Step 3: 完整的调用链日志示例

**正常流程应该看到**：
```
== 添加小组到项目 ==
[GroupServiceImpl] 已更新项目小组缓存: projectId=100, count=5

【Redis设置成功】key=project:groups:100, expireSeconds=1800
【项目小组缓存】设置成功: key=project:groups:100, ttl=1800s

== 立即查询小组 ==
【Redis获取成功】key=project:groups:100, value类型=ArrayList
【项目小组缓存】命中: key=project:groups:100
```

**问题流程示例1 - Redis 未连接**：
```
【Redis设置失败】key=project:groups:100, 异常信息: Connection refused (或 DENIED Redis is running in protected mode)
【项目小组缓存】设置失败: key=project:groups:100, projectId=100
```

**问题流程示例2 - 序列化问题**：
```
【Redis设置失败】key=project:groups:100, 异常信息: Cannot serialize object of class java.util.ArrayList
【项目小组缓存】设置失败: key=project:groups:100, projectId=100
```

**问题流程示例3 - 内存不足**：
```
【Redis设置失败】key=project:groups:100, 异常信息: OOM command not allowed when used memory > 'maxmemory'
【项目小组缓存】设置失败: key=project:groups:100, projectId=100
```

## 常见问题及解决方案

### 1️⃣ 看到 "Connection refused"
**问题**: Redis 服务未启动或地址配置错误  
**解决**: 
- 检查 Redis 是否运行：`redis-cli ping`
- 检查 `application.yml` 中的 Redis 配置
- 检查防火墙和网络连接

### 2️⃣ 看到 "protected mode" 错误
**问题**: Redis 开启了保护模式但没有密码
**解决**: Redis 配置中设置 `protected-mode no` 或配置密码

### 3️⃣ 看到 "Cannot serialize" 错误
**问题**: RedisTemplate 的序列化配置有问题  
**解决**: 检查 Redis 配置类中是否正确配置了序列化方式

### 4️⃣ 看到 "OOM" 错误
**问题**: Redis 内存已满  
**解决**: 增加 Redis 最大内存设置或清理过期数据

### 5️⃣ 没有任何日志输出
**问题**: 代码可能没有走到缓存设置的地方  
**解决**: 
- 检查是否抛出了异常
- 检查权限校验是否通过
- 添加更多日志到 GroupServiceImpl 中

## 如何阅读日志

打开应用日志（console 或日志文件），搜索关键词：
```
【Redis          → 底层操作日志
【项目小组缓存    → 业务层日志
```

完整的成功流程应该包含两个级别的日志，例如：
```
1. 【Redis设置成功】    ← 底层操作成功
2. 【项目小组缓存】设置成功  ← 业务确认
```

如果只有底层日志而没有业务日志，说明业务层没有正确检查返回值。

## 测试 Redis 操作

在修改后，可以使用以下方式测试：

### 使用 Redis CLI 验证数据
```bash
# 查看指定 key
redis-cli GET project:groups:100

# 查看所有小组相关的 key
redis-cli KEYS "project:groups:*"

# 查看 key 的过期时间（秒）
redis-cli TTL project:groups:100

# 监控所有 Redis 操作
redis-cli MONITOR
```

### 使用 API 测试
```bash
# 1. 添加小组到项目
POST /groups/{groupId}/add-to-project
Content-Type: application/json
{
  "groupId": 1,
  "projectId": 100
}

# 2. 立即查询（应该看到【项目小组缓存】命中）
GET /groups?projectId=100

# 3. 查看 Redis 中的数据
redis-cli GET project:groups:100
```

## 改进内容总结

✅ **RedisBaseUtil** - 添加了详细的操作日志
- `set()` - 显示设置成功/失败 + 异常信息
- `get()` - 显示是否找到 + 数据类型
- `delete()` - 显示删除结果
- `hasKey()` - 显示 key 是否存在

✅ **ProjectCacheUtil** - 添加了业务级日志
- `cacheProjectGroups()` - 检查返回值并记录结果
- `getProjectGroupsCache()` - 记录命中/未命中
- `clearProjectGroupsCache()` - 记录清除结果

现在运行应用后，所有 Redis 操作都会被完整记录，可以准确诊断问题所在。

## 3. 菜单路由缓存排障

### 3.1 相关缓存键

- `auth:menu:version`：菜单路由缓存全局版本号
- `auth:routes:role:{roleCode}:v{menuVersion}`：某个角色当前版本的路由树缓存

### 3.2 正常日志示例

首次请求 `/auth/routes` 时，通常会看到：

```text
【Redis获取】key=auth:menu:version 不存在
【Redis设置成功】key=auth:menu:version, persistent=true
【Redis获取】key=auth:routes:role:admin:v1 不存在
【缓存回填】查询当前用户路由: userId=1, role=admin, version=1, count=8
【Redis设置成功】key=auth:routes:role:admin:v1, expireSeconds=3600
```

第二次同角色访问时，通常会看到：

```text
【Redis获取成功】key=auth:menu:version, value类型=Long
【Redis获取成功】key=auth:routes:role:admin:v1, value类型=ArrayList
【缓存命中】查询当前用户路由: userId=1, role=admin, version=1, count=8
```

菜单新增/更新/删除后，通常会看到：

```text
菜单路由缓存版本已更新: version=2
```

### 3.3 排查步骤

1. 确认菜单写接口是否成功返回 200。
2. 查看应用日志中是否出现 `菜单路由缓存版本已更新`。
3. 使用 Redis CLI 查看版本号：

```bash
redis-cli GET auth:menu:version
```

4. 查看对应角色路由缓存是否已切换到新版本：

```bash
redis-cli KEYS "auth:routes:role:admin:*"
```

5. 如果版本号没变，说明菜单写入流程没有走到失效逻辑。
6. 如果版本号变了但前端还是旧菜单，检查 `/auth/routes` 是否还在读取旧响应缓存或是否有代理层缓存。

### 3.4 常见问题


## 新增：数据库被打穿 & 响应变慢 的排查与建议

### 概要
近期出现后端响应变慢，怀疑是“打穿数据库”。我检查了代码中与缓存失效、统计重建相关的实现，发现有几个可能导致数据库压力与响应延迟的点，建议优先修复并临时缓解。

### 主要发现
- 每次提交打分（`submitScore`）在事务提交后会调用 `projectStatisticsSummaryRebuildService.rebuildProjectStatisticsSummaryAsync(projectId)`，该方法会执行多个全量查询（`selectGroupScoreDetails`、`selectIndicatorScoreDetails`、`selectScorerDistribution`），并进行复杂计算与批量 upsert。若并发提交较多，会触发大量异步重建任务，瞬时并发查询会压垮数据库。
- 提交分数时会调用 `scoringRecordCacheUtil.clearUserProjectRecordsCache(projectId, userId)` 清除缓存。实现采用 `RedisTemplate.scan(match="*" + prefix + "*")` 扫描全局 key 空间并删除匹配 key。当缓存量大或 key 总量很多时，扫描与批量删除会给 Redis 带来额外压力，且删除操作可能与数据库重建同时进行，造成资源争用。
- `ScoringRecordMapper.selectPageByProjectAndUser` + `detailMapper.selectByRecordIds` 在分页读取时每次都会访问数据库，若缓存未命中且并发请求较多，会产生大量小而频繁的查询。
- Hikari 连接池配置在 `application.yml` 中 `maximum-pool-size: 20`，若异步任务很多且每个任务需要多个连接，可能达到连接池上限，导致请求排队等待连接而变慢。

### 风险与证据
- 触发点：`submitScore()` → 事务提交后触发统计重建（每次）
- 证据：代码位置 [ScoringRecordServiceImpl](src/main/java/com/eval/gameeval/service/impl/ScoringRecordServiceImpl.java#L1-L600)
- 风险：数据库短时间内承受大量全表/分组扫描查询与批量写入，连接耗尽，出现响应延迟或超时；Redis 扫描与删除加重 I/O 压力。

### 临时缓解（可快速部署）
1. 暂时关闭/延迟统计重建：在 `submitScore` 中注释掉 `triggerProjectStatisticsSummaryRebuildAfterCommit(projectId)`，或在 `ProjectStatisticsSummaryRebuildService` 中增加防护（例如立即返回如果已有待处理任务）。
2. 将恶意判定自动刷新功能改为阈值模式或延迟批处理：把 `MALICIOUS_RULE_AUTO` 改为 `THRESHOLD`，减少每次打分触发的额外扫描。
3. 把 `scoringRecordCacheUtil.clearUserProjectRecordsCache` 改为只删除精确 key（已知 page），避免使用通配符扫描。短期内若业务允许，可直接不清除缓存以观察效果。
4. 增加数据库连接池容量并观察（短期），或开启慢查询日志，定位最慢 SQL：在数据库端开启 slow_query_log 并分析。

### 建议的长期修复（优先顺序）
1. 批量/去重重建任务（高优先级）
  - 设计单一的“项目统计重建队列”：将请求入队并合并同一 projectId 的多次重建到一次（例如使用内存去重 + 延迟 1-5 秒执行，或使用 Redis 的去重队列/布隆过滤器）。
  - 伪代码（在服务内）示例：

```java
// 简化示例：延迟合并重建（单个 JVM）
private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
private final ConcurrentMap<Long, Boolean> pending = new ConcurrentHashMap<>();

public void requestRebuild(Long projectId) {
   if (pending.putIfAbsent(projectId, Boolean.TRUE) == null) {
      scheduler.schedule(() -> {
        try { rebuildService.rebuildProjectStatisticsSummary(projectId); }
        finally { pending.remove(projectId); }
      }, 2, TimeUnit.SECONDS); // 合并 2s 内的请求
   }
}
```

2. 改进缓存清除策略（高优先级）
  - 在写缓存时，维护一个专门的 Set 来记录该项目/用户的 cache keys，例如 `scoring:record:keys:{projectId}:{userId}`。写缓存时 `SADD` 新 key；清除时 `SMEMBERS` + `DEL`。这样避免全局 SCAN。
  - 示例（Redis 命令层面）：
    - 写缓存：`SET scoring:record:page:...` 之后 `SADD scoring:record:keys:pid:uid scoring:record:page:...`
    - 清除：`SMEMBERS scoring:record:keys:pid:uid` → pipeline `DEL` → `DEL scoring:record:keys:pid:uid`

3. 将耗时的聚合计算移到离线/定时任务中（中优先级）
  - 如果统计不是强实时可见，改为每分钟或更长时间批量重建，或在低峰期重建。

4. 优化 SQL 与分页策略（中优先级）
  - 检查 `selectGroupScoreDetails` 等 SQL 的执行计划，确保有合适索引（`project_id`、`group_info_id`、`user_id` 等）。
  - 对大表使用分页游标或增量增量计算，而不是每次全表扫描。

5. 监控与限流（中/低优先级）
  - 增加指标：DB 连接占用、慢 SQL、Redis latency、应用线程池饱和度。
  - 对高频接口（打分）做速率限制或熔断，防止刷量打穿后台。

### 推荐代码示例（替换清除实现）
替换 `ScoringRecordCacheUtil.clearUserProjectRecordsCache` 为：

```java
// 在写入缓存时：
redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
redisTemplate.opsForSet().add("scoring:record:keys:"+projectId+":"+userId, key);

// 清除时：
Set<Object> keys = redisTemplate.opsForSet().members("scoring:record:keys:"+projectId+":"+userId);
if (keys != null && !keys.isEmpty()) {
   // pipeline/delete in batches
   redisTemplate.delete(keys.stream().map(Object::toString).collect(Collectors.toList()));
   redisTemplate.delete("scoring:record:keys:"+projectId+":"+userId);
}
```

注意：使用 Set 需要在缓存写入处添加 `SADD`，并在删除后清理该 Set。

### 排查步骤与验证
1. 临时：注释掉重建触发并观察系统延迟是否恢复；若恢复，说明重建任务是主因。
2. 在 DB 侧开启慢查询与当前连接数监控，观察高延迟时间段的慢 SQL。
3. 部署“延迟合并重建”或“Key Set 清理”修复后，重新压测并观察 DB 连接利用率与 QPS。

### 结论
当前最可能的原因是：每次打分都会触发耗时的统计重建（全量查询 + 批量写入）以及全局 Redis SCAN 删除，这两者在高并发下会相互放大，导致数据库连接池耗尽和请求排队。优先采取临时禁用/延迟重建与改为维护 key-set 的缓存清理策略，可以快速缓解问题；随后实现合并重建队列与 SQL/索引优化作为长期修复。

---
如需我可以：
- 帮你把 `ScoringRecordCacheUtil` 改为基于 Set 的实现并提交 patch；
- 增加延迟合并重建示例并替换当前触发逻辑；
- 写一份部署前的回滚与验证清单。

## 新增：用户列表 / 在线用户当前页信息排查

### 1. 普通用户列表 `getUserList`

代码位置：[UserServiceImpl](src/main/java/com/eval/gameeval/service/impl/UserServiceImpl.java#L557)

当前实现的特点是：
- 先做一次当前登录用户查询和权限校验。
- 再执行一次分页列表查询 `userMapper.selectPageWithGroups(...)`。
- 再执行一次总数查询 `userMapper.countTotal(...)`。
- 最后在内存里把 `GROUP_CONCAT(rgm.group_id)` 结果拆成列表并组装 VO。

这里没有明显的“逐条查数据库”的 N+1 问题，数据库压力主要来自：
- `sys_user` + `reviewer_group_member` 的 `LEFT JOIN`。
- `GROUP BY u.id` 和 `COUNT(*)` 在带模糊搜索时的代价。
- `LIKE '%keyword%'` 这种条件会削弱索引效果。

结论：如果你感受到的是“用户列表分页变慢”，优先检查 SQL 执行计划和索引，而不是缓存失效。这个接口更像是“单次查询偏重”，不是“打穿数据库”的主要来源。

### 2. 在线用户列表 `getOnlineUsers`

代码位置：[AuthServiceImpl](src/main/java/com/eval/gameeval/service/impl/AuthServiceImpl.java#L414)

这条链路更危险，原因是它有两层放大：
- 先通过 `authSessionStore.getActiveOnlineUserIds()` 或 `getLoggedInUserIds()` 扫描整个 `auth:session:*` 键空间，筛选出用户集合。
- 然后对当前页的每个用户，调用 `buildSessionSummary(userId)`，而这个方法会：
  - `authSessionStore.getUserSessions(userId)` 读一次用户会话集合；
  - 再对集合里的每个 `sid` 调一次 `authSessionStore.getSession(sid)`。

这意味着在线用户列表本质上是：
- 1 次全量 Redis 扫描 +
- N 次用户会话集合读取 +
- M 次单会话读取

如果当前页有 10 个用户，每人有 3 个会话，就会额外发出至少 40 次 Redis 相关读操作，还没算 session 里的地理位置解析。

### 3. 具体热点

- `AuthSessionStore.getActiveOnlineUserIds()`：扫描 `auth:session:*`，对所有 session hash 做 `hGet`。
- `AuthSessionStore.getLoggedInUserIds()`：同样扫描 `auth:session:*`，只是少了活跃窗口判断。
- `AuthServiceImpl.buildSessionSummary(...)`：每个用户都要读取自己的 `sid` 集合，再逐个读 session hash。
- `resolveLoginLocation(...)`：如果 session 里没有 `loginLocation`，还会回退到 IP 地理位置解析。

### 4. 风险判断

如果你观察到的是“在线用户列表越多，响应越慢”，这里大概率是根因之一，而不是 MyBatis 的分页 SQL。

如果你观察到的是“普通用户列表变慢”，重点仍然是 SQL 本身：
- `selectPageWithGroups`
- `countTotal`
- `GROUP_CONCAT`
- `LIKE` 模糊搜索

### 5. 建议修复顺序

1. 在线用户列表先做降本：
  - 只返回基础分页数据，不在列表中实时计算每个用户的完整 `SessionSummary`。
  - 把 `onlineCount / lastActiveAt / loginLocation / ip` 改成延迟展开或点击详情再查。

2. 给在线用户摘要做缓存：
  - 按 `userId` 缓存会话摘要，TTL 30-60 秒即可。
  - 列表页直接读摘要缓存，减少 `getSession` 的重复读取。

3. 给 `AuthSessionStore` 增加索引化数据结构：
  - 维护 `auth:online:userIds`、`auth:loggedIn:userIds` 之类的 Set，而不是每次全量 `SCAN`。
  - 维护 `auth:user:lastSessionSummary:{userId}` 之类的摘要 key。

4. 普通用户列表做 SQL 优化：
  - 为 `sys_user` 增加联合索引，至少覆盖 `is_deleted, is_enabled, role, create_time`。
  - 为 `reviewer_group_member(user_id)` 建索引。
  - 如果关键词搜索很频繁，考虑拆分搜索策略，不要直接依赖 `%keyword%` 模糊匹配。

### 6. 一句话结论

“用户列表当前页信息”里，普通用户列表主要是 SQL 偏重；在线用户列表则明显是 Redis/session 读取放大，属于更典型的性能热点，值得优先处理。

## 新增：项目列表 / 授权项目分页排查

### 1. 项目列表 `getProjectList`

代码位置：[src/main/java/com/eval/gameeval/service/impl/ProjectServiceImpl.java](src/main/java/com/eval/gameeval/service/impl/ProjectServiceImpl.java)

发现点：
- 入口会调用 `reconcileProjectStatuses(...)`，每次请求都会执行一次“状态不一致检查 + 可能的同步更新”。如果流量大，这个 DB 读写会成为稳定的额外开销。
- 缓存未命中时，分页查询项目后，**对每个项目**再执行两次查询：
  - `groupMapper.selectByProjectId(projectId)`
  - `scorerMapper.selectByProjectId(projectId)`
  这属于典型 N+1 放大（每页 N 个项目 → 2N 次额外查询）。

风险：
- 在高并发场景下，哪怕分页只返回 10 条，也会触发 20 次额外查询，DB 压力会被放大。
- 当缓存失效或频繁被清理时，列表页会退化成“多查询热点”。

建议：
1. 给项目列表补“批量加载”接口（一次性查出 group/scorer 关系），避免 N+1。
2. `reconcileProjectStatuses` 改为定时任务或带缓存节流（例如 1-5 分钟内只执行一次）。
3. 对 `project` 表和 `project_group`、`project_scorer` 建索引，保证关联查询可用索引。

### 2. 授权项目列表 `getAuthorizedProjects`

代码位置：[src/main/java/com/eval/gameeval/service/impl/ProjectServiceImpl.java](src/main/java/com/eval/gameeval/service/impl/ProjectServiceImpl.java)

发现点：
- 与 `getProjectList` 类似，分页查出项目后，对每个项目再查 group/scorer，仍是 N+1。
- 入口同样调用 `reconcileProjectStatuses(...)`，有额外 DB 开销。

建议与 `getProjectList` 相同：批量查询关联关系 + 状态纠偏节流。

### 3. 项目详情 `getProjectDetail`

发现点：
- 详情接口有缓存与空值缓存，但缓存未命中时依然会做 group/scorer 两次查询。
- 如果某些写接口频繁清缓存，详情接口会退化成高频读 DB。

建议：
- 保证缓存命中率，减少频繁的全量清理。
- 可以考虑把 group/scorer 关系缓存成单独的 key，避免每次查详情都回源。

### 4. 项目概览 `getProjectOverview`

发现点：
- 已有概览缓存，正常情况下不会频繁回源 DB。
- 入口仍会调用 `reconcileProjectStatuses(...)`，导致概览接口也有额外 DB 访问。

建议：
- 把状态纠偏改为定时任务或带节流的懒执行，避免概览接口被拖慢。

## 新增：小组列表 / 评审组列表排查

### 1. 项目小组列表 `getProjectGroups`

代码位置：[src/main/java/com/eval/gameeval/service/impl/GroupServiceImpl.java](src/main/java/com/eval/gameeval/service/impl/GroupServiceImpl.java)

发现点：
- 缓存未命中时会先查 `project_group` 关系表，再对每条关系执行 `groupInfoMapper.selectById`，属于 N+1。
- 如果项目包含的小组较多或缓存频繁失效，会放大数据库读压力。

建议：
1. 批量拉取 `project_group_info`（一次性 `IN (...)`），减少 N+1。
2. 保持项目小组缓存稳定，避免频繁清理导致回源。

### 2. 全局小组列表 `getAllGroups`

发现点：
- 只做分页查询与总数统计，没有明显 N+1。
- 关键词搜索使用 `LIKE '%keyword%'`，在数据量大时会退化为全表扫描。

建议：
- 给 `project_group_info.name` 加索引；若搜索很频繁，可考虑前缀搜索或引入搜索索引。

### 3. 评审组列表 `getReviewerGroupList`

代码位置：[src/main/java/com/eval/gameeval/service/impl/ReviewerGroupServiceImpl.java](src/main/java/com/eval/gameeval/service/impl/ReviewerGroupServiceImpl.java)

发现点：
- 分页查询评审组后，对每个评审组调用 `memberMapper.selectUserIdsByGroupId`，属于 N+1。
- 当评审组数量多时，列表页会导致大量单条查询。

建议：
1. 批量查询 `reviewer_group_member`，按 group_id 聚合返回。
2. 可将 memberIds 拆为“详情页再查”，列表只展示成员数量。

### 4. 小组/评审组概览

发现点：
- `getGroupOverview` 与 `getReviewerGroupOverview` 已走缓存，正常情况下不会频繁回源。

## 新增：打分标准列表 / 详情排查

### 1. 打分标准列表 `getStandardList`

代码位置：[src/main/java/com/eval/gameeval/service/impl/ScoringStandardServiceImpl.java](src/main/java/com/eval/gameeval/service/impl/ScoringStandardServiceImpl.java)

发现点：
- 缓存未命中时会先 `selectAll()` 取出所有标准，然后对每个标准分别查询分类与指标，属于 N+1（每个标准 2 次额外查询）。
- 关键词搜索与分页在内存中完成，不走数据库分页。标准数量大时，会出现“全量拉取 + 内存筛选”的性能放大。

建议：
1. 标准数量增长后，应该改为数据库分页 + 条件过滤。
2. 如果继续用缓存列表，可把分类/指标改为延迟加载，避免首次加载时放大查询。

### 2. 打分标准详情 `getStandardDetail`

发现点：
- 已有详情缓存与空值缓存，穿透风险较低。
- 查询详情时仍需要两次查询（分类 + 指标），但可被详情缓存覆盖。

### 3. 打分标准概览 `getStandardOverview`

发现点：
- 已走 `OverviewCacheUtil` 缓存，正常情况下不会频繁回源。

## 新增：平台统计 / 概览排查

### 1. 平台统计 `getPlatformStatistics`

代码位置：[src/main/java/com/eval/gameeval/service/impl/ProjectStatisticsServiceImpl.java](src/main/java/com/eval/gameeval/service/impl/ProjectStatisticsServiceImpl.java)

发现点：
- 平台统计使用 `statistics:platform:overview` 缓存，命中时直接返回。
- 缓存未命中时会执行 3 次聚合查询：
  - `countProjectsBefore`
  - `selectDailyProjectCount`
  - `selectDailyScoreCount`
  - `selectDailyAverageScore`
  这些查询会对 `project` 与 `scoring_record` 做按天聚合。

风险：
- 如果平台统计缓存频繁失效（TTL 过短或被频繁清理），会重复触发聚合查询。

建议：
1. 保持平台统计缓存稳定，避免写操作频繁清理或 TTL 过短。
2. 对 `project.create_time`、`scoring_record.create_time` 建索引，保证聚合查询可用索引扫描。

### 2. 各类概览接口

已确认以下概览均走缓存：
- 项目概览、用户概览、打分标准概览、小组概览、评审组概览。

## 排查 TODO

### 已排查业务

- [x] 打分提交链路：`submitScore` 触发统计汇总重建，存在全量查询和批量写入放大问题。
- [x] 用户列表分页：`getUserList` 主要是 SQL 偏重，不是明显的 N+1 读取。
- [x] 在线用户列表分页：`getOnlineUsers` + `buildSessionSummary` 存在 Redis/session N+1 读取放大。
- [x] 打分记录分页：`getUserProjectRecords` 已使用 Redis 缓存，但失效清理使用了通配符扫描，存在 Redis 压力。
- [x] 菜单路由缓存：版本号缓存机制和路由 key 切换逻辑已整理清楚。
- [x] 项目列表分页：`getProjectList`/`getAuthorizedProjects` 存在 group/scorer 关联的 N+1 查询放大。
- [x] 项目详情与项目概览：`getProjectDetail`/`getProjectOverview` 有缓存，但入口的状态纠偏会带来额外 DB 开销。
- [x] 小组列表与小组详情：项目小组存在 N+1；评审组列表存在成员 N+1；全局小组分页主要是 LIKE 搜索退化。
- [x] 评审标准/打分标准列表：列表缓存未命中时存在 N+1，且分页与搜索在内存执行。
- [x] 统计概览接口：项目/用户/标准/小组/评审组概览与平台统计均走缓存，但需关注平台统计聚合查询的回源成本。

### 待排查业务

- [ ] 批量查询用户/会话详情：检查 `batchQueryUsers`、`getUserSessions` 这类批量接口是否存在逐条补查。

### 建议排查顺序

1. 先看高频读接口：项目列表、在线用户列表、用户列表。
2. 再看高频写接口：打分提交、用户修改、项目/小组维护。
3. 最后看统计类接口：概览、汇总、榜单、导出。

### 记录规则

- 已确认问题的业务写成 `[x]`。
- 只是怀疑、还没看代码的业务写成 `[ ]`。
- 每排查完一个业务，就把结论补到对应条目后面，避免重复检查。

**优先检查**：
- `auth:menu:version` 是否递增
- `/auth/routes` 是否命中 `auth:routes:role:{roleCode}:v{menuVersion}`
- 是否有前端本地缓存或网关缓存拦截了响应

#### `auth:menu:version` 不存在

**处理方式**：
- 让 `/auth/routes` 首次访问自动初始化
- 或手动执行：`redis-cli SET auth:menu:version 1`

#### 路由缓存 key 很多

**原因**：
- 版本号递增后旧 key 仍会保留到 TTL 到期

**说明**：
- 这是预期行为，不需要批量删除
- 只要版本号始终使用最新值，旧 key 不会再被读到

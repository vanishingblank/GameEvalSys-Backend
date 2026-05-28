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

#### 菜单改了但前端没变化

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

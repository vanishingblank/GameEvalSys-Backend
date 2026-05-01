# 项目逻辑速读

这份文档的目标不是把每个接口都展开，而是让第一次接触这个后端的人，能在几分钟内搞清楚它“做什么、怎么跑、数据怎么流、代码该从哪看起”。

## 1. 这个项目解决什么问题

GameEvalSys Backend 是一个评分平台后端，主要服务于“项目评分、评审分组、标准打分、统计汇总、导出分析”这一整套流程。

它的职责可以概括为四层：

1. 提供登录、登出、刷新令牌等认证能力。
2. 维护项目、分组、评审组、评分标准和评分记录等业务数据。
3. 基于 Redis 和定时任务维护缓存、会话和项目状态。
4. 对外提供统计接口和导出接口，支撑前端展示与管理操作。

## 2. 一次请求是怎么走的

最常见的业务请求路径如下：

1. 前端先调用登录接口。
2. 后端校验账号密码，签发访问令牌和刷新令牌。
3. 访问令牌通过请求头 `Authorization: Bearer <token>` 传递。
4. `TokenAuthenticationFilter` 先读取请求头，再去 Redis 校验 token 是否有效。
5. 校验通过后，过滤器把当前用户和角色写入 Spring Security 上下文。
6. 控制器层拿到当前用户信息，调用 service 层完成业务处理。
7. service 层通过 mapper 访问数据库，必要时同步更新 Redis 缓存。
8. 结果统一包装成 `ResponseVO<T>` 返回给前端。

这个项目的核心特点是：

- 认证是“无状态”的，服务端不依赖传统 Session。
- 登录态主要由 token + Redis 共同维护。
- 权限判断在 Spring Security 里统一收口，而不是散落在各个接口里。

## 3. 主要运行时组件

### 启动入口

项目从 [GameEvalApplication.java](../src/main/java/com/eval/gameeval/GameEvalApplication.java) 启动。它开启了：

- Spring Boot 主应用
- 缓存能力
- 定时任务能力

这意味着应用启动后，除了处理 HTTP 请求，还会自动执行缓存维护和初始化任务。

### 安全链路

安全配置在 [SecurityConfig.java](../src/main/java/com/eval/gameeval/config/SecurityConfig.java)。当前策略是：

- `/auth/login`、`/auth/logout`、`/auth/refresh` 放行。
- `/admin/**` 需要管理员或超级管理员权限。
- 其他接口默认都要先通过认证。
- 使用无 Session 的方式运行，认证信息靠 token 传递。

真正解析 token 的逻辑在 [TokenAuthenticationFilter.java](../src/main/java/com/eval/gameeval/interceptor/TokenAuthenticationFilter.java)。它的职责很明确：

- 识别 `Bearer` token。
- 去 Redis 校验 token 是否有效。
- 读出用户 ID 和角色。
- 把当前请求映射成 Spring Security 可识别的认证对象。

### 登录和会话

登录入口在 [AuthController.java](../src/main/java/com/eval/gameeval/controller/AuthController.java)。它提供：

- `POST /auth/login`：登录并写入刷新令牌 Cookie
- `POST /auth/logout`：退出登录并清理刷新令牌 Cookie
- `POST /auth/refresh`：使用刷新令牌换新访问令牌
- `GET /auth/sessions/me`：查看当前用户的会话信息

也就是说，这个系统不是只靠一个短期 token 就结束，而是把“访问令牌 + 刷新令牌 + Redis 会话状态”组合起来，既方便前端长期登录，又能做会话控制。

## 4. 代码是怎么分层的

项目采用比较标准的后端分层方式：

- `controller`：接收 HTTP 请求，做参数校验和返回封装。
- `service`：承载业务规则，是大多数逻辑判断的中心。
- `mapper`：负责数据库访问，基本是一层 SQL 映射。
- `models`：承载 DTO、VO、Entity 等对象。
- `config`：Spring Security、CORS 等基础配置。
- `interceptor`：请求过滤、鉴权入口、异常入口等横切逻辑。
- `init`：初始化、缓存预热、定时维护任务。
- `util`：token、redis key、缓存辅助之类的工具代码。

如果你只想快速定位“业务规则在哪”，优先看 `service`。如果你想知道“接口入口在哪”，看 `controller`。如果你想知道“为什么这个请求会被拦截”，看 `config` 和 `interceptor`。

## 5. 数据和缓存怎么配合

这个项目的存储不是只有数据库，还包含 Redis 的状态配合。

### 数据库

数据库负责保存长期业务数据，例如：

- 用户
- 项目
- 分组
- 评审组
- 打分标准
- 打分记录

### Redis

Redis 负责更偏“运行态”的数据，例如：

- token 会话
- 登录状态
- 缓存的统计结果
- 项目状态相关缓存

### 定时任务

项目启动后会启用定时任务，主要做两件事：

1. 缓存热数据预热。
2. 对项目状态做纠偏和同步。

这里有一个很重要的业务口径：项目状态是按时间自动计算的，而不是只靠人工改状态。当前口径是：

- 当前时间早于开始时间：未开始
- 当前时间晚于结束时间：已结束
- 其他情况：进行中

这类状态纠偏完成后，还会联动清理相关缓存，保证列表、详情、统计这些页面不会读到旧数据。

## 6. 打分标准为什么要特别看

打分标准已经演进成“分类化结构”。这意味着它不再只是一个平铺列表，而是“分类 + 指标”的组合。

你在看这块逻辑时要注意：

- 前端展示可能同时兼容平铺字段和分类字段。
- 新增或更新标准时，不能只看指标本身，还要看它归属的分类。
- SQL 查询、插入、更新都要携带分类信息。

这块是评分业务的核心之一，因为它直接影响项目评分的规则展示和录入结构。

## 7. 建议按什么顺序读代码

如果你是第一次读这个项目，建议按下面顺序：

1. 先看 [GameEvalApplication.java](../src/main/java/com/eval/gameeval/GameEvalApplication.java)，确认应用启动能力。
2. 再看 [SecurityConfig.java](../src/main/java/com/eval/gameeval/config/SecurityConfig.java)，理解哪些接口需要鉴权。
3. 接着看 [TokenAuthenticationFilter.java](../src/main/java/com/eval/gameeval/interceptor/TokenAuthenticationFilter.java)，理解 token 是怎么变成当前用户的。
4. 然后看 [AuthController.java](../src/main/java/com/eval/gameeval/controller/AuthController.java)，把登录、刷新、登出串起来。
5. 最后从某一个具体业务模块进入，比如项目、评分标准、评分记录或统计接口，沿着 controller -> service -> mapper 去看。

## 8. 部署时要记住的事情

项目支持单机 Docker 部署和 LXC 多容器部署。最常见的本地/测试环境依赖是：

- Java 17
- MariaDB / MySQL
- Redis

部署时要特别关注三件事：

1. 数据库初始化脚本是否已执行。
2. Redis 是否可用，因为 token 和缓存依赖它。
3. 初始化管理员配置是否正确，否则第一次启动可能没有可登录账号。

## 9. 一句话总结

这个项目本质上是一个“Spring Boot + Security + Redis + MyBatis”的评分平台后端：

- 请求先过 token 鉴权
- 业务逻辑集中在 service
- 数据落库，运行态落 Redis
- 定时任务负责缓存和状态纠偏
- 评分标准、评分记录、统计导出是业务主线

如果只想先抓住主干，优先看“认证链路 + 项目状态 + 评分标准”这三块。
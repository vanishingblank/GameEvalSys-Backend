# 课题项目打分系统 API 文档

## 文档说明

- 基础路径：`/api/v1`
- 数据格式：请求/响应均为 JSON
- 状态码：
  - 200：请求成功
  - 400：参数错误
  - 401：未授权/Token 失效
  - 403：权限不足
  - 404：资源不存在
  - 500：服务器内部错误
- 通用响应格式：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {}
  }
  ```

## 1. 认证模块

### 1.1 登录

- **接口地址**：`/auth/login`
- **请求方式**：POST
- **请求参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | username | string | 是 | 用户名 |
  | password | string | 是 | 密码 |
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "登录成功",
    "data": {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "userInfo": {
        "id": 1,
        "username": "admin",
        "role": "super_admin",
        "name": "超级管理员"
      }
    }
  }
  ```

### 1.2 退出登录

- **接口地址**：`/auth/logout`
- **请求方式**：POST
- **请求头**：`Authorization: Bearer {token}`
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "退出成功",
    "data": null
  }
  ```

## 2. 用户管理模块（超级管理员/管理员）

### 2.1 创建用户并分配评审组(可批量创建)

- **接口地址**：`/users`
- **请求方式**：POST
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | users | array | 是 | 用户列表 |
  | users[].username | string | 是 | 用户名（唯一） |
  | users[].password | string | 是 | 密码 |
  | users[].name | string | 是 | 真实姓名 |
  | users[].role | string | 是 | 角色（super_admin/admin/scorer/normal） |
  | users[].isEnabled | boolean | 否 | 是否启用（默认true） |
  | users[].reviewerGroupIds | array | 否 | 评审组ID（自动加入该评审组） |
- **请求示例**：
  ```json
  {
    "users": [
      {
        "username": "newuser01",
        "name": "新用户01",
        "role": "scorer",
        "isEnabled": true,
        "password": "qwert123",
        "reviewerGroupIds": [1, 2, 6]
      }
    ]
  }
  ```
- **响应示例**：

```json
{
  "code": 200,
  "message": "创建成功",
  "data": [
    {
      "id": 15,
      "username": "newuser01",
      "name": "新用户01",
      "role": "scorer",
      "isEnabled": true,
      "createTime": "2026-03-03 18:30:00",
      "reviewerGroupIds": [1, 2, 6],
      "reviewerGroupName": "中期答辩评审组"
    }
  ]
}
```

### 2.2 编辑用户

- **接口地址**：`/users/{userId}`
- **请求方式**：PUT
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：`userId` - 用户ID
- **请求参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | name | string | 否 | 真实姓名 |
  | role | string | 否 | 角色 |
  | isEnabled | boolean | 否 | 是否启用 |
  | newPassword | string | 否 | 新密码 |
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "编辑成功",
    "data": null
  }
  ```

### 2.3 删除用户

- **接口地址**：`/users/{userId}`
- **请求方式**：DELETE
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：`userId` - 用户ID
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "删除成功",
    "data": null
  }
  ```

### 2.4 获取用户列表

- **接口地址**：`/users`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**（Query）：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | page | number | 否 | 页码（默认1） |
  | size | number | 否 | 每页条数（默认10） |
  | role | string | 否 | 角色筛选 |
  | keyWords | string | 否 | 关键词搜索 |
  | isEnabled | Boolean | 否 | 按启用状态筛选；不传时仅返回启用用户，`true` 返回启用用户，`false` 返回禁用用户 |
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "查询成功",
    "data": {
      "list": [
        {
          "id": 1,
          "username": "admin",
          "name": "超级管理员",
          "role": "super_admin",
          "isEnabled": true,
          "createTime": "2026-01-27 09:00:00",
          "reviewerGroupIds": [1, 2]
        }
      ],
      "total": 1,
      "page": 1,
      "size": 10
    }
  }
  ```

### 2.5 批量启用/禁用用户

- **接口地址**：`/users/batch-status`
- **请求方式**：PUT
- **请求头**：`Authorization: Bearer {token}`
- **权限要求**：仅超级管理员、管理员可调用
- **请求参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | userIds | array | 是 | 需要批量操作的用户ID列表 |
  | isEnabled | boolean | 是 | 目标状态，`true` 为启用，`false` 为禁用 |
- **请求示例**：
  ```json
  {
    "userIds": [2, 3, 4],
    "isEnabled": false
  }
  ```
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "批量禁用完成",
    "data": {
      "totalCount": 3,
      "successCount": 2,
      "failCount": 1,
      "failedIds": [4]
    }
  }
  ```
- **说明**
  - 允许部分成功，不会因为单个用户失败导致整批回滚
  - `failedIds` 表示本次未成功修改状态的用户ID列表，前端可直接用于提示或高亮
- **典型错误**
  - 400：`userIds` 为空、参数类型错误
  - 403：当前用户无权限操作
  - 404：用户不存在或批量接口暂未实现
  - 500：服务器内部错误

### 2.6 批量删除用户

- **接口地址**：`/users/batch-delete`
- **请求方式**：DELETE
- **请求头**：`Authorization: Bearer {token}`
- **权限要求**：仅超级管理员、管理员可调用
- **请求参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | userIds | array | 是 | 需要批量删除的用户ID列表 |
- **请求示例**：
  ```json
  {
    "userIds": [2, 3, 4]
  }
  ```
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "批量删除完成",
    "data": {
      "totalCount": 3,
      "successCount": 2,
      "failCount": 1,
      "failedIds": [4]
    }
  }
  ```
- **说明**
  - 允许部分成功，不会因为单个用户失败导致整批回滚
  - `failedIds` 表示本次未成功删除的用户ID列表
- **典型错误**
  - 400：`userIds` 为空、参数类型错误
  - 403：当前用户无权限操作
  - 404：用户不存在或批量接口暂未实现
  - 409：禁止删除当前登录用户、禁止删除超级管理员
  - 500：服务器内部错误

### 2.7 批量获取用户详情 (需求性调整，不完全符合restFul)

- **接口地址**：`/users/batch-query`
- **请求方式**：POST
- **请求头**：`Authorization: Bearer {token}`
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | ids | array | 是 | 用户ID列表 |
  | includeDisabled | boolean | 否 | 是否包含被禁用用户（默认false） |
- **响应示例**：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 2,
      "username": "scorer01",
      "name": "cxk001",
      "role": "scorer",
      "isEnabled": true,
      "createTime": "2026-02-24 10:00:00"
    },
    {
      "id": 3,
      "username": "scorer02",
      "name": "cxk002",
      "role": "scorer",
      "isEnabled": true,
      "createTime": "2026-02-24 10:05:00"
    }
  ]
}
```

### 2.8 用户更改自己的密码

- **接口地址**：`/users/me/password`
- **请求方式**：PUT
- **请求头**：`Authorization: Bearer {token}`
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | oldPassword | String | 是 | 用户正在使用的密码 |
  | newPassword | String | 是 | 用户想要更改成的密码 |
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "编辑成功",
    "data": null
  }
  ```

### 2.9 获取用户概览统计

- **接口地址**：`/users/overview`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **权限要求**：仅超级管理员、管理员可调用
- **请求参数**：无
- **响应示例**：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "totalUsers": 120,
    "adminUsers": 6,
    "scorerUsers": 88,
    "normalUsers": 26
  }
}
```

- **字段说明**：
  | 字段名 | 类型 | 说明 |
  |--------|------|------|
  | totalUsers | number | 用户总数 |
  | adminUsers | number | 管理员与超级管理员总数 |
  | scorerUsers | number | 打分用户总数 |
  | normalUsers | number | 普通用户总数 |

## 3. 打分标准管理模块（管理员）

### 3.1 创建打分标准

- **接口地址**：`/scoring-standards`
- **请求方式**：POST
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | name | string | 是 | 打分标准名 |
  | categories | array | 是 | 分类列表，至少 1 个分类 |
  | categories[].name | string | 是 | 分类名称 |
  | categories[].description | string | 否 | 分类描述 |
  | categories[].indicators | array | 是 | 分类下指标列表，至少 1 个指标 |
  | categories[].indicators[].name | string | 是 | 指标名称 |
  | categories[].indicators[].description | string | 否 | 指标说明 |
  | categories[].indicators[].minScore | number | 是 | 分值最小值 |
  | categories[].indicators[].maxScore | number | 是 | 分值最大值 |
- **兼容说明**：
  - 创建接口仍兼容旧字段 `indicators`，但前端应优先使用 `categories`。
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "创建成功",
    "data": {
      "id": 1,
      "name": "项目1的打分标准",
      "categories": [
        {
          "id": 11,
          "name": "项目质量",
          "description": "项目整体质量评价",
          "indicators": [
            {
              "id": 1,
              "name": "复杂程度",
              "description": "课题的技术复杂程度",
              "minScore": 1,
              "maxScore": 5
            }
          ]
        }
      ],
      "indicators": [
        {
          "id": 1,
          "name": "复杂程度",
          "description": "课题的技术复杂程度",
          "categoryId": 11,
          "minScore": 1,
          "maxScore": 5
        }
      ],
      "createTime": "2026-01-27 10:30:00"
    }
  }
  ```

### 3.2 获取打分标准列表

- **接口地址**：`/scoring-standards`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | keyWords | string | 否 | 模糊搜索关键词 |
  | page | number | 否 | 页码（默认1） |
  | size | number | 否 | 每页条数（默认10） |
- **响应示例**：

  ```json
  {
    "code": 200,
    "message": "查询成功",
    "data": {
      "list": [
        {
          "id": 1,
          "name": "项目1的打分标准",
          "categories": [
            {
              "id": 11,
              "name": "项目质量",
              "description": "项目整体质量评价",
              "indicators": [
                {
                  "id": 1,
                  "name": "复杂程度",
                  "description": "课题的技术复杂程度",
                  "minScore": 1,
                  "maxScore": 5
                }
              ]
            }
          ],
          "indicators": [
            {
              "id": 1,
              "name": "复杂程度",
              "description": "课题的技术复杂程度",
              "categoryId": 11,
              "minScore": 1,
              "maxScore": 5
            }
          ],
          "createTime": "2026-01-27 10:30:00"
        },
        {
          "id": 2,
          "name": "项目2的打分标准",
          "categories": [
            {
              "id": 21,
              "name": "展示表现",
              "description": "展示和答辩表现",
              "indicators": [
                {
                  "id": 3,
                  "name": "复杂程度",
                  "description": "课题的技术复杂程度",
                  "minScore": 1,
                  "maxScore": 5
                }
              ]
            }
          ],
          "indicators": [
            {
              "id": 3,
              "name": "复杂程度",
              "description": "课题的技术复杂程度",
              "categoryId": 21,
              "minScore": 1,
              "maxScore": 5
            }
          ],
          "createTime": "2026-01-27 10:30:00"
        }
      ],
      "total": 23,
      "page": 1,
      "size": 10
    }
  }
  ```

### 3.3 获取单个打分标准详情

- **接口地址**：`/scoring-standards/{standardId}`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：`standardId` - 标准ID
- **响应说明**：
  - 返回 `categories`（新结构）与 `indicators`（兼容结构）。
  - 前端渲染应优先读取 `categories`，`indicators` 作为兼容回退字段。
- **响应示例**：同上（单个标准详情）

### 3.4 编辑打分标准

- **接口地址**：`/scoring-standards/{standardId}`
- **请求方式**：PUT
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：`standardId` - 标准ID
- **请求参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | name | string | 否 | 打分标准名 |
  | categories | array | 否 | 分类列表，建议作为主结构传递 |
  | categories[].id | number | 否 | 分类ID；有值表示更新，无值表示新增 |
  | categories[].name | string | 是 | 分类名称 |
  | categories[].description | string | 否 | 分类描述 |
  | categories[].indicators | array | 是 | 分类下指标列表 |
  | categories[].indicators[].id | number | 否 | 指标ID；有值表示更新，无值表示新增 |
  | categories[].indicators[].name | string | 是 | 指标名称 |
  | categories[].indicators[].description | string | 否 | 指标说明 |
  | categories[].indicators[].minScore | number | 是 | 分值最小值 |
  | categories[].indicators[].maxScore | number | 是 | 分值最大值 |
- **更新策略说明**：
  - 请求中有且带 `id` 的分类/指标：更新。
  - 请求中有但不带 `id` 的分类/指标：新增。
  - 库中存在但请求缺失的分类/指标：删除。
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "编辑成功",
    "data": null
  }
  ```

### 3.5 获取打分标准概览统计

- **接口地址**：`/scoring-standards/overview`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **权限要求**：仅超级管理员、管理员可调用
- **请求参数**：无
- **响应示例**：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "totalStandards": 23,
    "enabledStandards": 20
  }
}
```

- **字段说明**：
  | 字段名 | 类型 | 说明 |
  |--------|------|------|
  | totalStandards | number | 打分标准总数 |
  | enabledStandards | number | 已启用打分标准总数 |

## 4. 项目管理模块（管理员）

### 4.1 创建项目

- **接口地址**：`/projects`
- **请求方式**：POST
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | name | string | 是 | 项目名称 |
  | description | string | 否 | 项目描述 |
  | startDate | string | 是 | 起始日期（yyyy-MM-dd HH:mm） |
  | endDate | string | 是 | 结束日期（yyyy-MM-dd HH:mm） |
  | isEnabled | boolean | 否 | 是否启用（默认true） |
  | standardId | number | 是 | 关联打分标准ID |
  | maliciousRuleType | string | 否 | 恶意判定规则：`AUTO`/`THRESHOLD`，默认 `AUTO` |
  | maliciousScoreLower | number | 否 | 恶意总分阈值下限（仅 `THRESHOLD` 生效） |
  | maliciousScoreUpper | number | 否 | 恶意总分阈值上限（仅 `THRESHOLD` 生效） |
  | groupIds | array | 是 | 关联小组ID列表 |
  | reviewerGroupId | number | 是 | 评审组ID，后端将自动绑定该评审组成员为打分用户 |
- **参数规则**：
  - 创建项目时不再接收 `scorerIds`，打分用户统一由 `reviewerGroupId` 推导。
  - 当 `maliciousRuleType=THRESHOLD` 时，`maliciousScoreLower` 与 `maliciousScoreUpper` 必须同时传入，且 `lower <= upper`。
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "创建成功",
    "data": {
      "id": 1,
      "name": "2026中期答辩",
      "description": "2026年度中期答辩打分",
      "startDate": "2026-03-01 08:10",
      "endDate": "2026-03-15 10:59",
      "status": "not_started",
      "isEnabled": true,
      "standardId": 1,
      "maliciousRuleType": "AUTO",
      "maliciousScoreLower": null,
      "maliciousScoreUpper": null,
      "groupIds": [1, 2],
      "scorerIds": [2, 3],
      "reviewerGroupId": 9,
      "creatorId": 1,
      "createTime": "2026-01-27 11:00:00"
    }
  }
  ```

### 4.2 编辑项目

- **接口地址**：`/projects/{projectId}`
- **请求方式**：PUT
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：`projectId` - 项目ID
- **请求参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | name | string | 否 | 项目名称 |
  | description | string | 否 | 项目描述 |
  | startDate | string | 否 | 起始日期（yyyy-MM-dd HH:mm） |
  | endDate | string | 否 | 结束日期（yyyy-MM-dd HH:mm） |
  | isEnabled | boolean | 否 | 是否启用 |
  | standardId | number | 否 | 关联打分标准ID |
  | maliciousRuleType | string | 否 | 恶意判定规则：`AUTO`/`THRESHOLD` |
  | maliciousScoreLower | number | 否 | 恶意总分阈值下限（仅 `THRESHOLD` 生效） |
  | maliciousScoreUpper | number | 否 | 恶意总分阈值上限（仅 `THRESHOLD` 生效） |
  | groupIds | array | 否 | 关联小组ID列表 |
  | scorerIds | array | 否 | 手动调整可参与打分的用户ID列表 |
- **参数规则**：
  - 当 `maliciousRuleType=THRESHOLD` 时，`maliciousScoreLower` 与 `maliciousScoreUpper` 必须同时有效，且 `lower <= upper`。
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "编辑成功",
    "data": null
  }
  ```

### 4.3 结束项目

- **接口地址**：`/projects/{projectId}/end`
- **请求方式**：POST
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：`projectId` - 项目ID
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "项目已结束",
    "data": null
  }
  ```

### 4.4 获取项目列表

- **接口地址**：`/projects`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**（Query）：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | page | number | 否 | 页码（默认1） |
  | size | number | 否 | 每页条数（默认10） |
  | status | string | 否 | 项目状态（not_started/ongoing/ended） |
  | isEnabled | boolean | 否 | 是否启用 |
  | keyWords | string | 否 | 模糊查询关键字 |
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "查询成功",
    "data": {
      "list": [
        {
          "id": 1,
          "name": "2026中期答辩",
          "status": "not_started",
          "isEnabled": true,
          "maliciousRuleType": "AUTO",
          "maliciousScoreLower": null,
          "maliciousScoreUpper": null,
          "startDate": "2026-03-01 08:10",
          "endDate": "2026-03-15 10:59"
        }
      ],
      "total": 1,
      "page": 1,
      "size": 10,
      "keyWords": ""
    }
  }
  ```

### 4.5 获取单个项目详情

- **接口地址**：`/projects/{projectId}`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：`projectId` - 项目ID
- **响应示例**：同创建项目响应（完整详情）

### 4.6 获取当前用户授权的项目列表

- **接口地址**：`/projects/authorized`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**（Query）：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | page | number | 否 | 页码（默认1） |
  | size | number | 否 | 每页条数（默认10） |
  | status | string | 否 | 项目状态（not_started/ongoing/ended） |
  | isEnabled | boolean | 否 | 是否启用 |
  | keyWords | string | 否 | 模糊查询关键字 |
- **响应示例**：同上（列表格式）

### 4.7 获取项目概览统计

- **接口地址**：`/projects/overview`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **权限要求**：仅超级管理员、管理员可调用
- **请求参数**：无
- **响应示例**：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "totalProjects": 42,
    "notStartedProjects": 10,
    "ongoingProjects": 25,
    "endedProjects": 7
  }
}
```

- **字段说明**：
  | 字段名 | 类型 | 说明 |
  |--------|------|------|
  | totalProjects | number | 项目总数 |
  | notStartedProjects | number | 未开始项目数（status=not_started） |
  | ongoingProjects | number | 进行中项目数（status=ongoing） |
  | endedProjects | number | 已截止项目数（status=ended） |

## 5. 小组管理模块（管理员）

### 5.1 创建小组

- **接口地址**：`/groups`
- **请求方式**：POST
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**：
  | 参数名 | 类型 | 必需 | 说明 |
  |------|------|------|------|
  | name | String | 是 | 小组名称，不能为空 |
  | description | String | 否 | 小组描述 |
  | isEnabled | Integer | 否 | 是否启用，默认为 1（启用）。0=禁用，1=启用 |
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "创建成功",
    "data": {
      "id": 1,
      "name": "小组名称",
      "description": "小组描述",
      "isEnabled": 1,
      "createTime": "2026-03-20 10:30:00",
      "updateTime": "2026-03-20 10:30:00"
    }
  }
  ```
- **注意**
- 只有管理员（super_admin、admin）可以创建小组
- 创建小组时不关联任何项目
- 返回的响应中不包含 projectId 和 relationId

### 5.1.1 批量创建小组

- **接口地址**：`/groups/batch-create`
- **请求方式**：POST
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**：
  | 参数名 | 类型 | 必需 | 说明 |
  |------|------|------|------|
  | prefixName | String | 是 |小组名称前缀 |
  | name | String | 是 | 小组名称，不能为空 |
  | startIndex | String | 是 | 小组数字开始下标 |
  | endIndex | String | 否 | 小组数字结束下标 |
  | description | String | 否 | 小组描述 |
  | isEnabled | Integer | 否 | 是否启用，默认为 1（启用）。0=禁用，1=启用 |
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "创建成功",
    "data": {
      "list": [
        {
          "id": 1,
          "name": "计算机学院小组1",
          "description": "小组描述",
          "isEnabled": 1,
          "createTime": "2026-03-20 10:30:00",
          "updateTime": "2026-03-20 10:30:00"
        },
        {
          "id": 2,
          "name": "计算机学院小组2",
          "description": "小组描述",
          "isEnabled": 1,
          "createTime": "2026-03-20 10:30:00",
          "updateTime": "2026-03-20 10:30:00"
        }
      ]
    }
  }
  ```
- **注意**
- 只有管理员（super_admin、admin）可以创建小组
- 创建小组时不关联任何项目
- 返回的响应中不包含 projectId 和 relationId

### 5.2 将小组关联到项目

- **接口地址**：`/groups/{groupId}/add-to-project`
- **请求方式**：POST
- **请求头**：`Authorization: Bearer {token}`
  **请求参数**
  | 字段 | 类型 | 必需 | 说明 |
  |------|------|------|------|
  | groupId | Long | 是 | 小组 ID |
  | projectId | Long | 是 | 项目 ID |

**响应示例（成功）**

```json
{
  "code": 200,
  "message": "关联成功",
  "data": {
    "id": 1,
    "name": "小组名称",
    "description": "小组描述",
    "projectId": 100,
    "relationId": 50,
    "isEnabled": 1,
    "createTime": "2026-03-20 10:30:00",
    "updateTime": "2026-03-20 10:30:00"
  }
}
```

**可能的错误响应**

```json
{
  "code": 400,
  "message": "小组已经关联到该项目"
}
```

**业务规则**

- 只有管理员可以关联小组到项目
- 小组必须存在
- 项目必须存在且未结束
- 同一小组和项目的组合只能关联一次
- 关联后会清除相关缓存

### 5.3 编辑小组

- **接口地址**：`/groups/{groupId}`
- **请求方式**：PUT
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**：
  | 字段 | 类型 | 必需 | 说明 |
  |------|------|------|------|
  | name | String | 是 | 小组名称，不能为空 |
  | description | String | 否 | 小组描述 |
  | isEnabled | Integer | 否 | 是否启用。0=禁用，1=启用 |

**响应示例（成功）**

```json
{
  "code": 200,
  "message": "编辑成功",
  "data": {
    "id": 1,
    "name": "更新后的小组名称",
    "description": "更新后的小组描述",
    "isEnabled": 1,
    "createTime": "2026-03-20 10:30:00",
    "updateTime": "2026-03-20 10:35:00"
  }
}
```

**业务规则**

- 只有管理员可以编辑小组
- 小组必须存在
- 编辑后会清除该小组关联的所有项目的缓存
- 编辑后会清除相关打分人员的授权项目缓存

---

### 5.4 获取项目受评分的小组列表

- **接口地址**：`/projects/{projectId}/groups`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：`projectId` - 项目ID
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "查询成功",
    "data": [
      {
        "id": 42,
        "name": "小组1",
        "description": "项目2的小组",
        "projectId": 27,
        "relationId": 203,
        "isEnabled": 1,
        "createTime": "2026-03-20 20:39:40",
        "updateTime": "2026-03-20 20:39:40"
      }
    ]
  }
  ```

### 5.5 查询所有被打分组（小组）

- **接口地址**：`/groups`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：`projectId` - 项目ID
- **请求参数**（Query）：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | page | number | 否 | 页码（默认1） |
  | size | number | 否 | 每页条数（默认10） |
  | keyWords | string | 否 | 关键词搜索 |
- **响应示例**：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "id": 1,
        "name": "第一小组",
        "projectId": 1,
        "projectName": "2026中期答辩",
        "createTime": "2026-02-01 10:00:00",
        "updateTime": "2026-02-01 10:00:00"
      },
      {
        "id": 2,
        "name": "第二小组",
        "projectId": 1,
        "projectName": "2026中期答辩",
        "createTime": "2026-02-01 10:05:00",
        "updateTime": "2026-02-01 10:05:00"
      }
    ],
    "total": 2,
    "page": 1,
    "size": 10
  }
}
```

### 5.6 获取小组概览统计

- **接口地址**：`/groups/overview`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **权限要求**：仅超级管理员、管理员可调用
- **请求参数**：无
- **响应示例**：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "totalGroups": 66,
    "activeGroups": 61,
    "totalMembers": 314,
    "avgGroupSize": 4.76
  }
}
```

- **字段说明**：
  | 字段名 | 类型 | 说明 |
  |--------|------|------|
  | totalGroups | number | 小组总数 |
  | activeGroups | number | 启用小组总数 |
  | totalMembers | number | 小组成员总数 |
  | avgGroupSize | number | 平均小组规模（保留2位小数） |

## 6. 打分模块（打分用户）

### 6.1 提交/修改打分

- **接口地址**：`/scoring/records`
- **请求方式**：POST
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | projectId | number | 是 | 项目ID |
  | groupId | number | 是 | 被打分组ID |
  | scores | array | 是 | 各指标打分 |
  | scores[].indicatorId | number | 是 | 指标ID |
  | scores[].score | number | 是 | 打分值 |
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "打分成功",
    "data": {
      "id": 1,
      "projectId": 1,
      "groupId": 1,
      "userId": 2,
      "isMalicious": 0,
      "scores": [
        {
          "indicatorId": 1,
          "score": 4
        },
        {
          "indicatorId": 2,
          "score": 2
        }
      ],
      "totalScore": 6,
      "createTime": "2026-01-27 14:00:00",
      "updateTime": "2026-01-27 14:00:00"
    }
  }
  ```

### 6.2 获取用户对指定小组的打分记录

- **接口地址**：`/scoring/records`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**（Query）：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | projectId | number | 是 | 项目ID |
  | groupId | number | 是 | 小组ID |
- **响应示例**：同上（单个打分记录）

### 6.3 获取用户对项目内所有小组的打分记录

- **接口地址**：`/projects/{projectId}/records`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**（Query）：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | page | number | 否 | 页码（默认1） |
  | size | number | 否 | 每页条数（默认10） |
  | isMalicious | number | 否 | 恶意标记筛选：`0`=非恶意，`1`=恶意；不传表示全部 |
- **响应示例**：

  ```json
  {
    "code": 200,
    "message": "获取成功",
    "data": {
      "list": [
        {
          "id": 1,
          "projectId": 1,
          "groupId": 1,
          "userId": 2,
          "isMalicious": 0,
          "scores": [
            {
              "indicatorId": 1,
              "score": 4
            },
            {
              "indicatorId": 2,
              "score": 2
            }
          ],
          "totalScore": 6,
          "createTime": "2026-01-27 14:00:00",
          "updateTime": "2026-01-27 14:00:00"
        },
        {
          "id": 2,
          "projectId": 1,
          "groupId": 1,
          "userId": 2,
          "isMalicious": 1,
          "scores": [
            {
              "indicatorId": 1,
              "score": 4
            },
            {
              "indicatorId": 2,
              "score": 2
            }
          ],
          "totalScore": 6,
          "createTime": "2026-01-27 14:00:00",
          "updateTime": "2026-01-27 14:00:00"
        }
      ],
      "total": 21,
      "page": 1,
      "size": 10
    }
  }
  ```

### 6.4 获取用户打分概览数据统计

- **接口地址**：`/scoring/overview`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**：无
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "获取成功",
    "data": {
      "totalProjects": 10,
      "ongoingProjects": 5,
      "completedProjects": 3,
      "pendingProjects": 2
    }
  }
  ```
- **字段说明**：
  | 字段名 | 类型 | 说明 |
  |--------|------|------|
  | totalProjects | number | 用户有权打分的项目总数 |
  | ongoingProjects | number | 状态为"进行中"的项目数 |
  | completedProjects | number | 用户已完成打分的项目数（所有小组都评分过） |
  | pendingProjects | number | 用户待完成打分的项目数（存在未评的小组） |
- **业务逻辑说明**：
  - 基于用户被授权的项目列表
  - `ongoingProjects` = 状态为ongoing的项目数
  - `completedProjects` = 用户对所有小组都完成打分的项目数
  - `pendingProjects` = 用户对至少有一个小组未打分的项目数

## 7. 统计与导出模块

### 7.1 获取项目打分统计

- **接口地址**：`/projects/{projectId}/statistics`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：`projectId` - 项目ID
- **说明**：该接口现已支持**评委标准化 + 异常检测 + 原始/处理后双展示**。其中 `averageScore` 为兼容旧前端保留字段，语义等同于 `processedAverageScore`。
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "查询成功",
    "data": {
      "groupAverage": [
        {
          "groupId": 1,
          "groupName": "第一小组",
          "averageScore": 4.63,
          "rawAverageScore": 4.41,
          "normalizedAverageScore": 4.58,
          "processedAverageScore": 4.63,
          "abnormalCount": 1,
          "sampleSize": 6,
          "validSampleSize": 5
        }
      ],
      "indicatorAverage": [
        {
          "indicatorId": 1,
          "indicatorName": "复杂程度",
          "averageScore": 4.27,
          "rawAverageScore": 4.08,
          "normalizedAverageScore": 4.21,
          "processedAverageScore": 4.27,
          "abnormalCount": 1,
          "sampleSize": 6,
          "validSampleSize": 5
        }
      ],
      "scorerDistribution": [
        {
          "userId": 2,
          "userName": "打分员01",
          "scoreRange": "4-6分",
          "count": 3
        }
      ]
    }
  }
  ```
- **字段说明**：
  | 字段名 | 类型 | 说明 |
  |--------|------|------|
  | groupAverage | array | 小组维度统计结果，按处理后均分倒序返回 |
  | groupAverage[].groupId | number | 小组ID |
  | groupAverage[].groupName | string | 小组名称 |
  | groupAverage[].averageScore | number | 兼容字段，等于 `processedAverageScore` |
  | groupAverage[].rawAverageScore | number | 原始平均分，未做标准化和异常剔除 |
  | groupAverage[].normalizedAverageScore | number | 标准化后平均分，已消除评委整体偏严/偏松影响，但未剔除异常值 |
  | groupAverage[].processedAverageScore | number | 处理后平均分，基于标准化结果剔除恶意评分后计算 |
  | groupAverage[].abnormalCount | number | 判定为恶意评分的条数（按项目规则） |
  | groupAverage[].sampleSize | number | 原始样本总数 |
  | groupAverage[].validSampleSize | number | 剔除异常值后的有效样本数 |
  | indicatorAverage | array | 指标维度统计结果，按处理后均分倒序返回 |
  | indicatorAverage[].indicatorId | number | 指标ID |
  | indicatorAverage[].indicatorName | string | 指标名称 |
  | indicatorAverage[].averageScore | number | 兼容字段，等于 `processedAverageScore` |
  | indicatorAverage[].rawAverageScore | number | 原始平均分，未做标准化和异常剔除 |
  | indicatorAverage[].normalizedAverageScore | number | 标准化后平均分，已消除评委整体偏严/偏松影响，但未剔除异常值 |
  | indicatorAverage[].processedAverageScore | number | 处理后平均分，基于标准化结果剔除恶意评分后计算 |
  | indicatorAverage[].abnormalCount | number | 判定为恶意评分的条数（按项目规则） |
  | indicatorAverage[].sampleSize | number | 原始样本总数 |
  | indicatorAverage[].validSampleSize | number | 剔除异常值后的有效样本数 |
  | scorerDistribution | array | 打分用户分布统计 |
  | scorerDistribution[].userId | number | 打分用户ID |
  | scorerDistribution[].userName | string | 打分用户名称 |
  | scorerDistribution[].scoreRange | string | 总分区间，当前可能值为 `0-2分`、`2-4分`、`4-6分`、`6-8分`、`8-10分`、`其他` |
  | scorerDistribution[].count | number | 该用户落在当前分值区间的记录数 |
- **统计逻辑说明**：
  - **原始平均分**：直接基于原始打分求平均。
  - **标准化平均分**：按评委在当前统计范围内的整体均值做中心化处理，公式为 `adjusted = raw - scorerMean + overallMean`。
  - **恶意判定规则**（项目级）：
    - `AUTO`：使用 MAD 低分单侧规则 `x < median - 3 × 1.4826 × MAD` 标记恶意评分。
    - `THRESHOLD`：使用项目配置阈值区间，`x < lower` 或 `x > upper` 标记恶意评分。
  - **处理后平均分**：先按项目规则标记恶意评分，再在**非恶意样本**上计算标准化后的平均值。
  - **降级策略**：`AUTO` 模式在样本量过少时会跳过异常检测，仅保留标准化结果。


### 7.1.1 获取项目内指定小组的指标平均得分明细

- **接口地址**：`/projects/{projectId}/statistics/groups/{groupId}`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | projectId | number | 是 | 项目ID |
  | groupId | number | 是 | 小组ID |
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "查询成功",
    "data": {
      "groupId": 1,
      "groupName": "第一小组",
      "indicatorAverage": [
        {
          "indicatorId": 1,
          "indicatorName": "复杂程度",
          "averageScore": 4.35
        },
        {
          "indicatorId": 2,
          "indicatorName": "创新性",
          "averageScore": 3.92
        }
      ]
    }
  }
  ```
- **字段说明**：
  | 字段名 | 类型 | 说明 |
  |--------|------|------|
  | groupId | number | 小组ID |
  | groupName | string | 小组名称 |
  | indicatorAverage | array | 当前小组各指标平均得分 |
  | indicatorAverage[].indicatorId | number | 指标ID |
  | indicatorAverage[].indicatorName | string | 指标名称 |
  | indicatorAverage[].averageScore | number | 指标平均得分 |

### 7.2 获取平台全局统计数据

- **接口地址**：`/statistics/platform`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **权限要求**：仅超级管理员、管理员可调用
- **请求参数**：无
- **响应示例**：

  ```json
  {
    "code": 200,
    "message": "查询成功",
    "data": {
      "dates": ["2026-03-17", "2026-03-18", "..."],
      "projectTrend": [500, 800, 750, "..."],
      "scoreTrend": [12, 8, 15, "..."],
      "averageScoreTrend": [4.56, 4.61, 4.58, "..."]
    }
  }
  ```

- **字段说明**：
  | 字段名 | 类型 | 说明 |
  |--------|------|------|
  | totalProjects | number | 累计项目总数 |
  | totalScores | number | 本周新增评分数 |
  | averageScore | number | 平台平均得分（具体满分由各项目的打分标准决定） |
  <!-- | projectTrend | array | 过去30天项目数量趋势 |
  | scoreTrend | array | 过去30天平均得分趋势 |
  | rateTrend | array | 过去30天项目通过率趋势 | -->

- **说明**：
  - `averageScore` 的含义为平台所有已评分项目的平均得分
  - 平均得分的满分值由各项目关联的打分标准决定，不同项目可能有不同的满分
  - 建议后端在计算时对不同满分的项目进行归一化处理或在响应中返回标准满分信息

### 7.3 导出项目打分数据

- **接口地址**：`/projects/{projectId}/export`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：`projectId` - 项目ID
- **请求参数**（Query）：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | projectId | string | 是 | 项目唯一标识 ID |
  | format | string | 否 | 导出格式（excel/csv，默认excel） |
- **响应**：文件流（直接下载）
- **说明**：
  - 导出文件包含项目的所有打分记录、小组统计、指标统计等数据
  - 文件名格式：`项目名_打分统计_{时间戳}.xlsx`

### 7.4 导出当前项目小组的打分数据

- **接口地址**：`/projects/{projectId}/export/group-indicator-items`
- **请求方式**：GET
- **请求参数**（Query）：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | projectId | string | 是 | 项目唯一标识 ID |
  | format | string | 否 | 导出格式（excel/csv，默认excel） |

- **响应头**：  
  `Content-Type` 根据请求格式返回对应 MIME 类型：
  - `excel` → `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  - `csv` → `text/csv; charset=utf-8`
- **响应体**：文件二进制流，客户端需作为文件下载。
- **说明**：
  - 导出文件包含项目的所有打分记录、小组统计、指标统计等数据
  - 文件名格式：`项目名_打分统计_{时间戳}.xlsx`

- **导出数据列说明**

| 列名         | 说明                                         |
| ------------ | -------------------------------------------- |
| 项目名称     | 当前项目名称                                 |
| 评分标准     | 当前项目使用的评分标准名称                   |
| 小组名称     | 小组名称                                     |
| 评分项       | 评分项名称                                   |
| 每项得分明细 | 该小组在该评分项下的所有得分，用英文逗号分隔 |
| 平均分       | 该小组在该评分项下的得分平均值               |
| 评分次数     | 该小组在该评分项下被评分的总次数             |

- **错误响应**

| HTTP 状态码 | 错误码                  | 说明                         |
| ----------- | ----------------------- | ---------------------------- |
| `400`       | `INVALID_FORMAT`        | `format` 参数值不合法        |
| `404`       | `PROJECT_NOT_FOUND`     | 指定项目不存在               |
| `500`       | `INTERNAL_SERVER_ERROR` | 服务器内部错误，生成文件失败 |

- **请求示例**
  导出为 Excel 文件

```http
GET /projects/114514/export/group-indicator-items?format=excel
```

### 7.5 导出项目异常打分记录（方案 B）

- **接口地址**：`/projects/{projectId}/export/abnormal-scores`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | projectId | number | 是 | 项目ID |
- **请求参数**：无
- **响应**：Excel 文件流（直接下载）
- **说明**：
  - 仅导出被判定为异常的打分记录。
  - 判定规则与统计接口保持一致，按项目策略执行：
    - `AUTO`：`x < median - 3 × 1.4826 × MAD`
    - `THRESHOLD`：`x < lower` 或 `x > upper`
  - 当项目不存在异常记录时，返回错误提示：`当前项目暂无被标记为异常的打分记录`。
  - 文件名格式：`项目名_异常打分记录_{时间戳}.xlsx`。

- **导出数据列说明**

| 列名         | 说明 |
| ------------ | ---- |
| 项目名称     | 当前项目名称 |
| 记录ID       | 打分记录ID（`scoring_record.id`） |
| 小组ID       | 被评分小组ID |
| 小组名称     | 被评分小组名称 |
| 评委ID       | 打分用户ID |
| 评委姓名     | 打分用户姓名 |
| 原始总分     | 打分记录原始总分（未标准化） |
| 标准化后分数 | 按评委偏严/偏松校正后的分数 |
| 偏差绝对值   | `AUTO` 模式为 `|原始总分 - 中位数|`；`THRESHOLD` 模式为 `-` |
| 异常阈值     | `AUTO` 模式为 MAD 距离阈值；`THRESHOLD` 模式为 `-` |
| 打分时间     | 打分记录创建时间 |
| 异常规则     | 具体触发规则文本（AUTO 或 THRESHOLD） |

- **请求示例**

```http
GET /projects/114514/export/abnormal-scores
```

## 8. 评审组管理模块（管理员）

### 8.1 创建评审组

- **接口地址**：`/reviewer-groups`
- **请求方式**：POST
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |--------|------|------|------|
  | name | string | 是 | 评审组名称 |
  | description | string | 否 | 评审组描述 |
  | isEnabled | boolean | 否 | 是否启用，默认true |
  | memberIds | array | 是 | 评审组成员ID列表 |
- **响应**：

```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 1,
    "name": "中期答辩评审组",
    "description": "负责2026年中期答辩评审",
    "creatorId": 1,
    "isEnabled": true,
    "memberIds": [2, 3, 4, 5],
    "createTime": "2026-02-24 21:30:00",
    "updateTime": "2026-02-24 21:30:00"
  }
}
```

### 8.2 获取评审组列表

- **接口地址**：`/reviewer-groups`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- - **请求参数**：
    | 参数名 | 类型 | 必填 | 说明 |
    |--------|------|------|------|
    | keyWords | string | 否 | 关键词搜索 |
- **响应示例**：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 1,
      "name": "中期答辩评审组",
      "description": "负责2026年中期答辩评审",
      "creatorId": 1,
      "isEnabled": true,
      "memberIds": [2, 3, 4, 5],
      "createTime": "2026-02-24 21:30:00",
      "updateTime": "2026-02-24 21:30:00"
    },
    {
      "id": 2,
      "name": "期末答辩评审组",
      "description": "负责2026年期末答辩评审",
      "creatorId": 1,
      "isEnabled": true,
      "memberIds": [3, 6, 7],
      "createTime": "2026-02-24 21:35:00",
      "updateTime": "2026-02-24 21:35:00"
    }
  ]
}
```

### 8.3 获取评审组详情

- **接口地址**：`/reviewer-groups/{groupId}`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：`groupId` - 评审组ID
- **响应示例**：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "id": 1,
    "name": "中期答辩评审组",
    "description": "负责2026年中期答辩评审",
    "creatorId": 1,
    "isEnabled": true,
    "memberIds": [2, 3, 4, 5],
    "createTime": "2026-02-24 21:30:00",
    "updateTime": "2026-02-24 21:30:00"
  }
}
```

### 8.4 编辑评审组

- **接口地址**：`/reviewer-groups/{groupId}`
- **请求方式**：PUT
- **请求头**：`Authorization: Bearer {token}`
- **路径参数**：`groupId` - 评审组ID
- - **请求参数**：
    | 参数名 | 类型 | 必填 | 说明 |
    |--------|------|------|------|
    | name | string | 是 | 评审组名称 |
    | description | string | 否 | 评审组描述 |
    | isEnabled | boolean | 否 | 是否启用，默认true |
    | memberIds | array | 是 | 评审组成员ID列表 |
- **响应示例**：

```json
{
  "code": 200,
  "message": "编辑成功",
  "data": {
    "id": 1,
    "name": "2026中期答辩评审组",
    "description": "负责2026年中期答辩评审",
    "creatorId": 1,
    "isEnabled": true,
    "memberIds": [2, 3, 4, 5, 6],
    "createTime": "2026-02-24 21:30:00",
    "updateTime": "2026-03-03 19:45:00"
  }
}
```

### 8.5 获取评审组概览统计

- **接口地址**：`/reviewer-groups/overview`
- **请求方式**：GET
- **请求头**：`Authorization: Bearer {token}`
- **权限要求**：仅超级管理员、管理员可调用
- **请求参数**：无
- **响应示例**：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "totalGroups": 18,
    "activeGroups": 16,
    "totalMembers": 92,
    "avgGroupSize": 5.11
  }
}
```

- **字段说明**：
  | 字段名 | 类型 | 说明 |
  |--------|------|------|
  | totalGroups | number | 评审组总数 |
  | activeGroups | number | 启用评审组总数 |
  | totalMembers | number | 评审组成员总数 |
  | avgGroupSize | number | 平均评审组规模（保留2位小数） |

---

## 📊 统计与分析体系完整指南

### 系统架构（方案 C：全局 & 项目统计两层模式）

该系统提供两层统计能力，为管理员提供完整的数据分析体验：

```
┌─────────────────────────────────────────────────────────┐
│  管理员仪表板                                           │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ 层级 1：平台全局统计 (/admin/statistic)           │ │
│  │ - 调用 API：GET /statistics/platform               │ │
│  │ - 展示：整体趋势、关键指标、洞察建议              │ │
│  │ - 用户：超级管理员/管理员（全局概览）            │ │
│  ├───────────────────────────────────────────────────┤ │
│  │ ✓ 核心指标卡片：总项目数、新增评分、平均得分    │ │
│  │ ✓ 30天趋势图表：项目数趋势、评分趋势、通过率    │ │
│  │ ✓ 关键洞察 & 优化建议                            │ │
│  └───────────────────────────────────────────────────┘ │
│                           ↓                             │
│  ┌───────────────────────────────────────────────────┐ │
│  │ 层级 2：项目级统计 (/admin/project/statistic)     │ │
│  │ - 路由1：项目列表页（选择项目）                  │ │
│  │ - 路由2：项目详情页（查看统计）                  │ │
│  │ - 调用 API：GET /projects/{projectId}/statistics │ │
│  │ - 展示：小组成绩、指标分析、评分分布             │ │
│  │ - 支持数据导出：GET /projects/{projectId}/export │ │
│  │ - 异常记录导出：GET /projects/{projectId}/export/abnormal-scores │ │
│  ├───────────────────────────────────────────────────┤ │
│  │ ✓ 小组统计：各小组平均得分、评分进度            │ │
│  │ ✓ 指标分析：各评分指标的平均得分                │ │
│  │ ✓ 评分分布：评分人的打分情况统计                │ │
│  │ ✓ 数据导出：支持 Excel/CSV 格式导出            │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### API 调用流程示例

**场景 1：浏览平台统计大盘**

```bash
GET /api/v1/statistics/platform
✓ 获取平台全局汇总数据
✓ 用于展示整体态势和趋势分析
```

**场景 2：查看特定项目统计**

```bash
1. GET /api/v1/projects                           # 列表选择项目
2. GET /api/v1/projects/{projectId}/statistics    # 获取该项目统计
3. GET /api/v1/projects/{projectId}/statistics/groups/{groupId}  # 可选：查看小组指标平均分明细
4. GET /api/v1/projects/{projectId}/export        # 可选：导出数据
5. GET /api/v1/projects/{projectId}/export/abnormal-scores  # 可选：导出异常打分记录
```

### 前端页面路由对应关系

| 页面功能     | 路由路径                              | 调用 API                                                | 说明                   |
| ------------ | ------------------------------------- | ------------------------------------------------------- | ---------------------- |
| 平台统计大盘 | `/admin/statistic`                    | `GET /statistics/platform`                              | 全局汇总数据概览       |
| 项目统计列表 | `/admin/project/statistic`            | `GET /projects`                                         | 项目选择界面           |
| 项目统计详情 | `/admin/project/statistic/:projectId` | `GET /projects/{projectId}/statistics`                  | 单项目详细统计         |
| 小组得分明细 | （在详情页内弹窗）                    | `GET /projects/{projectId}/statistics/groups/{groupId}` | 查看单小组按指标平均分 |
| 数据导出     | （在详情页内）                        | `GET /projects/{projectId}/export`                      | 导出项目统计数据       |
| 异常导出     | （在详情页内）                        | `GET /projects/{projectId}/export/abnormal-scores`      | 导出异常打分记录       |

### 实现建议

**后端需要实现的 API：**

1. ✅ **平台全局统计** - `GET /statistics/platform`
   - 汇总所有项目数据
   - 计算平台级别的平均分、驳回率等
   - 返回30天趋势数据

2. ✅ **项目统计** - `GET /projects/{projectId}/statistics`（已有）
   - 返回小组平均分、指标平均分、评分分布等

3. ✅ **小组指标明细统计** - `GET /projects/{projectId}/statistics/groups/{groupId}`
   - 返回指定小组的指标平均分明细
   - 用于详情页“查看明细”弹窗

4. ✅ **导出功能** - `GET /projects/{projectId}/export`（需完善）
   - 支持 Excel/CSV 格式
   - 返回文件流供客户端下载

5. ✅ **异常打分导出（方案 B）** - `GET /projects/{projectId}/export/abnormal-scores`
  - 导出 MAD 规则标记的异常记录
  - 返回 Excel 文件流供客户端下载

---

## 总结

1. **权限控制**：接口权限严格区分超级管理员、管理员、打分用户、普通用户，核心操作需验证token和角色权限。
2. **核心流程**：管理员创建项目→配置打分标准→关联小组和打分用户→打分用户完成打分→管理员查看统计/导出数据。
3. **状态控制**：项目结束后禁止新增/修改打分记录，保证数据的最终性；通过`isEnabled`开关控制项目是否可用。
4. **统计体系**：采用两层模式提供完整的数据分析能力
   - **层级 1 平台全局统计**：汇总所有项目的评分数据，展示平台级别的关键指标和趋势
   - **层级 2 项目级统计**：单个项目的详细统计，包括小组成绩、指标分析、评分分布等
   - **特别说明**：平均得分的满分值由各项目的打分标准决定，不同项目可能有不同的满分，后端应支持灵活的标准化处理

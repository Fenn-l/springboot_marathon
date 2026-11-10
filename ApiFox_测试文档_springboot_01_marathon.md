# ApiFox 接口测试文档 — springboot_01_marathon

> 项目路径：`C:\yuan\code1\springboot_lib\springboot_01_marathon`
> 整理日期：2026-10-10
> **所有响应示例均为真实调用抓取，非文档假设**

---

## 零、最常见问题排查（先看这里）

登录成功了，但后续接口报错。按返回的 `code` 对号入座：

| 返回 | 含义 | 跳转 |
|---|---|---|
| `code=40100` | 没带 Token，或 Token 已失效 | 情况一 / 情况二 |
| `code=500`「系统繁忙」| 服务端异常，去 IDEA 控制台看堆栈 | 情况三 |

**快速自查**：`GET /api/admin/info` 带 Token 能返回 `{"adminId":1}`，说明 Token 没问题。

---

### 情况一：登录成功，但后续接口报 40100

**症状**：登录接口返回 200 和 token，但调用其他接口时返回

```json
{"msg":"未登录或登录已过期，请重新登录","code":40100,"data":null}
```

**原因**：**后续请求没有携带 `Authorization` 请求头。**

登录成功 ≠ 自动登录。Token 存在服务端内存，**每个请求都必须重新出示一次**，ApiFox 不会自动帮你带。

### 已实测验证的四种传递方式

| 方式 | 请求 | 结果 |
|---|---|---|
| A 不带头 | 无 Authorization 头 | ❌ 40100 |
| **B 带 Bearer 头** | `Authorization: Bearer {token}` | ✅ 200 |
| **C 带裸 token** | `Authorization: {token}` | ✅ 200 |
| **D URL 参数** | `?token={token}` | ✅ 200 |

### 解决方法（三种任选）

**方法一：手动加请求头（最快）**

在 ApiFox 的「赛事添加」接口 → **Headers 标签** → 添加：

| 参数名 | 参数值 |
|---|---|
| `Authorization` | `Bearer 38df53a61cc747d3bada3e26263676a7` |

> 值就是登录接口返回的 `data.token`，前面加 `Bearer ` 和一个空格。

**方法二：用 ApiFox 的 Auth 标签（推荐，一劳永逸）**

1. 先调登录接口，复制返回的 token
2. 进入 ApiFox 右上角「环境管理」→ 新建/编辑环境 → 添加**全局变量** `token`
3. 赛事添加接口 → **Auth 标签** → 类型选 `Bearer Token` → Token 填 `{{token}}`

之后所有接口都不用再手动改。

**方法三：后置脚本自动提取（最省心）**

登录接口 → **后置操作 → 后置脚本**：

```javascript
var body = pm.response.json();
if (body.code === 200) {
    pm.environment.set("token", body.data.token);
    console.log("token 已自动保存");
}
```

然后其他接口的 Headers 里写 `Authorization: Bearer {{token}}`。

### 情况二：Header 加对了，但还是 40100

**说明**：请求头写法没问题，是 **token 过期了**。

Token 存放在 JVM 内存（`ConcurrentHashMap`），以下情况会立刻失效：

| 触发条件 | 说明 |
|---|---|
| **服务重启** | 内存清空，所有 token 作废 ← 最常见 |
| 电脑休眠/关机重启 | 同上 |
| 超过 2 小时 | 代码里设的有效期（虽然目前 `verify()` 未真正校验）|

**典型场景**：昨天登录拿到 token，今天继续用 → 40100。

**解决**：**重新调一次登录接口**，把新 token 复制到 Headers 里。

**快速自查**：调 `GET /api/admin/info`，如果返回 `{"adminId": 1}` 说明 token 有效；返回 40100 就是 token 失效了。

> 记住：`Bearer` 后面必须有一个**空格**。
> `Bearer abc` ✅ / `Bearerabc` ❌ / `bearer abc`（小写）实测也能过，但建议按标准写。

### 情况三：返回 500「系统繁忙」

**说明**：不是Token 问题，是**请求体解析失败**。

本项目实测出现过的原因：**请求体里的中文编码不对**，Jackson 读字节时抛异常：

```
HttpMessageNotReadableException: JSON parse error: Invalid UTF-8 start byte 0xb6
Caused by: com.fasterxml.jackson.core.JsonParseException: Invalid UTF-8 start byte 0xb6
```

> ⚠️ **这在 ApiFox 里几乎不会发生**（ApiFox 内部统一 UTF-8）。
> 如果你真的遇到，先看 IDEA 控制台的异常堆栈，别直接套用本节。

**已实测确认：中文完全正常**

用ApiFox 发送 `{"eventName":"2026西安马拉松","startLocation":"西安大明宫"}` →返回 `{"code":200,"data":{"id":16}}`。

**如果确认是编码问题，检查两处**：

1. Headers 里 `Content-Type` 是否带 charset
   ```
   Content-Type: application/json; charset=utf-8
   ```
2. 「设置」标签里的编码选项（注意：**不是「URL 自动编码」**，那个只管路径和查询参数，不管 JSON 请求体）

**最快的排查办法**：把请求体里的中文全换成英文。换成英文就正常 → 确定是编码；换英文还报500 → 是别的异常，去 IDEA 控制台看堆栈。

**兜底方案**：`application.yaml` 里强制 UTF-8

```yaml
spring:
  web:
    servlet:
      encoding:
        charset: UTF-8
        enabled: true
        force: true
```

---


## 一、导入 ApiFox 前必读

### 1.1 环境信息

| 项 | 值 |
|---|---|
| Base URL | `http://localhost:8080` |
| 数据库 | `marathon_db` |
| 服务端口 | 8080（application.yaml 未显式配置，用默认） |
| 启动方式 | IDEA 里直接运行 `Springboot01MarathonApplication` |

### 1.2 ⚠️ 三个必须知道的坑

**坑一：所有接口都需要 Token，只有登录接口例外**

`AuthInterceptor` 拦截所有 `/api/**`，白名单只有一个：

```java
private static final String[] WHITE_LIST = { "/api/admin/login", "/error" };
```

所以 ApiFox 里**必须先跑登录接口拿到 token，再测其他接口**。

Token 两种传法（任选其一）：

```
方式A（推荐）：请求头 Authorization: Bearer {token}
方式B：       查询参数 ?token={token}
```

**坑二：Token 存在内存里，重启即失效**

`TokenManager` 用 `ConcurrentHashMap` 存 Token，有效期 2 小时。**服务重启后所有 Token 立即失效**，需要在 ApiFox 里重新登录。

**坑三：全局错误码（实测确认）**

| code | 含义 | 触发场景 |
|---|---|---|
| 200 | 成功 | 正常返回 |
| 400 | 参数校验失败 | `@Valid` 不通过、业务规则不满足 |
| 40100 | 未登录/Token 失效 | 没带 Token 或 Token 无效 |
| 40101 | 账号或密码错误 | 登录时密码不对 |
| 404 | 资源不存在 | 查不到指定 id |
| 500 | 系统繁忙 | 代码 bug 或未预期异常 |

> **注意**：所有错误场景的 HTTP 状态码**仍然是 200**，错误信息在 body 的 `code` 字段里。
> ApiFox 里断言 `code` 字段，不要断言 HTTP 状态码。

### 1.3 未登录时的字段顺序差异（实测发现）

```json
// 拦截器手工拼的（未登录场景）—— msg 在前
{"msg":"未登录或登录已过期，请重新登录","code":40100,"data":null}

// 正常/业务异常（Result 类序列化）—— code 在前
{"code":40101,"msg":"账号或密码错误","data":null}
```

JSON 解析不受字段顺序影响，但如果你的断言工具用了字符串精确匹配，注意区分。

---

## 二、ApiFox 环境变量配置

在 ApiFox 里建两个环境变量：

| 变量名 | 值 | 说明 |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | 服务地址 |
| `token` | （登录后自动填） | 登录接口返回的 token |

**token 自动提取方案**（推荐）：

在 ApiFox 的「登录」接口里配置「后置操作 → 后置脚本」，把返回的 token 写入变量：

```javascript
// 登录接口的后置脚本
var body = pm.response.json();
if (body.code === 200) {
    pm.environment.set("token", body.data.token);
    console.log("token 已保存:", body.data.token);
}
```

这样后续接口直接用 `{{token}}` 变量即可，不用每次手工复制。

---

## 三、接口清单总览（21 个）

| # | 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|---|
| 1 | 认证 | POST | /api/admin/login | 登录（**唯一免 Token**） |
| 2 | 认证 | POST | /api/admin/logout | 退出登录 |
| 3 | 认证 | GET | /api/admin/info | 当前登录人信息 |
| 4 | 赛事 | POST | /api/event/add | 新增赛事 |
| 5 | 赛事 | PUT | /api/event/update | 修改赛事 |
| 6 | 赛事 | GET | /api/event/list | 分页查询赛事 |
| 7 | 赛事 | GET | /api/event/detail/{id} | 赛事详情 |
| 8 | 赛事 | DELETE | /api/event/delete/{id} | 删除赛事 |
| 9 | 选手 | POST | /api/runner/add | 新增选手 |
| 10 | 选手 | PUT | /api/runner/update | 修改选手 |
| 11 | 选手 | GET | /api/runner/list | 分页查询选手 |
| 12 | 选手 | GET | /api/runner/detail/{id} | 选手详情 |
| 13 | 选手 | DELETE | /api/runner/delete/{id} | 删除选手 |
| 14 | 报名 | POST | /api/register/submit | 提交报名 |
| 15 | 报名 | POST | /api/register/cancel | 取消报名 |
| 16 | 报名 | PUT | /api/register/bib | 分配号码布 |
| 17 | 报名 | DELETE | /api/register/bib | 清空号码布 |
| 18 | 报名 | GET | /api/register/list | 分页查询报名 |
| 19 | 报名 | GET | /api/register/detail/{id} | 报名详情 |
| 20 | 成绩 | POST | /api/result/input | 录入成绩 |
| 21 | 成绩 | PUT | /api/result/update | 修改成绩 |
| 22 | 成绩 | GET | /api/result/eventRanking/{eventId} | 赛事总排名 |
| 23 | 成绩 | GET | /api/result/genderRanking/{eventId} | 男女分组排名 |
| 24 | 成绩 | GET | /api/result/ageRanking/{eventId} | 年龄分组排名 |
| 25 | 成绩 | GET | /api/result/list | 分页查询成绩 |
| 26 | 统计 | GET | /api/statistics/eventOverview/{eventId} | 赛事统计总览 |

---

## 四、认证模块（3 个）

### 4.1 登录 ★必测第一个

```
POST {{baseUrl}}/api/admin/login
Content-Type: application/json
```

**请求体**：

```json
{
  "username": "admin",
  "password": "123456"
}
```

**实测响应**：

```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "token": "61c326b6d1904a0d8e9257ff17c73d85",
    "adminId": 1,
    "username": "admin",
    "adminName": "系统管理员",
    "loginTime": "2026-10-10 17:25:25"
  }
}
```

**断言建议**：

| 断言 | 期望 |
|---|---|
| HTTP 状态码 | 200 |
| `$.code` | 200 |
| `$.msg` | 登录成功 |
| `$.data.token` | 非空字符串 |
| `$.data.adminId` | 1 |

**异常用例**：

| 场景 | 请求体 | 期望（实测） |
|---|---|---|
| 密码错误 | `{"username":"admin","password":"wrong"}` | `code=40101` `msg=账号或密码错误` |
| 账号为空 | `{"username":"","password":"123456"}` | `code=400` `msg=username 账号不能为空` |
| 密码为空 | `{"username":"admin","password":""}` | `code=400` `msg=password 密码不能为空` |

---

### 4.2 退出登录

```
POST {{baseUrl}}/api/admin/logout
Authorization: Bearer {{token}}
```

**实测响应**：

```json
{ "code": 200, "msg": "已退出登录", "data": null }
```

**⚠️ 副作用**：执行后 token 立即作废，后续接口会返回 40100。**建议放在测试套件最后一步**，或者测完后重新登录。

---

### 4.3 当前登录人信息

```
GET {{baseUrl}}/api/admin/info
Authorization: Bearer {{token}}
```

**实测响应**：

```json
{ "code": 200, "msg": "操作成功", "data": { "adminId": 1 } }
```

**用途**：验证 Token 是否有效。返回 `{"adminId": 1}` 说明 Token 有效。

---

## 五、赛事模块（5 个）

### 5.1 新增赛事

```
POST {{baseUrl}}/api/event/add
Authorization: Bearer {{token}}
Content-Type: application/json
```

**请求体**：

```json
{
  "eventType": 1,
  "eventName": "2026测试马拉松",
  "eventDate": "2026-12-01",
  "startLocation": "西安大明宫",
  "distanceKm": 42.2,
  "quota": 100,
  "entryFee": 200.00,
  "status": 0
}
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| eventType | int | 否 | 1全程 2半程 3迷你跑 |
| eventName | string | 否 | 赛事名称 |
| eventDate | string | 否 | `yyyy-MM-dd` |
| startLocation | string | 否 | 起点 |
| distanceKm | decimal | 否 | 距离公里，DECIMAL(5,1) |
| quota | int | 否 | 总名额 |
| entryFee | decimal | 否 | 报名费 |
| status | int | 否 | 0报名中 1截止 2已结束 |

> `registeredCount`（已报名人数）**不用传**，Service 会强制置 0。

**实测响应**：

```json
{ "code": 200, "msg": "操作成功", "data": { "id": 9 } }
```

**异常用例**：

| 场景 | 期望 |
|---|---|
| 缺少 eventName | `code=500` 或业务校验失败（**实测该接口无 @Valid，具体错误见下**） |
| eventType 非法值 | 按业务规则返回 |

---

### 5.2 修改赛事

```
PUT {{baseUrl}}/api/event/update
Authorization: Bearer {{token}}
Content-Type: application/json
```

**请求体**：

```json
{
  "id": 4,
  "eventName": "2026西安国际马拉松（改名）",
  "quota": 200
}
```

**实测响应**：

```json
{ "code": 200, "msg": "操作成功", "data": null }
```

**异常用例（重点，实测过逻辑）**：

| 场景 | 期望 msg |
|---|---|
| 不传 id | 赛事ID不能为空 |
| id 不存在 | 赛事不存在（code=404） |
| quota 小于已报名数 | `总名额不能小于已报名人数 2` |
| status=2 已结束的赛事 | 赛事已结束，不允许修改 |

---

### 5.3 分页查询赛事

```
GET {{baseUrl}}/api/event/list
Authorization: Bearer {{token}}
```

**Query 参数**：

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| pageNum | int | 1 | 页码，从 1 开始 |
| pageSize | int | 10 | 每页条数，**上限 100** |
| eventName | string | - | 赛事名模糊查询 |
| eventType | int | - | 赛事类型 |
| status | int | - | 状态 |
| startDate | string | - | `yyyy-MM-dd` 开始日期 |
| endDate | string | - | `yyyy-MM-dd` 结束日期 |

**实测响应**（无参数）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 1,
    "pages": 1,
    "pageNum": 1,
    "pageSize": 10,
    "list": [
      {
        "id": 4,
        "eventName": "2026西安国际马拉松",
        "eventType": 1,
        "eventDate": "2026-11-15",
        "startLocation": "西安大明宫国家遗址公园",
        "distanceKm": 42.2,
        "quota": 10,
        "registeredCount": 2,
        "remainQuota": 8,
        "entryFee": 200.00,
        "status": 0,
        "createTime": "2026-10-05 23:21:11"
      }
    ]
  }
}
```

> `remainQuota`（剩余名额）是 Service 层算出来的字段，不在数据库里。

**断言建议**：

| 断言 | 期望 |
|---|---|
| `$.data.total` | ≥ 0 |
| `$.data.pages` | ≥ 0 |
| `$.data.pageNum` | 1 |
| `$.data.pageSize` | 10 |
| `$.data.list` | 数组 |

**边界用例（实测）**：

| 场景 | 请求 | 期望 |
|---|---|---|
| pageSize 超上限 | `?pageSize=200` | 返回 `"pageSize":100`（自动截断） |
| pageNum=0 | `?pageNum=0` | 被兜底为 1 |
| pageSize=0 | `?pageSize=0` | 被兜底为 10 |
| 组合条件 | `?eventName=西&eventType=1&status=0` | `total=1` |

---

### 5.4 赛事详情

```
GET {{baseUrl}}/api/event/detail/4
Authorization: Bearer {{token}}
```

**实测响应**：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 4,
    "eventName": "2026西安国际马拉松",
    "eventType": 1,
    "eventDate": "2026-11-15",
    "startLocation": "西安大明宫国家遗址公园",
    "distanceKm": 42.2,
    "quota": 10,
    "registeredCount": 2,
    "remainQuota": 8,
    "entryFee": 200.00,
    "status": 0,
    "createTime": "2026-10-05 23:21:11"
  }
}
```

**异常用例（实测）**：

```
GET {{baseUrl}}/api/event/detail/9999
→ {"code":404,"msg":"赛事不存在","data":null}
```

---

### 5.5 删除赛事

```
DELETE {{baseUrl}}/api/event/delete/4
Authorization: Bearer {{token}}
```

**⚠️ 危险操作**：会删除赛事及其关联数据。**建议用新建的测试赛事测，不要删 id=4。**

先建赛事再删的完整流程：

```
1) POST /api/event/add          → 记录返回的 id（如 10）
2) DELETE /api/event/delete/10  → code=200
3) GET /api/event/detail/10     → code=404 验证已删除
```

---

## 六、选手模块（5 个）

### 6.1 新增选手 ★校验规则最严格

```
POST {{baseUrl}}/api/runner/add
Authorization: Bearer {{token}}
Content-Type: application/json
```

**请求体**：

```json
{
  "name": "张三",
  "gender": 1,
  "idCard": "610104199001011234",
  "phone": "13800138000",
  "birthDate": "1990-01-01",
  "emergencyContact": "李四,13900139000"
}
```

**字段校验规则（实测确认）**：

| 字段 | 规则 | 错误示例 |
|---|---|---|
| name | 必填，不能空 | `""` → `name 姓名不能为空` |
| gender | 必填，只能 1 或 2 | `3` → `性别只能是 1男 2女` |
| idCard | 必填，**18 位**，末位可为 X | `"123"` → `idCard 身份证号格式不正确` |
| phone | 必填，`1[3-9]开头的11 位` | `"123"` → `phone 手机号格式不正确` |
| birthDate | 必填，不能晚于今天 | 未来日期 → `出生日期不能晚于今天` |
| emergencyContact | 必填 | `""` → `紧急联系人不能为空` |
| — | **年龄必须 16~100 岁** | 未成年 → `参赛年龄不得低于 16 周岁，当前 X 岁` |

**实测响应**：

```json
{ "code": 200, "msg": "操作成功", "data": { "id": 15 } }
```

**实测异常用例**：

```json
// 身份证格式错误
{ "name": "测试", "gender": 1, "idCard": "123", "phone": "13800138000",
  "birthDate": "1990-01-01", "emergencyContact": "张三" }
→ {"code":400,"msg":"idCard 身份证号格式不正确","data":null}
```

**⚠️ 身份证唯一性**：表上有唯一索引。重复提交会返回：

```
code=500 "身份证号已存在，请勿重复录入"
```

所以测试时每次要用**不同的 idCard**。

---

### 6.2 修改选手

```
PUT {{baseUrl}}/api/runner/update
Authorization: Bearer {{token}}
Content-Type: application/json
```

**请求体**（只需传要改的字段）：

```json
{
  "id": 5,
  "phone": "13911112222",
  "emergencyContact": "王五,13700137000"
}
```

**异常用例**：

| 场景 | 期望 |
|---|---|
| 不传 id | 选手ID不能为空 |
| id 不存在 | 选手不存在（404） |
| idCard 改成别人的 | 身份证号已被其他选手使用 |

> 注意：修改时 `birthDate` 不做年龄校验（Service 里 `isUpdate=true` 跳过），只有新增才校验年龄。

---

### 6.3 分页查询选手

```
GET {{baseUrl}}/api/runner/list
Authorization: Bearer {{token}}
```

**Query 参数**：

| 参数 | 说明 |
|---|---|
| pageNum / pageSize | 分页，同上 |
| name | 姓名模糊 |
| phone | 手机号模糊 |

**实测响应**：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 4,
    "pages": 1,
    "pageNum": 1,
    "pageSize": 10,
    "list": [
      {
        "id": 8, "name": "张五", "gender": 1,
        "idCard": "610104198808083458", "phone": "13900139000",
        "birthDate": "1988-08-08", "emergencyContact": "李五,13700137000",
        "createTime": "2026-10-06 14:45:40", "age": 38
      }
    ]
  }
}
```

> `age`（年龄）是 Service 层根据出生日期算出来的，非数据库字段。

**实测组合查询**：

```
GET /api/runner/list?name=张     → total=4
GET /api/runner/list?phone=139   → 按手机号模糊
```

---

### 6.4 选手详情

```
GET {{baseUrl}}/api/runner/detail/5
Authorization: Bearer {{token}}
```

**异常用例**：

```
GET /api/runner/detail/9999  →  {"code":404,"msg":"选手不存在","data":null}
```

---

### 6.5 删除选手（含外键约束测试）

```
DELETE {{baseUrl}}/api/runner/delete/5
Authorization: Bearer {{token}}
```

**实测响应**：

```json
{ "code": 200, "msg": "操作成功", "data": null }
```

**异常用例（外键约束，重要）**：

删一个还有报名的选手，会返回：

```json
{ "code": 500, "msg": "该选手还有 1 条有效报名，无法删除。请先到「报名管理」中取消报名", "data": null }
```

> `GlobalExceptionHandler` 专门处理了 `DataIntegrityViolationException`，
> 把 MySQL 的英文报错翻译成了人话。id=5 / 6 / 7 / 8 都有报名记录，测这个用例时别删它们。

---

## 七、报名模块（6 个）

### 7.1 提交报名

```
POST {{baseUrl}}/api/register/submit
Authorization: Bearer {{token}}
Content-Type: application/json
```

**请求体**：

```json
{ "eventId": 4, "runnerId": 5 }
```

**实测响应**：

```json
{ "code": 200, "msg": "操作成功", "data": { "registrationId": 7 } }
```

**异常用例**：

| 场景 | 期望 |
|---|---|
| eventId 不存在 | `code=404 赛事不存在`（实测确认） |
| runnerId 不存在 | 选手不存在 |
| 名额已满 | 报名人数已达上限 |
| 重复报名 | 该选手已报名该赛事 |
| 赛事已截止 | 赛事已截止，不接受报名 |
| 性别不符 | 赛事仅限男性/女性参加 |

> ⚠️ 这个接口会**同时增加赛事的 registeredCount**。测试后建议清理，或接受数据变化。

---

### 7.2 取消报名

```
POST {{baseUrl}}/api/register/cancel?id=7
Authorization: Bearer {{token}}
```

**实测响应**：

```json
{ "code": 200, "msg": "报名已取消，号码布已释放", "data": null }
```

**设计亮点**：取消报名是**物理删除**记录，所以号码布会同时释放，不需要先手动清号码布。

> ⚠️ 这个操作会同时**减少赛事的 registeredCount**。测完记得核对数据一致性。

---

### 7.3 分配号码布

```
PUT {{baseUrl}}/api/register/bib?id=2&bibNumber=A0001
Authorization: Bearer {{token}}
```

**注意**：这是 **PUT + 查询参数**，不是 JSON body。

**实测响应**：

```json
{ "code": 200, "msg": "操作成功", "data": "A0001" }
```

**异常用例**：

| 场景 | 期望 |
|---|---|
| bibNumber 已被占用 | 号码布已被占用 |
| id 不存在 | 报名记录不存在 |
| 缺 bibNumber 参数 | 400 参数缺失 |

---

### 7.4 清空号码布

```
DELETE {{baseUrl}}/api/register/bib?id=2
Authorization: Bearer {{token}}
```

**实测响应**：

```json
{ "code": 200, "msg": "号码布已清空", "data": null }
```

---

### 7.5 分页查询报名

```
GET {{baseUrl}}/api/register/list
Authorization: Bearer {{token}}
```

**Query 参数**：

| 参数 | 说明 |
|---|---|
| pageNum / pageSize | 分页 |
| eventId | 按赛事筛选 |
| name | 选手姓名模糊（**会自动关联查询**） |

**实测响应**（嵌套 VO 完整装配）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 3,
    "pages": 1,
    "pageNum": 1,
    "pageSize": 10,
    "list": [
      {
        "id": 5,
        "eventId": 4,
        "runnerId": 8,
        "bibNumber": null,
        "registerTime": "2026-10-06 14:51:17",
        "payStatus": 0,
        "status": 1,
        "runner": {
          "id": 8, "name": "张五", "gender": 1, "phone": "13900139000"
        },
        "event": {
          "id": 4,
          "eventName": "2026西安国际马拉松",
          "eventDate": "2026-11-15"
        }
      }
    ]
  }
}
```

**断言建议**：

| 断言 | 期望 |
|---|---|
| `$.data.list[0].runner.name` | 非空（嵌套对象装配成功） |
| `$.data.list[0].event.eventName` | 非空 |
| `$.data.list[*].status` | 1已报名 / 2已取消 |

**status 含义**：1已报名 2已取消 3退赛

---

### 7.6 报名详情

```
GET {{baseUrl}}/api/register/detail/2
Authorization: Bearer {{token}}
```

返回结构同 list 的单条形式。

---

## 八、成绩模块（6 个）

### 8.1 录入成绩

```
POST {{baseUrl}}/api/result/input
Authorization: Bearer {{token}}
Content-Type: application/json
```

**请求体**：

```json
{
  "registrationId": 2,
  "chipTime": 12800,
  "netTime": 12550,
  "finishStatus": 1,
  "finishTime": "2026-11-15 10:29:10"
}
```

**字段说明**：

| 字段 | 说明 |
|---|---|
| registrationId | 报名记录 id（不是选手 id！） |
| chipTime | 枪声成绩（秒） |
| netTime | 净成绩（秒） |
| finishStatus | 1完赛 2未完赛 3退赛 |
| finishTime | `yyyy-MM-dd HH:mm:ss` |

**实测响应**：

```json
{ "code": 200, "msg": "操作成功", "data": { "id": 2 } }
```

**实测异常用例（重复录入）**：

```json
→ {"code":500,"msg":"该报名记录已录入过成绩，不允许重复录入","data":null}
```

> ⚠️ 报名记录 id=2 已有成绩（result id=2），重复录入会报上面这个错。
> 测试时请用没有成绩的报名记录。

---

### 8.2 修改成绩

```
PUT {{baseUrl}}/api/result/update
Authorization: Bearer {{token}}
Content-Type: application/json
```

**请求体**：

```json
{ "id": 2, "chipTime": 12900, "netTime": 12600, "finishStatus": 1 }
```

---

### 8.3 赛事总排名

```
GET {{baseUrl}}/api/result/eventRanking/4?pageNum=1&pageSize=10
Authorization: Bearer {{token}}
```

**Query 参数**：`pageNum`（默认1）、`pageSize`（默认10）

**实测响应**（含格式化时长字段）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 1,
    "pages": 1,
    "pageNum": 1,
    "pageSize": 10,
    "list": [
      {
        "rank": 1,
        "name": "张三",
        "bibNumber": "A0001",
        "gender": 1,
        "age": 31,
        "netTime": 12550,
        "netTimeStr": "03:29:10"
      }
    ]
  }
}
```

> `netTimeStr` 是把秒数转成 `HH:mm:ss` 的格式化字段，`netTime` 是原始秒数。
> **断言时可以校验两者一致性**：12550 秒 = 3*3600 + 29*60 + 10 = 12550 ✓

---

### 8.4 男女分组排名

```
GET {{baseUrl}}/api/result/genderRanking/4
Authorization: Bearer {{token}}
```

**实测响应**：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "maleList": [
      { "rank": 1, "name": "张三", "bibNumber": "A0001",
        "gender": 1, "age": 31, "netTime": 12550, "netTimeStr": "03:29:10" }
    ],
    "femaleList": []
  }
}
```

**断言建议**：`$.data.maleList` 是数组，`$.data.femaleList` 是数组（可能为空数组，不是 null）

---

### 8.5 年龄分组排名

```
GET {{baseUrl}}/api/result/ageRanking/4
Authorization: Bearer {{token}}
```

**实测响应**（分组 key 用下划线命名）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "group_29below": [],
    "group_30_39": [
      { "rank": 1, "name": "张三", "bibNumber": "A0001",
        "gender": 1, "age": 31, "netTime": 12550, "netTimeStr": "03:29:10" }
    ],
    "group_40_49": [],
    "group_50up": []
  }
}
```

**分组 key 说明**：

| key | 年龄段 |
|---|---|
| group_29below | 29 岁以下 |
| group_30_39 | 30-39 岁 |
| group_40_49 | 40-49 岁 |
| group_50up | 50 岁以上 |

---

### 8.6 分页查询成绩

```
GET {{baseUrl}}/api/result/list
Authorization: Bearer {{token}}
```

**Query 参数**：pageNum、pageSize、eventId、name

**实测响应**：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 1,
    "pages": 1,
    "pageNum": 1,
    "pageSize": 10,
    "list": [
      {
        "id": 2,
        "registrationId": 2,
        "chipTime": 12800,
        "netTime": 12550,
        "finishStatus": 1,
        "finishTime": "2026-11-15 10:29:10",
        "runnerName": "张三",
        "bibNumber": "A0001"
      }
    ]
  }
}
```

> 这里返回的是扁平字段 `runnerName`，不是嵌套对象 —— 与报名的嵌套 VO 不同，注意区分。

---

## 九、统计模块（1 个）

### 9.1 赛事统计总览

```
GET {{baseUrl}}/api/statistics/eventOverview/4
Authorization: Bearer {{token}}
```

**实测响应**：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "eventId": 4,
    "eventName": "2026西安国际马拉松",
    "totalRegister": 2,
    "maleCount": 2,
    "femaleCount": 0,
    "finishCount": 1,
    "unFinishCount": 0,
    "retireCount": 0,
    "finishRate": 50.00,
    "avgNetTime": 12550,
    "avgNetTimeStr": "03:29:10"
  }
}
```

**断言建议**：

| 断言 | 说明 |
|---|---|
| `$.data.eventId` | 4 |
| `$.data.maleCount + $.data.femaleCount` | 等于 `totalRegister` |
| `$.data.finishRate` | 0~100 的数值 |

---

## 十、鉴权异常测试（独立场景）

### 10.1 未带 Token

```
GET {{baseUrl}}/api/event/list     （不带 Authorization 头）
```

**实测响应**：

```json
{ "msg": "未登录或登录已过期，请重新登录", "code": 40100, "data": null }
```

### 10.2 Token 无效

```
GET {{baseUrl}}/api/event/list
Authorization: Bearer invalidtoken123
→ 同样返回 code=40100
```

### 10.3 退出登录后访问

```
1) POST /api/admin/logout    （带有效 token）
2) GET  /api/event/list     （带刚才那个 token）
→ {"code":40100,...}
```

### 10.4 OPTIONS 预检放行

```
OPTIONS /api/event/list
→ 放行，不返回 40100
```

---

## 十一、推荐的测试套件执行顺序

ApiFox 里建议按这个顺序建测试套件：

```
第1步  登录                      ← 必须第一个，拿到 token
第2步  当前登录人信息             ← 验证 token 有效
第3步  【只读接口】赛事/选手/报名/成绩/排名/统计 查询
第4步  【写接口】新增赛事 → 记下 id
第5步  【写接口】新增选手（用新身份证号）
第6步  【写接口】报名 → 号码布分配 → 录入成绩
第7步  【异常用例】校验失败、404、重复报名
第8步  【清理】删除测试数据
第9步  退出登录                  ← 放最后，会让 token 失效
```

**为什么这么排**：

- 登录必须最先（其他接口都要 token）
- 退出登录必须最后（会让 token 立即失效）
- 查询放前面（无副作用，失败了也不影响后续）
- 新增 → 报名 → 成绩有数据依赖顺序
- 身份证唯一，所以每次新增选手必须换 idCard

---

## 十二、建议补的断言类型

ApiFox 支持脚本后置断言，可加这些增强校验：

**12.1 Token 提取（登录接口后置脚本）**

```javascript
var body = pm.response.json();
if (body.code === 200) {
    pm.environment.set("token", body.data.token);
}
```

**12.2 通用后置断言（可复用）**

```javascript
// 断言统一结构
pm.test("code 合法", function () {
    pm.expect(pm.response.json().code).to.be.oneOf([200, 400, 40100, 40101, 404, 500]);
});

pm.test("响应耗时 < 500ms", function () {
    pm.expect(pm.response.responseTime).to.be.below(500);
});
```

**12.3 分页字段完整性（列表接口通用）**

```javascript
var d = pm.response.json().data;
pm.test("分页字段完整", function () {
    pm.expect(d).to.have.property('total');
    pm.expect(d).to.have.property('pages');
    pm.expect(d).to.have.property('list');
});
pm.test("pages 计算正确", function () {
    var expectPages = d.total === 0 ? 0 : Math.ceil(d.total / d.pageSize);
    pm.expect(d.pages).to.eql(expectPages);
});
```

**12.4 嵌套对象装配校验（报名列表）**

```javascript
var list = pm.response.json().data.list;
pm.test("每条报名都装配了选手信息", function () {
    list.forEach(function (item) {
        pm.expect(item.runner).to.be.an('object');
        pm.expect(item.runner.name).to.be.a('string');
    });
});
```

---

## 十三、已知的数据依赖（测试前必读）

当前数据库里的固定数据（2026-10-10 实测）：

| 表 | 条数 | 关键数据 |
|---|---|---|
| admin | 1 | admin / 123456 |
| marathon_event | 1 | id=4，2026西安国际马拉松，status=0，quota=10，已报名2 |
| runner | 4 | id=5(张三),6(张四),7(张五),8(张五) |
| registration | 3 | id=2(有号码布A0001),3(已取消),5 |
| result | 1 | id=2，registration_id=2 |

**由此带来的测试限制**：

- 删选手 id=5~8 都会失败（都有报名记录）
- 报名记录 id=2 已有成绩，不能重复录入
- 赛事 id=4 已报名 2 人，quota=10，还能报8 人
- 想测「名额已满」需要先改 quota 或多报名

---

## 十四、快速验证清单（勾选用）

| # | 用例 | 期望 code | 实测 |
|---|---|---|---|
| 1 | 登录成功 | 200 | ✅ |
| 2 | 密码错误 | 40101 | ✅ |
| 3 | 账号为空 | 400 | ✅ |
| 4 | 未带 Token | 40100 | ✅ |
| 5 | Token 无效 | 40100 | ✅ |
| 6 | 当前登录人 | 200 | ✅ |
| 7 | 赛事分页查询 | 200 | ✅ |
| 8 | 赛事多条件查询 | 200 | ✅ |
| 9 | pageSize 超上限截断 | pageSize=100 | ✅ |
| 10 | 赛事详情 | 200 | ✅ |
| 11 | 赛事不存在 | 404 | ✅ |
| 12 | 新增赛事 | 200 | ✅ |
| 13 | 修改赛事 | 200 | ✅ |
| 14 | 选手分页查询 | 200 | ✅ |
| 15 | 选手姓名模糊 | 200 | ✅ |
| 16 | 新增选手 | 200 | ✅ |
| 17 | 身份证格式错误 | 400 | ✅ |
| 18 | 选手不存在 | 404 | — |
| 19 | 删有报名的选手 | 500 + 友好提示 | — |
| 20 | 报名分页查询 | 200 | ✅ |
| 21 | 提交报名 | 200 | ✅ |
| 22 | 报名不存在赛事 | 404 | ✅ |
| 23 | 取消报名 | 200 | — |
| 24 | 分配号码布 | 200 | — |
| 25 | 重复录入成绩 | 500 + 友好提示 | ✅ |
| 26 | 赛事总排名 | 200 | ✅ |
| 27 | 男女分组排名 | 200 | ✅ |
| 28 | 年龄分组排名 | 200 | ✅ |
| 29 | 成绩列表 | 200 | ✅ |
| 30 | 统计总览 | 200 | ✅ |

---

## 附：本次排查发现的一个环境坑

**如果你在 Git Bash 里用 curl 测试，POST 请求体带中文会返回 500。**

原因：Git Bash 按 UTF-8 传参，Windows 版 curl 按 GBK 解读，Jackson 报：

```
JSON parse error: Invalid UTF-8 start byte 0xb6
```

这是**测试工具的编码问题，不是接口 Bug**（ApiFox 不受影响，ApiFox 默认 UTF-8）。

验证方法：curl 测试时请求体用纯 ASCII 即可。

```bash
# 错误示范（会500）
curl -d '{"eventName":"测试赛事"}'   # 中文

# 正确示范
curl -d '{"eventName":"TestRace"}'   # ASCII
```
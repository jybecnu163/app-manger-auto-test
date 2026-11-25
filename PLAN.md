# 系统管理平台实施方案

## 1. 目标

为数百个 Java/Spring 系统提供统一管理平台：

- 服务端展示系统列表，点击进入详情页。
- 详情页展示每个系统上报的接口、输入输出 Schema，并支持在线测试。
- 各系统引入客户端 SDK 后，启动时与服务端通信：首次获取唯一 appid 并存入本地文件；随后上报接口列表与在线状态。

## 2. 技术选型（已确认）

| 项     | 选择                                                              |
|-------|-----------------------------------------------------------------|
| 服务端   | Spring Boot 3.x 单体                                              |
| 持久层   | MyBatis-Plus                                                    |
| 数据库   | MySQL，连接 `jdbc:mysql://localhost:3306/test`                     |
| 管理页面  | 原生 HTML + JS，放在 `app-service-server/src/main/resources/static/` |
| 客户端   | Java Spring Boot Starter                                        |
| 接口元数据 | SDK 自动扫描 Spring MVC 路由并生成 JSON Schema                           |
| 测试链路  | 服务端转发到已注册实例                                                     |
| 安全    | 不做密钥、不做登录，内网使用                                                  |
| 扫描范围  | 仅 Spring MVC，不含 Feign/Dubbo                                     |

## 3. 模块结构

```text
app-service/
├── pom.xml                              (parent, packaging=pom)
├── app-service-common/                  (协议 DTO、常量)
├── app-sdk-spring-boot-starter/         (业务系统引入的客户端)
└── app-service-server/                  (Spring Boot 单体 + 管理页面)
    ├── src/main/java/...
    └── src/main/resources/
        ├── application.yml
        ├── db/schema.sql
        └── static/
            ├── index.html
            ├── app-detail.html
            ├── css/app.css
            └── js/
                ├── common.js
                ├── app.js
                └── test.js
```

## 4. 核心流程

```mermaid
flowchart TD
    A[业务系统启动] --> B{本地有 appid 文件?}
    B -- 否 --> C[调用 /api/agent/register<br/>按 systemName+env 幂等获取 appid+token]
    C --> D[原子写入本地文件]
    B -- 是 --> E[读取 appid+token]
    D --> F[ApplicationReadyEvent 后扫描 Spring MVC 路由]
    E --> F
    F --> G[调用 /api/agent/heartbeat<br/>上报实例状态 + 接口列表 + schemaHash]
    G --> H[(MySQL)]

    I[浏览器打开 HTML 页面] --> J[同源 fetch /api/admin/**]
    J --> K[系统列表 / 详情 / 接口 Schema]
    K --> L[点击测试]
    L --> M[服务端转发到在线实例]
    M --> N[展示状态码 / 响应体 / 耗时]
```

## 5. API 设计

### Agent 侧（SDK 调用）

| 方法   | 路径                     | 说明                                                  |
|------|------------------------|-----------------------------------------------------|
| POST | `/api/agent/register`  | 按 `(systemName, env)` 幂等分配 appid，返回 `appId`、`token` |
| POST | `/api/agent/heartbeat` | 上报实例信息、在线状态、接口列表、`schemaHash`                       |

### Admin 侧（HTML 页面调用）

| 方法   | 路径                                   | 说明                                      |
|------|--------------------------------------|-----------------------------------------|
| GET  | `/api/admin/apps`                    | 系统列表：名称、环境、在线实例数、接口数、最近心跳               |
| GET  | `/api/admin/apps/{appId}`            | 系统详情 + 实例列表                             |
| GET  | `/api/admin/apps/{appId}/interfaces` | 接口列表，含 `requestSchema`/`responseSchema` |
| POST | `/api/admin/apps/{appId}/test`       | 测试转发，返回状态码、响应头、响应体、耗时                   |

## 6. 数据库设计

`schema.sql` 使用 `CREATE TABLE IF NOT EXISTS`，启动自动初始化。

| 表               | 关键字段                                                                                                                                                       |
|-----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `app`           | `id`, `app_id`(唯一), `name`, `env`, `token`, `interfaces_hash`, `created_at`, `updated_at`                                                                  |
| `app_instance`  | `id`, `app_id`, `instance_key`, `ip`, `port`, `hostname`, `status`, `last_heartbeat_at`, `start_time`                                                      |
| `api_interface` | `id`, `app_id`, `method`, `path`, `controller`, `operation`, `request_schema`, `response_schema`, `schema_hash`, `updated_at`，唯一键 `(app_id, method, path)` |
| `test_log`      | `id`, `app_id`, `interface_id`, `request_summary`, `response_status`, `cost_ms`, `created_at`                                                              |

状态判定：心跳默认 30 秒一次，超过 3 个周期（90 秒）未上报则置为 `OFFLINE`。

## 7. SDK 设计

配置前缀：`app.registry.*`

| 配置项                  | 默认值                                       | 说明             |
|----------------------|-------------------------------------------|----------------|
| `server-url`         | 无                                         | 管理服务端地址        |
| `system-name`        | `${spring.application.name}`              | 系统名称           |
| `env`                | `default`                                 | 环境标识           |
| `appid-file`         | `~/.app-service/{systemName}-{env}.appid` | 本地 appid 文件    |
| `advertise-ip`       | 自动探测                                      | 上报给服务端的业务系统 IP |
| `heartbeat-interval` | `30s`                                     | 心跳间隔           |
| `exclude-paths`      | `/actuator/**,/error`                     | 不采集的路径         |

行为：

- 注册按 `(systemName, env)` 幂等；本地文件丢失可取回原 appid。
- 文件写入采用临时文件 + 原子移动，避免半写。
- 注册失败不阻塞业务系统启动，后台按退避策略重试。
- `ApplicationReadyEvent` 后扫描 `RequestMappingHandlerMapping`。
- 提取 HTTP 方法、路径、参数注解、DTO 字段结构，生成 JSON Schema。
- 用 `schemaHash` 对比，服务端无变化则不更新 schema。
- 心跳失败只记录日志，不影响业务系统。

## 8. 管理页面

原生 HTML + JS，无构建步骤：

- `index.html`：系统列表、搜索、状态筛选。
- `app-detail.html`：系统信息、实例列表、接口列表、Schema 折叠展示、测试面板。
- `common.js`：fetch 封装。
- `app.js`：列表与详情逻辑。
- `test.js`：测试请求与响应展示。

## 9. 测试转发约束

- 目标地址只能来自已注册实例的 `advertiseIp:port`，不接受任意 URL，防止 SSRF。
- 连接超时 3 秒，读取超时 10 秒，响应体最大 1MB。
- 每次测试写入 `test_log`。

## 10. 实施步骤

1. 父 `pom.xml` + 三个子模块骨架。
2. `app-service-common`：注册/心跳/接口元数据 DTO 与常量。
3. `app-service-server`：MySQL 配置、MyBatis-Plus、`schema.sql`、Agent API。
4. `app-service-server`：Admin API 与测试代理。
5. `app-service-server`：静态 HTML/JS 页面。
6. `app-sdk-spring-boot-starter`：自动配置、注册、本地文件、心跳、接口扫描。
7. 本地联调：启动 server → 启动示例业务系统 → 验证列表/详情/测试。
8. README：接入方式与配置项说明。

## 11. 后续可扩展

- 注册密钥与管理端登录。
- Feign/Dubbo 接口采集。
- 客户端反向通道，支持内网无入站端口的系统。
- Flyway 管理表结构版本。

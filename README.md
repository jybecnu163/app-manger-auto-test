# app-service 系统管理平台

统一管理数百个 Java/Spring 系统：服务端展示系统列表与接口详情，业务系统引入 SDK 后自动注册、上报接口与在线状态，管理端可在线测试接口。

详细方案见 [PLAN.md](PLAN.md)。

## 模块

- `app-service-common`：注册/心跳/接口元数据协议 DTO。
- `app-service-server`：Spring Boot 服务端，含 MyBatis-Plus、MySQL、管理 API、测试代理和原生 HTML/JS 页面。
- `app-sdk-spring-boot-starter`：业务系统引入的 Spring Boot Starter。

## 构建与运行

需要 JDK 17+ 和 Maven 3.9+。数据库使用 MySQL，先创建 `test` 库：

```sql
CREATE DATABASE IF NOT EXISTS test DEFAULT CHARACTER SET utf8mb4;
```

数据库连接在 `app-service-server/src/main/resources/application.yml` 中配置，默认：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/test?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&allowMultiQueries=true
    username: root
    password: pass
```

构建：

```bash
mvn clean install
```

启动服务端：

```bash
java -jar app-service-server/target/app-service-server-1.0.0.jar
```

浏览器打开 `http://localhost:8080/` 即可看到系统列表。表结构由 `db/schema.sql` 在启动时自动创建。

## 生成空壳业务系统

系统列表页点击 **生成空壳系统**，填写系统名称、端口和环境即可下载一个可运行的 Spring Boot 项目压缩包。生成项目包含：

- `GET /api/health` 健康检查接口
- 已配置好的 SDK 依赖与 `app.registry.*` 配置
- 自动生成的 appId 与 token
- `README.md` 启动说明

启动前先在项目根目录执行 `mvn install`，确保 SDK 依赖已安装到本地仓库。解压生成包后执行：

```bash
mvn clean package
java -jar target/{系统名}-1.0.0.jar
```

启动后 SDK 会自动向当前管理端注册并上报心跳，系统列表会出现该服务，详情页可看到 `/api/health` 接口。

设计说明见 [docs/DESIGN_SCAFFOLD_GENERATION.md](docs/DESIGN_SCAFFOLD_GENERATION.md)，任务拆解见 [docs/TASKS_SCAFFOLD_GENERATION.md](docs/TASKS_SCAFFOLD_GENERATION.md)。

## 业务系统接入

在被管理的 Spring Boot 系统中引入 SDK：

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>app-sdk-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

配置管理服务端地址与身份。推荐直接在配置中指定 `appid` 和 `token`，此时 SDK 完全不读写本地文件；首次启动会调用注册接口，服务端按该 appid 自动创建记录：

```yaml
app:
  registry:
    server-url: http://localhost:8080
    system-name: order-service
    env: dev
    appid: APP-ORDER-DEMO-0001
    token: order-demo-token
```

如果不配置 `appid`/`token`，SDK 会回退到原来的方式：首次调用服务端注册，把 `appId` 和 `token` 写入本地文件（默认 `~/.app-service/{systemName}-{env}.appid`）。

启动后 SDK 会：

1. 首次向服务端注册：配置身份时使用配置中的 appid/token，否则自动生成并写入本地文件。
2. 扫描 Spring MVC 路由，生成接口请求/响应 Schema。
3. 按 30 秒间隔上报接口列表和在线状态。
4. 服务端超过 90 秒未收到心跳则将实例标记为离线。

其他可选配置：

```yaml
app:
  registry:
    appid-file: /data/app-registry/order-service.appid   # 仅回退模式使用
    advertise-ip: 192.168.1.10
    heartbeat-interval: 30s
    exclude-paths:
      - /actuator/**
      - /error
    controller-log-enabled: true   # 默认开启，记录 Controller 输入输出日志
```

开启后，SDK 会通过 AOP + 反射记录所有 `@RestController` 方法的入参、返回值和耗时，日志前缀为 `[CTRL-IN]`、`[CTRL-OUT]`、`[CTRL-ERR]`。`ServletRequest`、`ServletResponse`、`HttpSession` 只记录类名，避免打印大对象。

### 流量录制与 Dry-Run 回放配置

以下能力全部默认关闭，仅建议在测试环境按需要开启：

```yaml
app:
  registry:
    traffic-record:
      enabled: false
      sample-rate: 0.05
      only-errors: false
      include-paths:
        - /api/**
      exclude-paths:
        - /actuator/**
        - /error
      max-body-bytes: 4096
      mask-headers:
        - Authorization
        - Cookie
        - Set-Cookie
        - X-Token
      mask-fields:
        - password
        - token
        - phone
        - idCard
      batch-size: 100
      flush-interval: 5s
      queue-size: 10000
    external-call-record:
      enabled: false
      max-body-bytes: 4096
      mask-headers:
        - Authorization
        - Cookie
        - Set-Cookie
        - X-Token
      mask-fields:
        - password
        - token
    replay:
      enabled: false
      db-strategy: rollback
      mock-missing-policy: fail
      signature-enabled: true
      max-timestamp-skew: 5m
      signature-secret: app-service-replay-secret
      mock-response-path: /api/agent/mock-responses
```

`traffic-record.enabled` 与 `external-call-record.enabled` 控制是否采集入站/出站流量；`replay.enabled` 控制是否接受管理端下发的 Dry-Run 回放请求。三者互不影响，未配置时均为 `false`。

## 接口测试表单与用例

管理端详情页左侧有两个 Tab：

- **接口列表**：选择接口后根据 `requestSchema` 自动渲染参数表单，展示字段名、类型和必填标记；枚举字段以下拉框展示可选值。
- **测试用例列表**：跨接口展示所有用例，包含接口方法、路径、用例名、来源和参数摘要。数据来自接口上报时自动生成的预设用例，以及接口测试中保存的用例。

每个接口上报入库时，服务端会根据 Schema 自动生成三条预设用例：默认用例、空请求用例、备选值用例。测试完成后，当前参数会自动保存到 `api_test_case` 表，按 `接口 ID + 参数内容哈希` 去重，重复参数不会重复存储。

用例操作支持：

- 在测试用例列表中**一键回放**：直接按用例参数发送请求，无需进入表单
- 点击**回放所有用例**：按列表顺序一次性批量回放当前系统的全部用例
- **单用例测试**：把用例参数载入表单，可修改后再发送
- 手动保存为命名用例
- 删除用例

一键回放或普通测试完成后，会显示该接口的测试统计：累计测试次数、成功率、平均/最快/最慢耗时和最近测试时间。统计数据来自 `test_log` 表。

“回放所有用例”完成后会展示批量统计：用例总数、已执行数、成功/失败数、跳过数、成功率、平均/最快/最慢/累计耗时，并逐条列出每个用例的状态码、耗时和响应摘要；失败的用例会显示错误原因。批量回放中的每次请求同样写入 `test_log` 表。

接口列表发生变更时，旧接口对应的用例会被清理并重新生成预设用例。

## 测试转发说明

管理端点击测试时，服务端只把请求转发到该系统已上报的在线实例地址（`advertiseIp:port`），不接受任意 URL。连接超时 3 秒，读取超时 10 秒，响应体超过 1MB 会被截断，每次调用会写入 `test_log` 表。

## 流量录制与 Dry-Run 回放

详情页第三个 Tab **流量录制** 展示已采集的入站流量，支持按接口、方法、状态码、时间范围、失败状态和 traceId 筛选；点击一行查看请求与响应详情，点击 **Dry-Run 回放** 会用原始请求真实调用业务实例。

### 页面控制录制起始时间

“流量录制”Tab 顶部提供录制控制区：

- **已停止 / 待开始 / 录制中** 三种状态。
- **起始时间**输入框：为空表示点击“开始录制”后立即采集；填写未来时间则到点自动开始。
- **开始录制** / **结束录制**按钮。结束录制会立即停止采集并清空起始时间。

控制接口为 `POST /api/admin/apps/{appId}/traffic-record/enabled?enabled=true|false`，请求体可选 `{"startAt":"2026-11-26T15:30:00"}`。服务端会把状态持久化，并立即下发到在线实例；离线实例在下一次心跳时同步。业务系统 YAML 中的 `traffic-record.enabled` 只作为首次启动的初始值，之后以管理端状态为准。

关键约定：

- 三项开关 `traffic-record.enabled`、`external-call-record.enabled`、`replay.enabled` 默认均为 `false`，生产环境不要开启回放。
- 回放 Header 由服务端 `app.replay.signature-secret` 签名，必须与 SDK 的 `app.registry.replay.signature-secret` 一致。
- 回放请求携带 `X-App-Replay*` Header；SDK 校验签名、时间窗与 nonce。**回放开关关闭时收到回放 Header 直接返回 403**，不执行业务逻辑。
- 回放期间外部 HTTP 调用由 Mock 接管；未命中 Mock 时抛出 `ReplayMockMissingException`，绝不回退真实外部调用。
- 回放结果包含 `replayId`、`replayMode`、`dbRolledBack`、`mockHits`、`mockMisses`。统计查询使用 `GET /api/admin/interfaces/{interfaceId}/stats?replayMode=DRY_RUN`，与真实测试统计分开。

相关接口：

| 方法   | 路径                                                   | 说明                                    |
|------|------------------------------------------------------|---------------------------------------|
| POST | `/api/agent/traffic/records`                         | SDK 批量上报入站流量                          |
| POST | `/api/agent/external-call/records`                   | SDK 批量上报出站调用                          |
| GET  | `/api/agent/mock-responses`                          | SDK 拉取已启用 Mock 规则                     |
| GET  | `/api/admin/apps/{appId}/traffic/records`            | 管理端查询录制流量                             |
| GET  | `/api/admin/apps/{appId}/external-call/records`      | 管理端查询出站调用                             |
| GET  | `/api/admin/interfaces/{interfaceId}/mock-responses` | 查询接口的 Mock 规则                         |
| POST | `/api/admin/mock-responses`                          | 手工新增或从 `externalCallRecordId` 生成 Mock |
| POST | `/api/admin/mock-responses/{id}/enabled?enabled=`    | 启用/禁用 Mock 规则                         |

生成 Mock 并回放的步骤：

1. 在业务系统正常调用一次含外部 HTTP 的写接口，SDK 上报入站流量与出站调用。
2. 查询 `/api/admin/apps/{appId}/external-call/records`，取得对应调用记录 ID。
3. `POST /api/admin/mock-responses`，Body 传 `{"appId":"...","externalCallRecordId":123}`。
4. 在“流量录制”Tab 对这条流量点击 **Dry-Run 回放**，确认统计中 Mock 命中、`dbRolledBack=true`。
5. 禁用该 Mock 后再次回放，应收到“回放失败：外部调用 Mock 未命中，已阻止真实调用”，且真实外部接口未被调用。

## 本地验证示例

仓库内自带两个示例系统，用于验证注册、接口上报和测试转发：

| 模块 | 端口 | 系统名 | 主要接口 |
|---|---|---|---|
| `demo-product-service` | 8081 | `product-service` | `GET/POST /api/products/**`（含分类、库存、搜索） |
| `demo-order-service` | 8082 | `order-service` | `GET/POST/DELETE /api/orders/**`，新增 `POST /api/orders/dryrun-demo`（外部 HTTP + 写审计表） |

启动顺序：

```bash
# 1. 管理服务端
java -jar app-service-server/target/app-service-server-1.0.0.jar

# 2. 产品管理系统
java -jar demo-product-service/target/demo-product-service-1.0.0.jar

# 3. 订单管理系统
java -jar demo-order-service/target/demo-order-service-1.0.0.jar
```

打开 `http://localhost:8080/`，应看到 `product-service` 与 `order-service` 两个系统。进入详情页可以看到接口及其输入输出 Schema，点击“发送请求”即可验证服务端转发。

验证接口自动上报：修改 `demo-product-service` 的 Controller 后重启该服务，SDK 会重新扫描 Spring MVC 路由并在首次心跳上报新的接口列表和 `schemaHash`；服务端发现 hash 变化后会删除旧接口并写入新接口，管理端详情页随之更新。

示例系统的 appid/token 直接写在各自的 `application.yml` 中，不再生成本地文件：

- `product-service`：`APP0001` / `product-demo-token`
- `order-service`：`APP0002` / `order-demo-token`

两个示例系统首次启动时会调用 `/api/agent/register`，服务端按配置中的 appid 自动创建 `app` 记录，无需在 `schema.sql` 中预置数据。`advertise-ip` 固定为 `127.0.0.1`，确保管理服务端能转发到本地实例。

## 当前范围与风险边界

- 仅扫描 Spring MVC 路由，不含 Feign/Dubbo；HTTP Mock 第一期仅覆盖 RestTemplate。
- 未做管理端登录鉴权，建议在内网使用。
- 管理页面使用原生 HTML + JS，无前端构建步骤。
- 流量录制与回放三项开关默认关闭，生产环境不要开启 `replay.enabled`。
- Dry-Run 只通过外层事务回滚实现，**不是影子库**；写入必须位于同一 DataSource / TransactionManager。
- 多步骤业务链路（跨请求 Session）暂不支持。
- MQ、Redis、文件系统 Mock 暂不支持。
- Service 不应使用 `@Transactional(REQUIRES_NEW)`；SDK 启动时会扫描并告警，但其中的写入不会被回放事务回滚。
- 异步线程中的数据库写入不在第一期保证范围内；`ReplayTaskDecorator` 只传播回放上下文。
- 回放开关关闭时收到回放 Header 返回 403；签名密钥需与服务端保持一致。

详细设计见 `docs/DESIGN_TRAFFIC_RECORDING_AND_DRYRUN_REPLAY.md`，任务拆解见 `docs/TASKS_TRAFFIC_RECORDING_AND_DRYRUN_REPLAY.md`。

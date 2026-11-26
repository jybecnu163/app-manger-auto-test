# 关键类与方法说明

本文汇总 `app-service-common`、`app-service-server`、`app-sdk-spring-boot-starter` 三个模块中的类、接口和关键方法，说明其在自动化测试平台中的职责与调用关系。本文与源码中的 Javadoc 注释一一对应，适合作为阅读代码和定位问题的索引。

## 1. 阅读范围

| 模块                            | 定位            | 主要内容                                                     |
|-------------------------------|---------------|----------------------------------------------------------|
| `app-service-common`          | 共享协议层         | DTO、枚举、统一响应、请求指纹和回放签名工具                                  |
| `app-service-server`          | 管理与调度中心       | 注册心跳、接口/用例、流量录制、Mock、Dry-Run 回放、Scaffold 生成、管理端 REST API |
| `app-sdk-spring-boot-starter` | 被管理业务系统接入 SDK | 自动装配、注册心跳、接口扫描、流量录制、外部调用录制、回放安全、Mock 拦截                  |

本次已在源码中为关键类和方法补充 Javadoc；纯数据结构类以“职责说明”为主，业务链路类以“方法语义、副作用、约束”为重点。

## 2. 总体调用链路

```text
业务系统启动
  -> SDK AppRegistryAutoConfiguration 装配 Bean
  -> HeartbeatScheduler 首次注册
  -> AppRegistryClient.register -> AgentController.register -> AppService.register
  -> InterfaceScanner.scan 上报接口元数据
  -> HeartbeatScheduler 定时心跳
  -> AppService.heartbeat 更新实例、接口和自动用例

管理端操作
  -> AdminController
  -> AppService / TestProxyService / MockResponseService / RecordingControlService
  -> TestProxyService 转发到在线实例

Dry-Run 回放
  -> ReplayHeaderService 构造签名头
  -> 业务实例 ReplayFilter 校验签名、nonce、时间窗
  -> ReplayContext 建立回放上下文
  -> 外层事务强制回滚
  -> ReplayRestTemplateInterceptor 按 requestHash 命中 Mock
  -> 未命中 Mock 时阻止真实外部调用
  -> 管理端 TestProxyService 写 test_log 并返回统计结果
```

## 3. app-service-common

共享模块没有业务状态，主要负责管理端与 SDK 之间的传输协议和安全计算。所有 DTO 使用 Lombok `@Data` 生成 getter/setter。

### 3.1 类清单

| 类                            | 包路径    | 职责                                                          |
|------------------------------|--------|-------------------------------------------------------------|
| `ExternalCallBatchDTO`       | `dto`  | 外部调用记录批量上报容器，包含 `appId`、`token` 和 `records`。                |
| `ExternalCallRecordDTO`      | `dto`  | 单条出站 HTTP 调用记录，包含请求/响应快照、耗时、`requestHash` 和 `traceId`。      |
| `HeartbeatRequest`           | `dto`  | Agent 心跳请求，携带实例信息、状态、接口列表及 `interfacesHash`。                |
| `HeartbeatResponse`          | `dto`  | 心跳响应，下发 `trafficRecordEnabled` 和 `trafficRecordStartAt`。    |
| `InstanceInfoDTO`            | `dto`  | 实例元数据：`instanceKey`、IP、端口、主机名和启动时间。                         |
| `InterfaceMetaDTO`           | `dto`  | 单个接口元数据，包含方法、路径、Controller、操作名、Schema 和 `schemaHash`。       |
| `MockResponseDTO`            | `dto`  | Mock 规则，包含 `urlPattern`、`requestHash`、模拟状态码/头/体和 `enabled`。 |
| `RegisterRequest`            | `dto`  | 应用注册请求，携带系统名、环境、身份信息和实例地址。                                  |
| `RegisterResponse`           | `dto`  | 注册响应，回传服务端签发的 `appId` 与 `token`。                            |
| `ReplayMode`                 | `dto`  | 回放模式枚举：`NONE` 常规调用，`DRY_RUN` 事务回滚回放。                        |
| `Result<T>`                  | `dto`  | 统一 API 响应包装，`code=0` 成功，`code=1` 失败。                        |
| `TrafficBatchDTO`            | `dto`  | 入站流量批量上报容器。                                                 |
| `TrafficRecordDTO`           | `dto`  | 单条入站流量记录，包含请求/响应快照、链路标识、`success`、`error` 和 `requestHash`。  |
| `TrafficRecordSwitchRequest` | `dto`  | 录制开关控制请求，`enabled` 与 `startAt` 组合表示立即或定时开启。                 |
| `ReplaySignatureCalculator`  | `util` | 回放请求 HMAC-SHA256 签名与验签工具。                                   |
| `RequestHashUtil`            | `util` | 请求指纹计算工具，用于流量去重与 Mock 匹配。                                   |

### 3.2 关键方法

| 类                           | 方法                                               | 说明                                                           |
|-----------------------------|--------------------------------------------------|--------------------------------------------------------------|
| `Result<T>`                 | `ok(T data)`                                     | 构造成功响应，固定 `code=0`、`message="ok"`。                           |
| `Result<T>`                 | `fail(String message)`                           | 构造失败响应，固定 `code=1`，并清空业务数据。                                  |
| `ReplaySignatureCalculator` | `sign(secret, replayId, mode, timestamp, nonce)` | 按 `replayId                                                  |mode|timestamp|nonce` 拼接后生成小写十六进制 HMAC-SHA256 签名。 |
| `ReplaySignatureCalculator` | `verify(..., signature)`                         | 使用常量时间比较校验签名，避免时间侧信道。                                        |
| `RequestHashUtil`           | `of(method, url, queryParams, body)`             | 按 method、去查询串 URL、规范化 query 和 body 生成 MD5 指纹；query 按 key 排序。 |

## 4. app-service-server

管理服务是平台的控制面，提供页面 API、SDK Agent API、数据存储与回放调度。

### 4.1 启动与配置

| 类 | 关键方法 | 职责 |
|---|---|---|
| `AppServiceServerApplication` | `main`、`mybatisPlusInterceptor`、`proxyRestTemplate` | 启动应用；注册分页插件；提供不抛异常的 RestTemplate，保留回放时 4xx/5xx 原始响应体。 |

### 4.2 Controller

| 类                        | 关键方法                                                                          | 职责                                                       |
|--------------------------|-------------------------------------------------------------------------------|----------------------------------------------------------|
| `AgentController`        | `register`、`heartbeat`、`trafficRecords`、`externalCallRecords`、`mockResponses` | SDK 上报入口：注册、心跳、流量记录、外部调用记录、Mock 规则拉取；除注册/心跳外均按 token 鉴权。 |
| `AdminController`        | `apps`、`detail`、`interfaces`                                                  | 应用列表、详情和接口清单。                                            |
| `AdminController`        | `test`                                                                        | 单接口测试或 Dry-Run 回放。                                       |
| `AdminController`        | `cases`、`appCases`、`saveCase`、`deleteCase`                                    | 测试用例查询、保存和删除。                                            |
| `AdminController`        | `replayAllCases`、`stats`                                                      | 批量回放和测试统计。                                               |
| `AdminController`        | `mockResponses`、`saveMockResponse`、`setMockEnabled`                           | Mock 规则查询、创建和启停。                                         |
| `AdminController`        | `setTrafficRecordEnabled`、`trafficRecords`、`externalCallRecords`              | 录制开关控制、流量查询、外部调用查询。                                      |
| `ScaffoldController`     | `generate`                                                                    | 生成空壳业务系统 ZIP，并通过响应头返回 `appId`/`token`。                   |
| `GlobalExceptionHandler` | `handle`                                                                      | 全局异常处理，统一包装为 `Result.fail`。                              |

### 4.3 Service

| 类                           | 关键方法                                                      | 职责                                                             |
|-----------------------------|-----------------------------------------------------------|----------------------------------------------------------------|
| `AppService`                | `register`                                                | 按 `appId` 或 `systemName+env` 幂等注册；新建时签发 token，切换 appId 时清理旧数据。 |
| `AppService`                | `authenticate`                                            | 校验 `appId + token`，失败时拒绝 Agent 请求。                             |
| `AppService`                | `heartbeat`                                               | 更新实例状态和心跳时间；按 `interfacesHash` 重建接口与自动用例；下发录制开关。               |
| `AppService`                | `listApps`、`detail`、`listInterfaces`、`findOnlineInstance` | 应用查询与在线实例选择。                                                   |
| `RecordingControlService`   | `setEnabled`                                              | 持久化录制开关和起始时间，并向所有 UP 实例下发控制指令；单实例失败只记录日志。                      |
| `TrafficRecordService`      | `saveBatch`                                               | 按请求指纹去重后入库，并用 `AntPathMatcher` 补全 `interfaceId`。               |
| `TrafficRecordService`      | `list`                                                    | 多条件查询入站流量，最多返回 500 条。                                          |
| `TrafficRecordService`      | `findInterfaceId`                                         | 按最长路径模板匹配接口 ID，支持路径变量。                                         |
| `ExternalCallRecordService` | `saveBatch`、`listByApp`                                   | 外部调用记录去重入库，并提供最近 200 条查询。                                      |
| `TestcaseService`           | `generateAutoCases`                                       | 按接口 Schema 生成默认、空值和备选值三类自动用例。                                  |
| `TestcaseService`           | `list`、`listByApp`、`save`、`delete`                        | 用例查询、保存、去重和删除。                                                 |
| `TestProxyService`          | `test`                                                    | 单接口测试/回放核心：应用录制参数、注入回放头、转发请求、解析统计头、写 `test_log`。               |
| `TestProxyService`          | `replayAll`                                               | 批量回放用例并汇总成功率、耗时分布和逐条结果。                                        |
| `TestProxyService`          | `stats`                                                   | 按接口和回放模式统计测试日志。                                                |
| `ReplayHeaderService`       | `newReplayId`、`buildHeaders`                              | 生成 replayId 和 `X-App-Replay-*` 签名头。                            |
| `MockResponseService`       | `listByApp`、`listByInterface`                             | 查询 Mock 规则。                                                    |
| `MockResponseService`       | `create`、`generateFromExternalCall`、`setEnabled`          | 手工创建、从外部调用一键生成和启停 Mock。                                        |
| `ScaffoldService`           | `generate`                                                | 校验参数、生成 appId/token、在内存中打包 Spring Boot 空壳工程。                   |
| `ScaffoldService`           | `applicationClassName`                                    | 将系统名转换为合法 Java 启动类名。                                           |

### 4.4 Entity

实体均为 MyBatis-Plus POJO，无显式业务方法。

| 实体                   | 对应表                    | 关键语义                                                                           |
|----------------------|------------------------|--------------------------------------------------------------------------------|
| `App`                | `app`                  | 应用主表，含 token、`interfacesHash`、`trafficRecordEnabled` 和 `trafficRecordStartAt`。 |
| `AppInstance`        | `app_instance`         | 实例地址、状态、最后心跳时间和启动时间。                                                           |
| `ApiInterface`       | `api_interface`        | 接口方法、路径、Schema、`schemaHash` 和是否写操作。                                            |
| `ApiTestCase`        | `api_test_case`        | 用例参数 JSON、内容指纹和来源（AUTO/USER）。                                                  |
| `TrafficRecord`      | `traffic_record`       | 入站流量请求/响应快照、接口 ID、`requestHash`、`success`、`error`。                             |
| `ExternalCallRecord` | `external_call_record` | 出站 HTTP 调用请求/响应快照、状态码、`requestHash`。                                           |
| `MockResponse`       | `mock_response`        | Mock 匹配规则、模拟响应和 `enabled` 状态。                                                  |
| `TestLog`            | `test_log`             | 测试审计日志，含 `replayMode`、`replayId`、`dbRolledBack`、`mockHits`、`mockMisses`。       |

### 4.5 VO 与 Mapper

| 类                          | 类型     | 职责                                                      |
|----------------------------|--------|---------------------------------------------------------|
| `AppListItemVO`            | VO     | 应用列表项，含在线实例数和录制状态。                                      |
| `AppDetailVO`              | VO     | 应用详情，含实例列表。                                             |
| `BatchCaseResultVO`        | VO     | 批量回放单条用例结果。                                             |
| `BatchReplayResultVO`      | VO     | 批量回放汇总结果。                                               |
| `MockResponseSaveRequest`  | VO     | Mock 保存请求，支持外部调用记录 ID。                                  |
| `ScaffoldRequest`          | VO     | 空壳系统生成请求。                                               |
| `ScaffoldResult`           | VO     | 空壳系统生成结果，含 ZIP 字节、文件名和凭据。                               |
| `TestCaseSaveRequest`      | VO     | 用例保存请求。                                                 |
| `TestCaseVO`               | VO     | 用例视图对象。                                                 |
| `TestRequest`              | VO     | 测试请求，含 `replayMode`、`trafficRecordId`、`mockResponseId`。 |
| `TestResultVO`             | VO     | 测试结果，含回滚和 Mock 命中统计。                                    |
| `TestStatsVO`              | VO     | 测试统计结果。                                                 |
| `AppInstanceMapper`        | Mapper | `upsert` 方法按 `instanceKey` 更新或插入实例。                     |
| `ApiInterfaceMapper`       | Mapper | 接口表 BaseMapper。                                         |
| `ApiTestCaseMapper`        | Mapper | 用例表 BaseMapper。                                         |
| `AppMapper`                | Mapper | 应用表 BaseMapper。                                         |
| `ExternalCallRecordMapper` | Mapper | 外部调用表 BaseMapper。                                       |
| `MockResponseMapper`       | Mapper | Mock 表 BaseMapper。                                      |
| `TestLogMapper`            | Mapper | 测试日志表 BaseMapper。                                       |
| `TrafficRecordMapper`      | Mapper | 流量表 BaseMapper。                                         |

## 5. app-sdk-spring-boot-starter

SDK 以 Spring Boot Starter 方式接入业务系统，负责采集、上报和回放保护。源码已按功能拆分到以下包中，所有类和方法均带有说明注释，核心链路代码补充了行内注释。

| 包 | 职责 | 主要类 |
|---|---|---|
| `com.example.appservice.sdk.config` | 自动装配与配置载体 | `AppRegistryAutoConfiguration`、`AppRegistryProperties` |
| `com.example.appservice.sdk.registry` | 注册、身份解析、心跳与接口扫描 | `AppRegistryClient`、`AppIdFileStore`、`AppIdentityResolver`、`HeartbeatScheduler`、`InterfaceScanner` |
| `com.example.appservice.sdk.logging` | Controller 调用日志 | `ControllerLogAspect` |
| `com.example.appservice.sdk.context` | 链路与回放上下文 | `TraceContext`、`ReplayContext`、`ReplayInfo` |
| `com.example.appservice.sdk.util` | 脱敏、签名与指纹工具 | `MaskUtils`、`MockSignature`、`ReplaySignatureUtil` |
| `com.example.appservice.sdk.http` | HTTP 请求/响应包装器 | `CachedBodyHttpServletRequest`、`CachedBodyHttpServletResponse`、`CachedClientHttpResponse`、`MockClientHttpResponse` |
| `com.example.appservice.sdk.record` | 入站流量与出站调用录制 | `TrafficCaptureFilter`、`TrafficRecordBuffer`、`TrafficRecordSwitch`、`TrafficRecordSwitchController`、`TrafficReporter`、`ExternalCallRecordBuffer`、`OutboundHttpRecordingInterceptor` |
| `com.example.appservice.sdk.replay` | Dry-Run 回放、Mock 与防重放 | `ReplayFilter`、`ReplayRestTemplateInterceptor`、`MockResponseStore`、`NonceCache`、`RequiresNewDetector`、`ReplayTaskDecorator`、`ReplayMockMissingException` |

自动装配入口的全限定名已同步更新为 `com.example.appservice.sdk.config.AppRegistryAutoConfiguration`，并写入 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

### 5.1 自动装配与配置

| 类                              | 关键方法/配置          | 职责                                                                                  |
|--------------------------------|------------------|-------------------------------------------------------------------------------------|
| `AppRegistryAutoConfiguration` | 多个 `@Bean` 方法    | 自动装配入口，按配置条件注册录制、回放、心跳和 Mock Bean。                                                  |
| `AppRegistryProperties`        | `app.registry.*` | 配置载体，含 `TrafficRecordProperties`、`ExternalCallRecordProperties`、`ReplayProperties`。 |

自动装配中的关键 Bean 方法：

| 方法                                                       | 说明                           |
|----------------------------------------------------------|------------------------------|
| `appRegistryProperties`                                  | 绑定 `app.registry.*` 配置。      |
| `appIdFileStore`                                         | 本地凭据存储。                      |
| `controllerLogAspect`                                    | Controller 日志切面。             |
| `appRegistryClient`                                      | 注册与心跳 HTTP 客户端。              |
| `interfaceScanner`                                       | 接口扫描器。                       |
| `trafficRecordSwitch`                                    | 初始录制开关，后续由管理端或心跳同步。          |
| `heartbeatScheduler`                                     | 启动注册与定时心跳调度器。                |
| `appIdentityResolver`                                    | appId/token 解析器。             |
| `trafficRecordSwitchController`                          | 接收管理端录制开关下发。                 |
| `trafficRecordBuffer`、`externalCallRecordBuffer`         | 入站/出站记录缓冲队列。                 |
| `trafficCaptureFilter`                                   | 入站流量采集过滤器。                   |
| `outboundHttpRecordingInterceptor`                       | 出站 HTTP 调用录制拦截器。             |
| `recordingRestTemplateCustomizer`                        | 为普通 RestTemplate 添加录制拦截器。    |
| `trafficReporter`                                        | 定时批量上报器。                     |
| `replayNonceCache`                                       | 回放 nonce 去重缓存。               |
| `mockResponseStore`                                      | Mock 规则按 replayId 缓存。        |
| `replayRestTemplateInterceptor`                          | 回放态 Mock 拦截。                 |
| `replayRestTemplateCustomizer`                           | 为回放 RestTemplate 添加 Mock 拦截。 |
| `replayFilter`                                           | Dry-Run 回放入口过滤器。             |
| `requiresNewDetector`                                    | `REQUIRES_NEW` 风险扫描器。        |
| `replayTaskDecorator`、`replayTaskDecoratorPostProcessor` | 线程池中的回放上下文传播。                |

### 5.2 注册与心跳

| 类                     | 关键方法                                  | 职责                                                    |
|-----------------------|---------------------------------------|-------------------------------------------------------|
| `AppIdentityResolver` | `appId`、`token`                       | 优先从配置读取身份，缺失时回落到本地 appid 文件。                          |
| `AppIdFileStore`      | `resolvePath`、`read`、`write`、`delete` | 本地凭据文件读写，写入时采用原子替换并收敛权限。                              |
| `AppRegistryClient`   | `register`、`heartbeat`                | 调用管理端 `/api/agent/register` 和 `/api/agent/heartbeat`。 |
| `InterfaceScanner`    | `scan`                                | 扫描 Spring MVC 路由并推导请求/响应 Schema 和 `schemaHash`。       |
| `HeartbeatScheduler`  | `onReady`、`heartbeat`                 | 启动注册、定时心跳，并按心跳响应同步录制开关。                               |

### 5.3 入站流量录制

| 类                               | 关键方法                                                   | 职责                                  |
|---------------------------------|--------------------------------------------------------|-------------------------------------|
| `TrafficCaptureFilter`          | `doFilterInternal`                                     | 按开关、采样、路径规则和回放态决定是否录制；缓存请求/响应体后上报。  |
| `CachedBodyHttpServletRequest`  | `getInputStream`、`getReader`、`bodyBytes`               | 请求体可重复读包装。                          |
| `CachedBodyHttpServletResponse` | `getOutputStream`、`getWriter`、`bodyBytes`、`copyBodyTo` | 响应体缓存与回写包装。                         |
| `TrafficRecordBuffer`           | `offer`、`drain`、`size`、`droppedCount`                  | 有界流量队列，队列满时计数丢弃。                    |
| `TrafficRecordSwitch`           | `isActive`、`set`                                       | 录制开关和生效起始时间判断。                      |
| `TrafficRecordSwitchController` | `set`                                                  | 校验 appId/token 后更新本地录制开关。           |
| `TrafficReporter`               | `flush`、`stop`                                         | 定时批量上报入站/出站记录，停止时清理调度。              |
| `ControllerLogAspect`           | `log`                                                  | 环绕 `@RestController`，记录入参、返回、耗时和异常。 |

### 5.4 出站调用录制

| 类                                  | 关键方法                                                           | 职责                           |
|------------------------------------|----------------------------------------------------------------|------------------------------|
| `OutboundHttpRecordingInterceptor` | `intercept`                                                    | 非回放态下记录 RestTemplate 外部调用。   |
| `CachedClientHttpResponse`         | `getBody`、`bodyBytes`                                          | 客户端响应体重复读包装。                 |
| `ExternalCallRecordBuffer`         | `offer`、`drain`、`size`、`droppedCount`                          | 出站调用有界队列。                    |
| `MaskUtils`                        | `maskHeaders`、`maskBody`、`queryParamsJson`、`toJson`、`truncate` | Header/Body 脱敏、查询参数序列化和长度截断。 |

### 5.5 Dry-Run 回放、Mock 与上下文

| 类                               | 关键方法                                                  | 职责                                                                 |
|---------------------------------|-------------------------------------------------------|--------------------------------------------------------------------|
| `ReplayFilter`                  | `doFilterInternal`                                    | 校验回放头、时间窗、签名和 nonce；设置上下文；强制回滚事务；输出 Mock 统计头。                      |
| `ReplayRestTemplateInterceptor` | `intercept`                                           | 回放态按 `requestHash` 返回 Mock 响应；未命中时抛出 `ReplayMockMissingException`。 |
| `MockResponseStore`             | `load`、`find`、`hits`、`misses`、`reset`                 | 按 replayId 加载并匹配 Mock，维护命中统计。                                      |
| `MockClientHttpResponse`        | `getStatusCode`、`getBody`、`getHeaders`                | 由 Mock 规则构造模拟 `ClientHttpResponse`。                                |
| `MockSignature`                 | `of`                                                  | 出站 Mock 请求指纹计算，口径与录制侧 `RequestHashUtil` 保持一致。                      |
| `ReplaySignatureUtil`           | `sign`、`verify`                                       | 委托共享模块完成回放签名与验签。                                                   |
| `ReplayContext`                 | `isReplay`、`get`、`set`、`setReplay`、`replayId`、`clear` | 基于 ThreadLocal 的回放上下文管理。                                           |
| `ReplayInfo`                    | 无显式业务方法                                               | 承载 replayId、模式、时间戳、nonce 和签名。                                      |
| `ReplayMockMissingException`    | 构造函数                                                  | Mock 未命中异常，保证不回退到真实外部调用。                                           |
| `ReplayTaskDecorator`           | `decorate`                                            | 将回放上下文传播到线程池任务并清理。                                                 |
| `RequiresNewDetector`           | `detect`                                              | 扫描 `REQUIRES_NEW` 方法并告警，提示无法回滚风险。                                  |
| `NonceCache`                    | `putIfAbsent`                                         | 基于 TTL 和容量上限的 nonce 去重缓存。                                          |
| `TraceContext`                  | `set`、`get`、`clear`                                   | ThreadLocal traceId，串接上下游链路。                                       |

## 6. 维护约定

- 新增跨模块传输字段时，应同时更新共享 DTO、数据库实体/VO 和本文说明。
- 修改签名协议或请求指纹算法时，必须同步修改 SDK 与管理端实现，否则回放 Mock 会失效。
- 新增核心服务方法时，应补充 Javadoc，说明入参、返回值、异常和副作用，并更新本文对应表格。
- `REQUIRES_NEW`、异步线程和跨请求会话仍是 Dry-Run 第一期风险边界，相关代码改动需要同步评估回滚语义。

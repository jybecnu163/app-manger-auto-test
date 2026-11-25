# 流量录制与 Dry-Run 回放任务卡片

> 执行原则：按编号依次完成。T04 与 T05 可在 T03 完成后并行，但每个任务合入前都要保证编译通过。

---

### T01 公共协议 DTO 与回放枚举定义

- 前置依赖：无
- 目标模块：`app-service-common`
- 交付内容：
  - `TrafficRecordDTO`
  - `TrafficBatchDTO`
  - `ExternalCallRecordDTO`
  - `ReplayMode` 枚举
  - `TestResultVO` 扩展字段
- 主要字段：
  - `TrafficRecordDTO`：appId、traceId、method、path、queryParams、headers、body、responseStatus、responseBody、costMs、success、error、createdAt
  - `ExternalCallRecordDTO`：appId、traceId、method、url、requestHeaders、requestBody、responseStatus、responseHeaders、responseBody、costMs、requestHash
  - `TestResultVO`：replayId、replayMode、dbRolledBack、mockHits、mockMisses
- 验收标准：
  - 所有 DTO 可被 SDK 与 Server 同时引用。
  - JSON 序列化字段名稳定。
  - `mvn -DskipTests compile` 通过。

---

### T02 数据库表与实体/Mapper

- 前置依赖：T01
- 目标模块：`app-service-server`
- 交付内容：
  - `db/schema.sql` 增加 `traffic_record`
  - `db/schema.sql` 增加 `external_call_record`
  - `db/schema.sql` 增加 `mock_response`
  - `api_interface` 增加 `write_operation`
  - `test_log` 增加 replay 与 mock 字段
  - 新增对应 Entity 与 Mapper
- 验收标准：
  - 启动服务端后所有表自动创建。
  - 已有库执行新增字段不会破坏现有用例流程。
  - MyBatis-Plus Mapper 可正常 CRUD。

---

### T03 SDK 配置属性扩展

- 前置依赖：T01
- 目标模块：`app-sdk-spring-boot-starter`
- 交付内容：
  - `AppRegistryProperties` 增加：
    - `traffic-record`
    - `external-call-record`
    - `replay`
  - 新增配置绑定支持
  - 更新 `README.md` 或 `PLAN.md` 配置示例
- 验收标准：
  - demo 服务可通过 YAML 开启/关闭流量录制与回放。
  - 所有开关默认关闭。
  - 配置项有合理默认值。

---

### T04 SDK 入站流量采集 Filter

- 前置依赖：T03
- 目标模块：`app-sdk-spring-boot-starter`
- 交付内容：
  - `TrafficCaptureFilter`
  - 请求/响应包装
  - 采样、脱敏、截断
  - 丢弃计数
- 验收标准：
  - 正常请求不受影响。
  - 采样率配置生效。
  - exclude path 生效。
  - Header / Body 脱敏生效。
  - Body 超过限制时截断。
  - 读取 Body 不影响业务接口正常入参。
  - 队列满时丢弃且不抛异常到业务链路。

---

### T05 SDK 出站 HTTP 调用录制拦截器

- 前置依赖：T03
- 目标模块：`app-sdk-spring-boot-starter`
- 交付内容：
  - `OutboundHttpRecordingInterceptor`
  - `MockSignature`
  - RestTemplate 自动装配拦截器
  - 出站调用记录队列
- 验收标准：
  - 正常模式下 RestTemplate 调用的 URL、方法、请求体、响应状态、响应体被记录。
  - requestHash 对相同请求稳定。
  - Header / Body 脱敏生效。
  - 录制失败不影响真实外部调用。

---

### T06 SDK 批量上报器

- 前置依赖：T04、T05
- 目标模块：`app-sdk-spring-boot-starter`
- 交付内容：
  - `TrafficRecordBuffer`
  - `TrafficReporter`
  - 定时批量刷新
  - 重试与失败日志
- 验收标准：
  - 按 batch-size / flush-interval 批量上报。
  - 上报失败不影响业务。
  - 服务不可用时不会无限堆积。
  - SDK 关闭时不再继续上报。

---

### T07 Agent 批量上报接口

- 前置依赖：T02
- 目标模块：`app-service-server`
- 交付内容：
  - `POST /api/agent/traffic/records`
  - `POST /api/agent/external-call/records`
  - `TrafficRecordService`
  - `ExternalCallRecordService`
  - appId / token 校验
  - interfaceId 关联逻辑
- 验收标准：
  - SDK 上报后数据落库。
  - 能按 appId + method + path 关联 `api_interface`。
  - 重复请求可去重或至少不会导致页面异常。
  - token 错误时拒绝上报。

---

### T08 回放上下文与签名校验

- 前置依赖：T03
- 目标模块：`app-sdk-spring-boot-starter`
- 交付内容：
  - `ReplayFilter`
  - `ReplayContext`
  - `ReplayInfo`
  - `ReplaySignatureUtil`
  - nonce 去重缓存
  - `ReplayTaskDecorator`
- 验收标准：
  - 管理端构造的回放 Header 能被 SDK 识别。
  - 签名错误返回 403。
  - 时间戳过期返回 403。
  - nonce 重复返回 403。
  - 回放开关关闭时 Header 被忽略或拒绝，策略需明确。
  - 异步线程能传播 ReplayContext。

---

### T09 SDK 回放事务回滚

- 前置依赖：T08
- 目标模块：`app-sdk-spring-boot-starter`
- 交付内容：
  - `ReplayFilter` 中用 `TransactionTemplate` 包裹 Filter Chain
  - 事务结束后统一 `setRollbackOnly`
  - REQUIRES_NEW 风险检测与日志告警
- 验收标准：
  - Dry-Run 请求真实进入 Controller。
  - INSERT / UPDATE 在请求内可见。
  - 请求结束后数据库无新增或更新。
  - 普通非回放请求事务行为不变。
  - 异常请求也能正确回滚。

---

### T10 SDK 外部 HTTP Mock 拦截器

- 前置依赖：T08、T07
- 目标模块：`app-sdk-spring-boot-starter`
- 交付内容：
  - `ReplayRestTemplateInterceptor`
  - `MockResponseStore`
  - `MockClientHttpResponse`
  - `ReplayMockMissingException`
  - mockHits / mockMisses 统计
- 验收标准：
  - 回放模式下 RestTemplate 不发起真实网络调用。
  - 命中 Mock 时返回指定 status、headers、body。
  - 未命中 Mock 时抛异常并阻止真实调用。
  - mockHits / mockMisses 可通过响应头或上下文返回给管理端。
  - 非回放模式行为完全不变。

---

### T11 管理端 Mock 数据查询与维护接口

- 前置依赖：T02、T07
- 目标模块：`app-service-server`
- 交付内容：
  - `GET /api/admin/interfaces/{interfaceId}/mock-responses`
  - `POST /api/admin/mock-responses`
  - `MockResponseService`
  - 从 `external_call_record` 生成 Mock 规则
- 验收标准：
  - 可按 appId、interfaceId 查询 Mock 响应。
  - 可手工新增/启用/禁用 Mock 规则。
  - 生成的 requestHash 与 SDK 侧一致。

---

### T12 TestProxyService 回放转发扩展

- 前置依赖：T01、T08、T10
- 目标模块：`app-service-server`
- 交付内容：
  - `TestRequest` 增加 replayMode、trafficRecordId、mockResponseId
  - `ReplayHeaderService`
  - replayId 生成
  - timestamp、nonce、signature
  - 回放 Header 注入
  - 解析 SDK 返回的 mock 统计响应头
- 验收标准：
  - 回放请求仍真实调用业务实例。
  - 回放 Header 能被 SDK 校验通过。
  - 回放结果包含 replayId、replayMode、dbRolledBack、mockHits、mockMisses。
  - 非回放请求不受影响。

---

### T13 管理端流量录制页面

- 前置依赖：T07、T12
- 目标模块：`app-service-server/src/main/resources/static`
- 交付内容：
  - `app-detail.html` 新增 “流量录制” Tab
  - `js/traffic.js` 新增
  - `css/app.css` 样式补充
  - 查询、筛选、详情、Dry-Run 回放按钮
  - 回放结果展示
- 验收标准：
  - 能看到录制流量列表。
  - 支持按接口、方法、状态、时间筛选。
  - 能查看请求与响应详情。
  - 点击 Dry-Run 回放后展示业务响应和 Mock / 回滚统计。
  - 静态资源加版本号，避免缓存问题。

---

### T14 test_log 统计与回放结果持久化

- 前置依赖：T12
- 目标模块：`app-service-server`
- 交付内容：
  - 回放请求写入 test_log
  - 持久化 replayMode、replayId、dbRolledBack、mockHits、mockMisses
  - 测试统计区分真实测试与 Dry-Run
- 验收标准：
  - 每次 Dry-Run 都能在 test_log 查到。
  - 统计页面不把 Mock 回放与真实回放混在一起。
  - Mock 未命中也写入失败日志。

---

### T15 demo 服务联调验证

- 前置依赖：T04、T05、T09、T10、T12、T13
- 目标模块：`demo-product-service`、`demo-order-service`
- 交付内容：
  - demo 服务开启流量录制与回放配置。
  - 构造一个含外部 HTTP 调用的写接口，或增加测试用 Controller。
  - 完成端到端验证。
- 验收标准：
  - GET 接口流量能被录制。
  - POST 写接口流量能被录制。
  - Dry-Run 回放真实进入 Controller。
  - 回放结束后业务库没有新增/更新数据。
  - 外部 HTTP 调用返回 Mock 数据。
  - 删除 Mock 规则后回放失败，不调用真实外部接口。
  - 管理端正确展示统计。

---

### T16 文档收尾与风险说明

- 前置依赖：T15
- 目标模块：文档
- 交付内容：
  - 更新 `README.md`
  - 更新 `PLAN.md`
  - 明确第一期限制：
    - 仅事务回滚，不是影子库；
    - 多步骤链路暂不支持；
    - MQ / Redis / 文件 Mock 暂不支持；
    - 生产环境默认关闭；
    - Service 不应使用 REQUIRES_NEW
- 验收标准：
  - 新成员可按文档完成配置与验证。
  - 风险边界描述清晰。

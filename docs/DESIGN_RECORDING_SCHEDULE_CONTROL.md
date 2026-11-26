# 管理端录制起始时间控制设计说明书

| 项目 | 内容 |
|---|---|
| 版本 | v1.0 |
| 日期 | 2026-11-26 |
| 状态 | 待实现 |
| 目标模块 | `app-service-server`、`app-sdk-spring-boot-starter`、`app-service-common` |
| 适用范围 | 管理端页面控制业务系统入站流量录制的开关与起始时间 |

## 1. 背景与目标

当前流量录制由业务系统本地 `app.registry.traffic-record.enabled` 静态配置控制，管理端页面只能查询已录制数据，无法控制录制开始时机。

本期目标：

1. 管理端“流量录制”Tab 增加“开始录制”“结束录制”按钮和“起始时间”输入框。
2. 点击“开始录制”时，可以把录制任务配置为立即开始，或指定未来某个时间点开始。
3. 服务端持久化每个 app 的录制开关与起始时间。
4. 在线实例立即收到控制指令；离线实例在下一次心跳时自动同步。
5. SDK 在未到起始时间前不采集流量，到达起始时间后自动开始采集。
6. 点击“结束录制”后立即停止采集，并清除起始时间。

## 2. 本期范围

### 2.1 包含

1. 公共协议：`HeartbeatResponse`、`TrafficRecordSwitchRequest` 扩展。
2. 数据库：`app` 表增加 `traffic_record_enabled`、`traffic_record_start_at`。
3. SDK：动态录制开关、起始时间判断、控制接口、心跳同步。
4. 服务端：控制接口、在线实例下发、状态查询。
5. 页面：按钮、起始时间输入、状态展示。
6. 执行日志与最终文档更新。

### 2.2 不包含

1. 外部 HTTP 调用录制的独立开关。
2. 复杂调度规则（循环、cron、按接口单独设置）。
3. 管理端登录鉴权。
4. 录制数据保留策略。

## 3. 总体流程

```mermaid
flowchart LR
    A[页面选择起始时间并点击开始录制] --> B[POST /api/admin/apps/{appId}/traffic-record/enabled]
    B --> C[Server 更新 app 表状态与时间]
    C --> D[Server 查找在线实例]
    D --> E[POST 实例 /api/agent/traffic-record/enabled]
    E --> F[SDK 更新动态开关与起始时间]
    F --> G{当前时间 >= 起始时间}
    G -- 否 --> H[暂不采集]
    G -- 是 --> I[TrafficCaptureFilter 采集流量]
    I --> J[TrafficReporter 批量上报]
    J --> K[管理端查询展示]
    H --> L[心跳响应同步状态]
    L --> F
```

## 4. 状态模型

每个 app 维护两个字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `traffic_record_enabled` | tinyint | 是否开启录制任务 |
| `traffic_record_start_at` | datetime | 计划起始时间；为空表示立即生效 |

页面显示三种状态：

| 条件 | 页面状态 |
|---|---|
| `enabled=false` | 已停止 |
| `enabled=true` 且 `startAt=null` 或 `startAt<=now` | 录制中 |
| `enabled=true` 且 `startAt>now` | 待开始 |
| `enabled=true` 且 `startAt<=now` | 录制中 |

SDK 实际采集条件：

```text
enabled == true 且 (startAt == null 或 now >= startAt)
```

## 5. 接口契约

### 5.1 管理端设置开关

```text
POST /api/admin/apps/{appId}/traffic-record/enabled?enabled=true|false
```

请求体可选，用于携带起始时间：

```json
{
  "startAt": "2026-11-26T15:30:00"
}
```

- `enabled=true`：开启录制；`startAt` 为空表示立即开始。
- `enabled=false`：停止录制；服务端清空 `startAt`。

响应：

```json
{"code": 0, "message": "ok", "data": null}
```

### 5.2 管理端查询状态

复用 `GET /api/admin/apps`，列表项新增：

```json
{
  "appId": "APP0002",
  "name": "order-service",
  "trafficRecordEnabled": true,
  "trafficRecordStartAt": "2026-11-26T15:30:00"
}
```

### 5.3 SDK 接收控制

```text
POST /api/agent/traffic-record/enabled
```

请求体：

```json
{
  "appId": "APP0002",
  "token": "order-demo-token",
  "enabled": true,
  "startAt": "2026-11-26T15:30:00"
}
```

SDK 校验 appId/token 与本地身份一致后更新动态开关。

### 5.4 心跳响应

```text
POST /api/agent/heartbeat
```

响应 `data` 新增：

```json
{
  "trafficRecordEnabled": true,
  "trafficRecordStartAt": "2026-11-26T15:30:00"
}
```

SDK 每次心跳后同步状态，处理离线期间的下发丢失。

## 6. 关键实现

### 6.1 SDK 动态开关

新增 `TrafficRecordSwitch`：

- `volatile boolean enabled`
- `volatile LocalDateTime startAt`
- `isActive()`：判断当前是否应该采集
- `set(enabled, startAt)`

SDK 的 `TrafficCaptureFilter` 从静态 `properties.getTrafficRecord().isEnabled()` 改为调用 `recorderSwitch.isActive()`。

SDK 的 Filter 与 Buffer Bean 不再依赖 YAML 开关创建，始终注册；YAML 中的 `enabled` 只作为动态开关初始值。

### 6.2 停止后数据上报

停止录制后，SDK 不再采集新请求，但 `TrafficReporter` 仍继续 flush 已缓存的流量，避免数据丢失。

### 6.3 服务端下发

`RecordingControlService`：

1. 更新 `app` 表状态和起始时间。
2. 查询在线实例。
3. 通过 `http://ip:port/api/agent/traffic-record/enabled` 下发控制请求。
4. 单个实例失败只记日志，不影响 DB 状态和其他实例。
5. 离线实例由心跳响应兜底同步。

## 7. 安全与校验

1. SDK 控制接口校验请求中的 appId/token 与本地身份一致。
2. 管理端接口位于 `/api/admin/**`，与现有管理接口同域。
3. `startAt` 允许为空；非法时间格式由 Spring/Jackson 返回参数错误。
4. 停止录制时强制清空 `startAt`。
5. 仅控制入站流量录制，不改变回放与 Mock 行为。

## 8. 测试方案

1. 全项目 `mvn -DskipTests compile` 通过。
2. 启动管理端与 demo-order-service。
3. 页面点击“结束录制”，调用 demo 接口，管理端不新增流量。
4. 设置“起始时间”为未来 10 秒后点击“开始录制”，状态显示“待开始”；时间到达后状态变“录制中”。
5. 调用 demo 接口，管理端能查到新流量。
6. 点击“结束录制”，状态变“已停止”，再次调用接口不新增流量。
7. 重启 demo 后，状态从心跳响应恢复。

## 9. 风险与限制

1. 时间依赖业务机器与管理端机器时钟一致，生产环境应接入 NTP。
2. 下发依赖管理端能访问业务实例 `advertise-ip:port`；不通时依赖心跳同步。
3. 心跳间隔默认 30 秒，离线恢复同步存在最长一个心跳周期的延迟。
4. 秒级以下精度不做保证。
5. 本功能只控制“入站流量录制”，外部 HTTP 调用录制仍由 YAML 独立控制。

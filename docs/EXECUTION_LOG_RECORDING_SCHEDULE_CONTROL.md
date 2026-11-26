# 录制起始时间控制执行日志

| 项目 | 内容 |
|---|---|
| 日期 | 2026-11-26 |
| 范围 | 管理端页面控制流量录制开关与起始时间 |
| 设计文档 | `docs/DESIGN_RECORDING_SCHEDULE_CONTROL.md` |
| 任务清单 | `docs/TASKS_RECORDING_SCHEDULE_CONTROL.md` |

## 前置：设计文档与任务清单

- 新增设计文档和任务清单，明确状态模型、接口契约、SDK 动态开关、心跳同步与验证方式。
- 提交：`22d131a docs: 录制起始时间控制设计与任务清单`

## T01 协议与持久化字段

- 新增 `HeartbeatResponse`，携带 `trafficRecordEnabled`、`trafficRecordStartAt`。
- 新增 `TrafficRecordSwitchRequest`。
- `schema.sql` 幂等增加 `app.traffic_record_enabled`、`app.traffic_record_start_at`。
- `App`、`AppListItemVO` 增加对应字段，`AppService.listApps()` 返回状态。
- 验证命令：`mvn -DskipTests compile -q`，退出码 0。
- 提交：`3232941 T01: 协议与持久化字段`
- 问题：无。

## T02 SDK 动态录制开关

- 新增 `TrafficRecordSwitch`，按 `enabled + startAt` 判断是否真正采集。
- 新增 `TrafficRecordSwitchController`，接收管理端下发的控制请求。
- `TrafficCaptureFilter` 改为读取动态开关，不再依赖 YAML 静态开关。
- `TrafficReporter` 移除静态开关判断，保证停止录制后仍上报已缓存数据。
- `AppRegistryClient` / `HeartbeatScheduler` 从心跳响应同步录制状态。
- `AppRegistryAutoConfiguration` 将录制 Filter/Buffer 改为常驻 Bean。
- 验证命令：`mvn -DskipTests compile -q`，退出码 0。
- 提交：`f3f2284 T02: SDK 动态录制开关`
- 问题：无。

## T03 服务端控制接口与下发

- 新增 `RecordingControlService`：持久化状态并下发到在线实例。
- 新增 `POST /api/admin/apps/{appId}/traffic-record/enabled`。
- `AppService.heartbeat` 改为返回 `HeartbeatResponse`。
- `AgentController.heartbeat` 返回录制状态。
- 单个实例下发失败只记录日志，不影响其他实例。
- 验证命令：`mvn -DskipTests compile -q`，退出码 0。
- 提交：`c8f88c5 T03: 服务端控制接口与下发`
- 问题：无。

## T04 页面按钮与起始时间输入

- 详情页“流量录制”Tab 增加：
  - 录制状态显示（已停止 / 待开始 / 录制中）
  - 起始时间输入框
  - “开始录制”“结束录制”按钮
- `traffic.js` 增加 `loadRecordingStatus()`、`setRecording(enabled)` 并绑定按钮。
- `app.css` 增加 `.record-control` 与状态色样式。
- 静态资源版本号递增到 `20261126a`。
- 验证：
  - `node --check static/js/traffic.js`，退出码 0。
  - `mvn -DskipTests compile -q`，退出码 0。
- 提交：`8a3c7e0 T04: 页面按钮与起始时间输入`
- 问题：无。

## T05 执行日志

- 本文件记录 T01–T04 的变更、验证命令、结果与提交号。
- 提交：`214897f T05: 执行日志`

## T06 端到端验证与最终文档

- 构建：`mvn -DskipTests package -q` 通过。
- 启动管理端与 demo-order-service，验证状态流转：
  - 初始：`enabled=False`，调用 3 次 `GET /api/orders` 后流量数不变（14→14）。
  - 设置未来起始时间：`enabled=True`、`startAt` 非空；起始时间前调用 3 次接口，流量数不变（14→14）。
  - 到点后调用 3 次接口，流量数增加（14→17）。
  - 点击结束录制：`enabled=False`；继续调用接口不再新增流量。
  - 设置立即开始后重启 demo：`enabled=True`、`onlineInstances=1`，心跳同步生效；调用接口流量数增加（18→20）。
- 发现问题：停止录制后 `startAt` 未清空。原因是 MyBatis-Plus 默认 update 策略忽略 null 字段。
- 修复：改用 `LambdaUpdateWrapper.set(App::getTrafficRecordStartAt, null)` 显式清空。
- 修复过程提交：`5b34809 fix: 停止录制时清空起始时间`、`7d7a131 fix: 修正布尔字段方法引用`。
- 修复后复验：`SCHEDULED enabled=True startAtSet=True`、`STOPPED enabled=False startAtCleared=True`。
- 最终验证：`mvn -DskipTests compile -q` 通过。
- 文档：README 增加页面控制说明，设计文档状态更新为“已实现”。

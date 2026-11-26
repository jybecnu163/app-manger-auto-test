# 管理端录制起始时间控制任务卡片

> 执行原则：按编号依次完成，每个任务合入前保证编译通过；最后写执行日志并更新最终文档。

---

### T01 协议与持久化字段

- 前置依赖：无
- 目标模块：`app-service-common`、`app-service-server`
- 交付内容：
  - `HeartbeatResponse` 扩展 `trafficRecordEnabled`、`trafficRecordStartAt`
  - 新增 `TrafficRecordSwitchRequest`
  - `app` 表新增 `traffic_record_enabled`、`traffic_record_start_at`
  - `App`、`AppListItemVO` 增加对应字段
  - `AppService.listApps()` 返回状态字段
- 验收标准：
  - `mvn -DskipTests compile` 通过
  - schema.sql 对已有库可重复执行

---

### T02 SDK 动态录制开关

- 前置依赖：T01
- 目标模块：`app-sdk-spring-boot-starter`
- 交付内容：
  - `TrafficRecordSwitch`
  - `TrafficRecordSwitchController`
  - `TrafficCaptureFilter` 使用动态开关
  - `TrafficReporter` 停止后仍 flush 缓存
  - `AppRegistryClient` / `HeartbeatScheduler` 心跳同步
  - `AppRegistryAutoConfiguration` 注册相关 Bean
- 验收标准：
  - YAML 开关作为初始值，但页面可动态覆盖
  - 未到起始时间不采集
  - 到起始时间后自动采集
  - 编译通过

---

### T03 服务端控制接口与下发

- 前置依赖：T01、T02
- 目标模块：`app-service-server`
- 交付内容：
  - `RecordingControlService`
  - `POST /api/admin/apps/{appId}/traffic-record/enabled`
  - 心跳接口返回录制状态
- 验收标准：
  - 设置状态后 DB 更新
  - 在线实例收到下发
  - 单个实例下发失败不影响其他实例
  - 编译通过

---

### T04 页面按钮与起始时间输入

- 前置依赖：T03
- 目标模块：`app-service-server/src/main/resources/static`
- 交付内容：
  - `app-detail.html` 流量录制面板增加状态、按钮、起始时间输入
  - `traffic.js` 增加状态加载与设置逻辑
  - `app.css` 增加控制区样式
  - 静态资源版本号递增
- 验收标准：
  - 页面显示“已停止 / 待开始 / 录制中”
  - 可选择起始时间并开始录制
  - 可结束录制并清空起始时间
  - JS 语法检查通过

---

### T05 执行日志

- 前置依赖：T01–T04
- 交付内容：
  - `docs/EXECUTION_LOG_RECORDING_SCHEDULE_CONTROL.md`
  - 逐卡片记录变更文件、验证命令、结果、问题与处理
- 验收标准：
  - 日志覆盖 T01–T04
  - 记录真实验证结果

---

### T06 端到端验证与最终文档

- 前置依赖：T05
- 交付内容：
  - 端到端验证“停止 → 未来起始时间 → 待开始 → 录制中 → 停止”
  - README 增加页面控制说明
  - 设计文档状态更新为“已实现”
- 验收标准：
  - 全项目编译通过
  - 端到端状态流转验证通过
  - 最终文档与实现一致

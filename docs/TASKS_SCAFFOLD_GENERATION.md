# 空壳业务系统生成任务卡片

> 执行原则：按编号依次完成，每个任务合入前保证编译通过。

---

### T01 生成协议与 ZIP 生成服务

- 前置依赖：无
- 目标模块：`app-service-server`
- 交付内容：
  - `ScaffoldRequest`
  - `ScaffoldResult`
  - `ScaffoldService`
- 主要内容：
  - 接收系统名、端口、环境、管理端地址
  - 清洗系统名、校验端口
  - 生成 appId 与 token
  - 生成 `pom.xml`、`application.yml`、启动类、`HealthController`、`README.md`
  - 打包为 ZIP 字节数组
- 验收标准：
  - `mvn -DskipTests compile` 通过
  - ZIP 内容结构完整
  - 非法系统名或端口被安全处理或拒绝

---

### T02 管理端生成接口

- 前置依赖：T01
- 目标模块：`app-service-server`
- 交付内容：
  - `POST /api/admin/scaffolds`
  - `ScaffoldController`
- 主要内容：
  - `serverUrl` 为空时从当前请求推导
  - 返回 `application/zip`
  - 设置 `Content-Disposition` 下载文件名
  - 通过响应头返回 appId 与 token
- 验收标准：
  - 接口返回 200 且可下载 ZIP
  - 响应头包含 `X-Generated-App-Id`、`X-Generated-Token`
  - 参数错误时返回 `Result.fail`

---

### T03 管理端页面入口

- 前置依赖：T02
- 目标模块：`app-service-server/src/main/resources/static`
- 交付内容：
  - `index.html` 增加“生成空壳系统”按钮与弹窗
  - `js/app.js` 增加下载逻辑
  - `css/app.css` 增加弹窗样式
  - 静态资源版本号递增
- 主要内容：
  - 填写系统名、端口、环境
  - 调用生成接口并下载 ZIP
  - 展示 appId、token 与启动说明
  - 生成后刷新系统列表
- 验收标准：
  - 页面可打开弹窗
  - 点击生成可下载 ZIP
  - 页面展示生成信息
  - JS 语法检查通过

---

### T04 端到端验证与文档收尾

- 前置依赖：T03
- 目标模块：`app-service-server`、文档
- 交付内容：
  - 生成项目解压、编译验证
  - 启动生成项目并验证自动注册
  - 验证 `GET /api/health`
  - 更新 README（如需要）
- 验收标准：
  - 生成项目可编译
  - 启动后出现在管理端系统列表
  - 健康检查接口返回 `status=UP`
  - 全项目 `mvn -DskipTests compile` 通过

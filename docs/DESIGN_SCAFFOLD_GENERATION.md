# 空壳业务系统生成功能设计说明书

| 项目 | 内容 |
|---|---|
| 版本 | v1.0 |
| 日期 | 2026-11-25 |
| 状态 | 待实现 |
| 目标模块 | `app-service-server` |
| 适用范围 | 管理端生成可自动注册的 Spring Boot 空壳业务系统 |

## 1. 背景与目标

当前管理端只能管理已经接入 SDK 的业务系统。新成员要验证注册、心跳、接口扫描、流量录制或 Dry-Run 时，需要手工搭建一个 Spring Boot 工程并配置 SDK，成本较高。

本功能目标：

1. 管理端填写系统名称、端口、环境后，一键生成一个可运行的空壳业务系统 ZIP 包。
2. 生成项目内置 `GET /api/health` 健康检查接口。
3. 生成项目预置 SDK 配置（管理端地址、appId、token、实例 IP），启动后自动注册到当前管理端并持续上报心跳。
4. 生成过程不写服务端任意目录，不启动外部进程，只返回压缩包。

## 2. 本期范围

### 2.1 本期包含

1. 后端 ZIP 生成服务与接口。
2. 管理端系统列表页“生成空壳系统”入口与参数弹窗。
3. 生成项目包含：启动类、`HealthController`、`application.yml`、`pom.xml`、`README.md`。
4. 自动生成 appId 与 token，并通过响应头和 README 返回。
5. 从当前请求推导管理端 `server-url`，减少手工输入。
6. 端到端验证：生成、解压、编译、启动、自动注册、健康检查。

### 2.2 本期不包含

1. 自动生成后直接启动业务系统。
2. 生成带数据库、MQ、复杂业务代码的完整项目。
3. 生成项目的在线升级或代码回写。
4. 管理端登录鉴权。
5. 端口占用检测与端口自动分配。

## 3. 总体流程

```mermaid
flowchart LR
    A[管理端填写系统名/端口/环境] --> B[POST /api/admin/scaffolds]
    B --> C[ScaffoldService 生成 ZIP 字节数组]
    C --> D[响应 application/zip + 生成信息响应头]
    D --> E[浏览器下载并解压]
    E --> F[启动生成项目]
    F --> G[SDK 读取 app.registry 配置]
    G --> H[自动 register + heartbeat]
    H --> I[系统出现在管理端列表]
    I --> J[GET /api/health 返回 UP]
```

## 4. 生成项目结构

```text
scaffold-{artifactId}-{port}.zip
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/example/generated/app/
    │   ├── GeneratedApplication.java
    │   └── HealthController.java
    └── resources/application.yml
```

### 4.1 `pom.xml`

- 继承 `spring-boot-starter-parent:3.3.5`。
- Java 17。
- 依赖 `spring-boot-starter-web`。
- 依赖 `app-sdk-spring-boot-starter:1.0.0`。
- 使用 `spring-boot-maven-plugin` 打包。

### 4.2 `HealthController`

```text
GET /api/health
```

返回：

```json
{
  "status": "UP",
  "systemName": "demo-app",
  "timestamp": "2026-11-25T22:00:00"
}
```

### 4.3 `application.yml`

```yaml
server:
  port: 18080
spring:
  application:
    name: demo-app
app:
  registry:
    server-url: http://localhost:8080
    system-name: demo-app
    env: dev
    advertise-ip: 127.0.0.1
    appid: APP-DEMOAPP-A1B2C3
    token: 8f14e45f-eaee-47fc-bd26-1f6fe4a9e8c1
```

SDK 在 `ApplicationReadyEvent` 后调用注册与心跳，因此生成项目启动后会自动出现在管理端系统列表。

## 5. 接口契约

### 5.1 生成接口

```text
POST /api/admin/scaffolds
```

请求体：

```json
{
  "systemName": "demo-app",
  "port": 18080,
  "env": "dev",
  "serverUrl": "http://localhost:8080"
}
```

`serverUrl` 可为空，为空时服务端从当前请求推导。

响应：

- `Content-Type: application/zip`
- `Content-Disposition: attachment; filename="scaffold-demo-app-18080.zip"`
- `X-Generated-App-Id: APP-DEMOAPP-A1B2C3`
- `X-Generated-Token: 8f14e45f-eaee-47fc-bd26-1f6fe4a9e8c1`
- Body：ZIP 二进制内容

### 5.2 错误响应

沿用现有 `Result.fail(message)`，例如端口非法：

```json
{
  "code": 1,
  "message": "port 必须在 1024-65535 之间",
  "data": null
}
```

## 6. 关键设计

### 6.1 类职责

| 类 | 模块 | 职责 |
|---|---|---|
| `ScaffoldRequest` | `app-service-server/vo` | 接收系统名、端口、环境、管理端地址 |
| `ScaffoldResult` | `app-service-server/vo` | 承载 ZIP 字节、文件名、appId、token |
| `ScaffoldService` | `app-service-server/service` | 校验参数、生成文件文本、打包 ZIP |
| `ScaffoldController` | `app-service-server/controller` | 暴露生成接口，设置下载响应头 |

### 6.2 命名与标识

1. `systemName` 只允许字母、数字、`_`、`-`，其他字符替换为 `-`。
2. `artifactId` = 小写 `systemName`，`_` 转为 `-`。
3. `appId` = `APP-` + 大写无横线系统名 + `-` + 6 位随机十六进制后缀。
4. `token` = UUID。
5. ZIP 文件名 = `scaffold-{artifactId}-{port}.zip`。

### 6.3 管理端地址推导

优先使用请求体中的 `serverUrl`；为空时使用 `ServletUriComponentsBuilder.fromCurrentContextPath()` 推导，避免用户手填端口错误。

## 7. 安全与校验

1. 接口位于 `/api/admin/**`，与现有管理端接口同域。
2. 不接收输出目录参数，避免任意路径写入。
3. 端口限制在 `1024-65535`。
4. `systemName` 做白名单清洗，避免生成非法 Java 文件/路径。
5. 生成项目默认 `advertise-ip: 127.0.0.1`，适合本地联调。
6. 不自动启动进程，不执行用户脚本。

## 8. 测试方案

### 8.1 编译验证

1. 全项目 `mvn -DskipTests compile` 通过。
2. 生成的空壳项目 `mvn -DskipTests compile` 通过。

### 8.2 接口验证

1. 调用 `POST /api/admin/scaffolds` 返回 200 与 ZIP。
2. 校验响应头包含 appId、token。
3. 非法端口返回失败结果。

### 8.3 端到端验证

1. 解压生成项目。
2. 启动生成项目。
3. 管理端系统列表出现该系统，且状态在线。
4. `GET /api/health` 返回 `status=UP`。
5. 详情页能看到 `/api/health` 接口。

## 9. 风险与限制

1. 生成项目依赖本地 Maven 仓库中已安装 `app-sdk-spring-boot-starter:1.0.0`，因此 README 要求先执行根目录 `mvn install`。
2. 端口冲突不会自动处理，需用户选择空闲端口。
3. 生成项目仅适用于 Spring Boot 3 / Java 17 Servlet Web 环境。
4. appId 随机后缀降低冲突概率，但极端情况下仍可能与已有 appId 重合；注册接口会按 appId 幂等处理。
5. 本功能不替代正式项目脚手架，生成内容仅用于接入与联调验证。

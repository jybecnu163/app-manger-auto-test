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

## 本地验证示例

仓库内自带两个示例系统，用于验证注册、接口上报和测试转发：

| 模块 | 端口 | 系统名 | 主要接口 |
|---|---|---|---|
| `demo-product-service` | 8081 | `product-service` | `GET/POST /api/products/**`（含分类、库存、搜索） |
| `demo-order-service` | 8082 | `order-service` | `GET/POST/DELETE /api/orders/**` |

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

- `product-service`：`APP-PRODUCT-DEMO-0001` / `product-demo-token`
- `order-service`：`APP-ORDER-DEMO-0001` / `order-demo-token`

两个示例系统首次启动时会调用 `/api/agent/register`，服务端按配置中的 appid 自动创建 `app` 记录，无需在 `schema.sql` 中预置数据。`advertise-ip` 固定为 `127.0.0.1`，确保管理服务端能转发到本地实例。

## 当前范围

- 仅扫描 Spring MVC 路由，不含 Feign/Dubbo。
- 未做注册密钥与管理端登录，建议在内网使用。
- 管理页面使用原生 HTML + JS，无前端构建步骤。

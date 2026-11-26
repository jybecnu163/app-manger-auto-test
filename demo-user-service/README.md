# user-service

由系统管理平台生成的空壳业务系统。

## 启动前准备

先在管理平台根目录执行一次 `mvn install`，确保 SDK 依赖可用。

## 启动

```bash
mvn clean package
java -jar target/user-service-1.0.0.jar
```

## 自动注册信息

- 管理端地址：http://localhost:8080
- appId：APP-USERSERVICE-7097CB
- token：332d8bd7-c424-47b3-86c1-7423e5ca4f04
- 端口：8083

启动后 SDK 会自动向管理端注册并上报心跳。

## 健康检查

```bash
curl http://localhost:8083/api/health
```

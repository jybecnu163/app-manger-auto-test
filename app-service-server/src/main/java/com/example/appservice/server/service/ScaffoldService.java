package com.example.appservice.server.service;

import com.example.appservice.server.vo.ScaffoldRequest;
import com.example.appservice.server.vo.ScaffoldResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ScaffoldService {

    public ScaffoldResult generate(ScaffoldRequest request) {
        String systemName = safeName(request.getSystemName());
        int port = request.getPort();
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("port 必须在 1024-65535 之间");
        }
        String env = StringUtils.hasText(request.getEnv()) ? request.getEnv().trim() : "dev";
        String serverUrl = trimSlash(request.getServerUrl());
        if (!StringUtils.hasText(serverUrl)) {
            throw new IllegalArgumentException("serverUrl 不能为空");
        }

        String artifactId = systemName.toLowerCase(Locale.ROOT).replace('_', '-');
        String applicationClass = applicationClassName(systemName);
        String appId = "APP-" + systemName.toUpperCase(Locale.ROOT).replace("-", "")
                + "-" + randomSuffix();
        String token = UUID.randomUUID().toString();
        String fileName = "scaffold-" + artifactId + "-" + port + ".zip";

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            String prefix = artifactId + "/";
            write(zip, prefix + "pom.xml", pom(artifactId));
            write(zip, prefix + "README.md",
                    readme(systemName, artifactId, appId, token, port, serverUrl));
            write(zip, prefix + "src/main/java/com/example/generated/app/" + applicationClass + ".java",
                    applicationClass(applicationClass));
            write(zip, prefix + "src/main/java/com/example/generated/app/HealthController.java",
                    healthController(systemName));
            write(zip, prefix + "src/main/resources/application.yml",
                    yaml(port, systemName, env, appId, token, serverUrl));
        } catch (IOException e) {
            throw new IllegalStateException("生成空壳系统失败", e);
        }
        return new ScaffoldResult(output.toByteArray(), fileName, appId, token);
    }

    private String pom(String artifactId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.3.5</version>
                        <relativePath/>
                    </parent>
                    <groupId>com.example.generated</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <java.version>17</java.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>app-sdk-spring-boot-starter</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(artifactId);
    }

    private String yaml(int port, String systemName, String env,
                        String appId, String token, String serverUrl) {
        return """
                server:
                  port: %d
                spring:
                  application:
                    name: %s
                app:
                  registry:
                    server-url: %s
                    system-name: %s
                    env: %s
                    advertise-ip: 127.0.0.1
                    appid: %s
                    token: %s
                """.formatted(port, systemName, serverUrl, systemName, env, appId, token);
    }

    private String applicationClass(String className) {
        return """
                package com.example.generated.app;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class %s {
                    public static void main(String[] args) {
                        SpringApplication.run(%s.class, args);
                    }
                }
                """.formatted(className, className);
    }

    private String healthController(String systemName) {
        return """
                package com.example.generated.app;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                import java.time.LocalDateTime;
                import java.util.LinkedHashMap;
                import java.util.Map;

                @RestController
                @RequestMapping("/api/health")
                public class HealthController {

                    @GetMapping
                    public Map<String, Object> health() {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("status", "UP");
                        result.put("systemName", "%s");
                        result.put("timestamp", LocalDateTime.now().toString());
                        return result;
                    }
                }
                """.formatted(systemName);
    }

    private String readme(String systemName, String artifactId, String appId,
                          String token, int port, String serverUrl) {
        return "# " + systemName + "\n\n"
                + "由系统管理平台生成的空壳业务系统。\n\n"
                + "## 启动前准备\n\n"
                + "先在管理平台根目录执行一次 `mvn install`，确保 SDK 依赖可用。\n\n"
                + "## 启动\n\n"
                + "```bash\n"
                + "mvn clean package\n"
                + "java -jar target/" + artifactId + "-1.0.0.jar\n"
                + "```\n\n"
                + "## 自动注册信息\n\n"
                + "- 管理端地址：" + serverUrl + "\n"
                + "- appId：" + appId + "\n"
                + "- token：" + token + "\n"
                + "- 端口：" + port + "\n\n"
                + "启动后 SDK 会自动向管理端注册并上报心跳。\n\n"
                + "## 健康检查\n\n"
                + "```bash\n"
                + "curl http://localhost:" + port + "/api/health\n"
                + "```\n";
    }

    private void write(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String safeName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("[^A-Za-z0-9_-]", "-");
        return name.isEmpty() ? "generated-app" : name;
    }

    private String applicationClassName(String systemName) {
        StringBuilder builder = new StringBuilder();
        for (String part : safeName(systemName).split("[-_]+")) {
            if (part.isEmpty()) continue;
            String normalized = part.equals(part.toUpperCase(Locale.ROOT))
                    ? part.toLowerCase(Locale.ROOT) : part;
            builder.append(Character.toUpperCase(normalized.charAt(0)))
                    .append(normalized.substring(1));
        }
        String prefix = builder.length() == 0 ? "Generated" : builder.toString();
        if (Character.isDigit(prefix.charAt(0))) {
            prefix = "App" + prefix;
        }
        return prefix + "Application";
    }

    private String trimSlash(String url) {
        return url == null || !url.endsWith("/") ? url : url.substring(0, url.length() - 1);
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }
}

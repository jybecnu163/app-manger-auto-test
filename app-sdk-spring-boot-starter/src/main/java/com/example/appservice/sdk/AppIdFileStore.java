package com.example.appservice.sdk;

import com.example.appservice.common.dto.RegisterResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;

public class AppIdFileStore {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String resolvePath(AppRegistryProperties properties) {
        if (StringUtils.hasText(properties.getAppidFile())) {
            return properties.getAppidFile();
        }
        String name = StringUtils.hasText(properties.getSystemName()) ? properties.getSystemName() : "unknown-system";
        String safeName = name.replaceAll("[^A-Za-z0-9_.-]", "_");
        return Paths.get(System.getProperty("user.home"), ".app-service",
                safeName + "-" + properties.getEnv() + ".appid").toString();
    }

    public StoredApp read(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        Path file = Paths.get(path);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return objectMapper.readValue(Files.readAllBytes(file), StoredApp.class);
        } catch (IOException e) {
            return null;
        }
    }

    public void delete(String path) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException ignored) {
            // 删除失败时下次启动仍会尝试重新注册
        }
    }

    public void write(String path, RegisterResponse response, String systemName, String env) throws IOException {
        Path target = Paths.get(path);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StoredApp stored = new StoredApp();
        stored.setAppId(response.getAppId());
        stored.setToken(response.getToken());
        stored.setSystemName(systemName);
        stored.setEnv(env);
        stored.setCreatedAt(LocalDateTime.now().toString());

        Path temp = Files.createTempFile(parent == null ? Paths.get(".") : parent, "appid-", ".tmp");
        objectMapper.writeValue(temp.toFile(), stored);
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ignored) {
            // Windows 或文件系统不支持 POSIX 权限时忽略
        }
    }

    @Data
    public static class StoredApp {
        private String appId;
        private String token;
        private String systemName;
        private String env;
        private String createdAt;
    }
}

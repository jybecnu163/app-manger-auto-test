package com.example.appservice.sdk;

import com.example.appservice.common.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private final AppRegistryProperties properties;
    private final AppRegistryClient client;
    private final InterfaceScanner scanner;
    private final AppIdFileStore fileStore;
    private final Environment environment;
    private final long startTime = System.currentTimeMillis();

    private String appId;
    private String token;
    private String appidFilePath;
    private InstanceInfoDTO instance;
    private List<InterfaceMetaDTO> interfaces;
    private String interfacesHash;

    public HeartbeatScheduler(AppRegistryProperties properties,
                              AppRegistryClient client,
                              InterfaceScanner scanner,
                              AppIdFileStore fileStore,
                              Environment environment) {
        this.properties = properties;
        this.client = client;
        this.scanner = scanner;
        this.fileStore = fileStore;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            ensureInitialized();
            sendHeartbeat();
            log.info("已上报到管理系统，appId={}, 接口数={}", appId, interfaces.size());
        } catch (Exception e) {
            log.warn("首次上报失败，将按调度重试", e);
            invalidateRegistration();
        }
    }

    @Scheduled(initialDelayString = "#{@appRegistryProperties.heartbeatInterval.toMillis()}",
            fixedDelayString = "#{@appRegistryProperties.heartbeatInterval.toMillis()}")
    public void heartbeat() {
        try {
            ensureInitialized();
            sendHeartbeat();
        } catch (Exception e) {
            log.warn("心跳上报失败，将重试", e);
            invalidateRegistration();
        }
    }

    private synchronized void invalidateRegistration() {
        appId = null;
        token = null;
        instance = null;
        interfaces = null;
        interfacesHash = null;
        if (!isConfiguredIdentity()) {
            fileStore.delete(appidFilePath);
        }
    }

    private boolean isConfiguredIdentity() {
        return StringUtils.hasText(properties.getAppid()) && StringUtils.hasText(properties.getToken());
    }

    private synchronized void ensureInitialized() throws Exception {
        if (appId != null) {
            return;
        }
        if (!StringUtils.hasText(properties.getSystemName())) {
            properties.setSystemName(environment.getProperty("spring.application.name", "unknown-system"));
        }
        InstanceInfoDTO info = buildInstanceInfo();

        if (isConfiguredIdentity()) {
            RegisterResponse response = client.register(buildRegisterRequest(info));
            appId = StringUtils.hasText(response.getAppId()) ? response.getAppId() : properties.getAppid();
            token = StringUtils.hasText(response.getToken()) ? response.getToken() : properties.getToken();
            appidFilePath = null;
        } else {
            String path = fileStore.resolvePath(properties);
            appidFilePath = path;
            AppIdFileStore.StoredApp stored = fileStore.read(path);
            if (stored == null || !StringUtils.hasText(stored.getAppId())) {
                RegisterResponse response = client.register(buildRegisterRequest(info));
                fileStore.write(path, response, properties.getSystemName(), properties.getEnv());
                appId = response.getAppId();
                token = response.getToken();
            } else {
                appId = stored.getAppId();
                token = stored.getToken();
            }
        }

        instance = info;
        interfaces = scanner.scan();
        interfacesHash = hashInterfaces(interfaces);
    }

    private RegisterRequest buildRegisterRequest(InstanceInfoDTO info) {
        RegisterRequest request = new RegisterRequest();
        request.setSystemName(properties.getSystemName());
        request.setEnv(properties.getEnv());
        request.setAppId(properties.getAppid());
        request.setToken(properties.getToken());
        request.setInstanceKey(info.getInstanceKey());
        request.setIp(info.getIp());
        request.setPort(info.getPort());
        request.setHostname(info.getHostname());
        request.setStartTime(info.getStartTime());
        return request;
    }

    private void sendHeartbeat() {
        HeartbeatRequest request = new HeartbeatRequest();
        request.setAppId(appId);
        request.setToken(token);
        request.setInstance(instance);
        request.setStatus("UP");
        request.setInterfaces(interfaces);
        request.setInterfacesHash(interfacesHash);
        client.heartbeat(request);
    }

    private InstanceInfoDTO buildInstanceInfo() {
        String ip;
        String hostname;
        try {
            InetAddress local = InetAddress.getLocalHost();
            ip = StringUtils.hasText(properties.getAdvertiseIp()) ? properties.getAdvertiseIp() : local.getHostAddress();
            hostname = local.getHostName();
        } catch (UnknownHostException e) {
            ip = StringUtils.hasText(properties.getAdvertiseIp()) ? properties.getAdvertiseIp() : "127.0.0.1";
            hostname = "unknown";
        }
        int port = environment.getProperty("server.port", Integer.class, 8080);
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

        InstanceInfoDTO info = new InstanceInfoDTO();
        info.setInstanceKey(ip + ":" + port + ":" + hostname + ":" + pid);
        info.setIp(ip);
        info.setPort(port);
        info.setHostname(hostname);
        info.setStartTime(startTime);
        return info;
    }

    private String hashInterfaces(List<InterfaceMetaDTO> interfaces) {
        List<InterfaceMetaDTO> sorted = new ArrayList<>(interfaces);
        sorted.sort(new Comparator<InterfaceMetaDTO>() {
            @Override
            public int compare(InterfaceMetaDTO left, InterfaceMetaDTO right) {
                int result = compareString(left.getMethod(), right.getMethod());
                return result != 0 ? result : compareString(left.getPath(), right.getPath());
            }

            private int compareString(String first, String second) {
                if (first == null && second == null) {
                    return 0;
                }
                if (first == null) {
                    return -1;
                }
                if (second == null) {
                    return 1;
                }
                return first.compareTo(second);
            }
        });

        StringBuilder builder = new StringBuilder();
        for (InterfaceMetaDTO meta : sorted) {
            builder.append(meta.getSchemaHash() == null ? "" : meta.getSchemaHash());
        }
        return DigestUtils.md5DigestAsHex(builder.toString().getBytes(StandardCharsets.UTF_8));
    }
}

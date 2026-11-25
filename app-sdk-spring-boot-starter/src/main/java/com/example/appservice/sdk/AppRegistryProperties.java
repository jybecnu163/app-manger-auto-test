package com.example.appservice.sdk;

import lombok.Data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
public class AppRegistryProperties {

    private String serverUrl;
    private String systemName;
    private String env = "default";
    private String appid;
    private String token;
    private String appidFile;
    private String advertiseIp;
    private Duration heartbeatInterval = Duration.ofSeconds(30);
    private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**", "/error"));
    private boolean controllerLogEnabled = true;

    private TrafficRecordProperties trafficRecord = new TrafficRecordProperties();
    private ExternalCallRecordProperties externalCallRecord = new ExternalCallRecordProperties();
    private ReplayProperties replay = new ReplayProperties();

    @Data
    public static class TrafficRecordProperties {
        private boolean enabled = false;
        private double sampleRate = 0.05;
        private boolean onlyErrors = false;
        private List<String> includePaths = new ArrayList<>(List.of("/api/**"));
        private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**", "/error"));
        private int maxBodyBytes = 4096;
        private List<String> maskHeaders = new ArrayList<>(
                List.of("Authorization", "Cookie", "Set-Cookie", "X-Token"));
        private List<String> maskFields = new ArrayList<>(
                List.of("password", "token", "phone", "idCard"));
        private int batchSize = 100;
        private Duration flushInterval = Duration.ofSeconds(5);
        private int queueSize = 10000;
    }

    @Data
    public static class ExternalCallRecordProperties {
        private boolean enabled = false;
        private int maxBodyBytes = 4096;
        private List<String> maskHeaders = new ArrayList<>(
                List.of("Authorization", "Cookie", "Set-Cookie", "X-Token"));
        private List<String> maskFields = new ArrayList<>(List.of("password", "token"));
    }

    @Data
    public static class ReplayProperties {
        private boolean enabled = false;
        private String dbStrategy = "rollback";
        private String mockMissingPolicy = "fail";
        private boolean signatureEnabled = true;
        private Duration maxTimestampSkew = Duration.ofMinutes(5);
        private String signatureSecret = "app-service-replay-secret";
        private String mockResponsePath = "/api/agent/mock-responses";
    }
}

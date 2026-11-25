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
}

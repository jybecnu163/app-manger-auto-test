package com.example.appservice.server.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppListItemVO {

    private String appId;
    private String name;
    private String env;
    private long onlineInstances;
    private long totalInstances;
    private long interfaceCount;
    private LocalDateTime lastHeartbeatAt;
}

package com.example.appservice.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrafficRecordSwitchRequest {

    private String appId;
    private String token;
    private Boolean enabled;
    private LocalDateTime startAt;
}

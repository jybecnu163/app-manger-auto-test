package com.example.appservice.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HeartbeatResponse {

    private Boolean trafficRecordEnabled;
    private LocalDateTime trafficRecordStartAt;
}

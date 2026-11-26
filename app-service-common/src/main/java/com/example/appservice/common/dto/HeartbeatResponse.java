package com.example.appservice.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 心跳响应，由服务端下发流量录制开关及生效起始时间。
 */
@Data
public class HeartbeatResponse {

    private Boolean trafficRecordEnabled;
    private LocalDateTime trafficRecordStartAt;
}

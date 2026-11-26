package com.example.appservice.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流量录制开关控制请求：enabled 表示目标状态，startAt 表示录制生效时间点。
 */
@Data
public class TrafficRecordSwitchRequest {

    private String appId;
    private String token;
    private Boolean enabled;
    private LocalDateTime startAt;
}

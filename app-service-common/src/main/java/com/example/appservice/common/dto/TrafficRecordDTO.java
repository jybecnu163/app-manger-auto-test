package com.example.appservice.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrafficRecordDTO {

    private String appId;
    private String instanceKey;
    private Long interfaceId;
    private String traceId;
    private String method;
    private String path;
    private String queryParams;
    private String headers;
    private String body;
    private Integer responseStatus;
    private String responseBody;
    private Long costMs;
    private boolean success = true;
    private String error;
    private String requestHash;
    private LocalDateTime createdAt;
}

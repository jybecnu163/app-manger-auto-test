package com.example.appservice.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExternalCallRecordDTO {

    private String appId;
    private String instanceKey;
    private String traceId;
    private String method;
    private String url;
    private String requestHeaders;
    private String requestBody;
    private Integer responseStatus;
    private String responseHeaders;
    private String responseBody;
    private Long costMs;
    private String requestHash;
    private LocalDateTime createdAt;
}

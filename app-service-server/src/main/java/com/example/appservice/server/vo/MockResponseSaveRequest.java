package com.example.appservice.server.vo;

import lombok.Data;

@Data
public class MockResponseSaveRequest {

    private String appId;
    private Long interfaceId;
    private String method;
    private String urlPattern;
    private String requestHash;
    private Integer mockStatus = 200;
    private String mockHeaders;
    private String mockBody;
    private boolean enabled = true;
    private Long externalCallRecordId;
}

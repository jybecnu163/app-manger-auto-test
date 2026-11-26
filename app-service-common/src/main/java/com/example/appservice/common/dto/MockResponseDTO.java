package com.example.appservice.common.dto;

import lombok.Data;

/**
 * 外部调用 Mock 规则定义，urlPattern 与 requestHash 共同决定回放时是否命中。
 */
@Data
public class MockResponseDTO {

    private Long id;
    private String appId;
    private Long interfaceId;
    private String method;
    private String urlPattern;
    private String requestHash;
    private Integer mockStatus = 200;
    private String mockHeaders;
    private String mockBody;
    private boolean enabled = true;
}

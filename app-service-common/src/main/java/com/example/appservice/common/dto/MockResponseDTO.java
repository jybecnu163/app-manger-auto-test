package com.example.appservice.common.dto;

import lombok.Data;

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

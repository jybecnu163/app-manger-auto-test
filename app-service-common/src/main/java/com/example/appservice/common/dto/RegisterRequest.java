package com.example.appservice.common.dto;

import lombok.Data;

@Data
public class RegisterRequest {

    private String systemName;
    private String env;
    private String appId;
    private String token;
    private String instanceKey;
    private String ip;
    private int port;
    private String hostname;
    private long startTime;
}

package com.example.appservice.common.dto;

import lombok.Data;

@Data
public class InstanceInfoDTO {

    private String instanceKey;
    private String ip;
    private int port;
    private String hostname;
    private long startTime;
}

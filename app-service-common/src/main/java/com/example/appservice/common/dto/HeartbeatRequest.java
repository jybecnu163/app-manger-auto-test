package com.example.appservice.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HeartbeatRequest {

    private String appId;
    private String token;
    private InstanceInfoDTO instance;
    private String status;
    private List<InterfaceMetaDTO> interfaces = new ArrayList<>();
    private String interfacesHash;
}

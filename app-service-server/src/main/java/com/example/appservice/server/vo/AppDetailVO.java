package com.example.appservice.server.vo;

import com.example.appservice.common.dto.InstanceInfoDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AppDetailVO {

    private String appId;
    private String name;
    private String env;
    private String token;
    private long interfaceCount;
    private List<InstanceInfoDTO> instances = new ArrayList<>();
}

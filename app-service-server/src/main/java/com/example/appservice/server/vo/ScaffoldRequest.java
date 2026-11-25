package com.example.appservice.server.vo;

import lombok.Data;

@Data
public class ScaffoldRequest {

    private String systemName;
    private int port = 18080;
    private String env = "dev";
    private String serverUrl;
}

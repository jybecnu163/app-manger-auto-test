package com.example.appservice.server.vo;

import lombok.Data;

import java.util.Map;

@Data
public class TestRequest {

    private Long interfaceId;
    private Map<String, String> pathParams;
    private Map<String, String> queryParams;
    private Map<String, String> headers;
    private String body;
    private String replayMode;
    private Long trafficRecordId;
    private String mockResponseId;
}

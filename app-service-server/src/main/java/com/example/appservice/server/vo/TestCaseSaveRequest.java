package com.example.appservice.server.vo;

import lombok.Data;

import java.util.Map;

@Data
public class TestCaseSaveRequest {

    private String name;
    private Map<String, String> pathParams;
    private Map<String, String> queryParams;
    private Map<String, String> headers;
    private String body;
}

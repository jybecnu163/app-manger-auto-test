package com.example.appservice.server.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class TestResultVO {

    private int status;
    private Map<String, String> headers = new LinkedHashMap<>();
    private String body;
    private long costMs;
}

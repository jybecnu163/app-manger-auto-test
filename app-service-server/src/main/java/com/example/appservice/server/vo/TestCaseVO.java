package com.example.appservice.server.vo;

import lombok.Data;

@Data
public class TestCaseVO {

    private Long id;
    private Long interfaceId;
    private String interfaceName;
    private String interfaceMethod;
    private String interfacePath;
    private String name;
    private String pathParams;
    private String queryParams;
    private String headers;
    private String body;
    private String source;
}

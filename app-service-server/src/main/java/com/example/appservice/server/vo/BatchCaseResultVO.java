package com.example.appservice.server.vo;

import lombok.Data;

@Data
public class BatchCaseResultVO {

    private Long caseId;
    private String caseName;
    private Long interfaceId;
    private String interfacePath;
    private String interfaceMethod;
    private Integer status;
    private boolean success;
    private long costMs;
    private String bodyPreview;
    private String error;
}

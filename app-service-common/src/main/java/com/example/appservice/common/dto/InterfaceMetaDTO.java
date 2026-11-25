package com.example.appservice.common.dto;

import lombok.Data;

@Data
public class InterfaceMetaDTO {

    private String method;
    private String path;
    private String controller;
    private String operation;
    private String requestSchema;
    private String responseSchema;
    private String schemaHash;
}

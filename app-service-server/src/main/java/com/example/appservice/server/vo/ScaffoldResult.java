package com.example.appservice.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScaffoldResult {

    private byte[] content;
    private String fileName;
    private String appId;
    private String token;
}

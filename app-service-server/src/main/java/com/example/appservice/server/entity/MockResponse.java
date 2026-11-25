package com.example.appservice.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mock_response")
public class MockResponse {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private Long interfaceId;
    private String method;
    private String urlPattern;
    private String matchMode = "EXACT";
    private String requestHash;
    private int mockStatus = 200;
    private String mockHeaders;
    private String mockBody;
    private boolean enabled = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.example.appservice.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("external_call_record")
public class ExternalCallRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private String traceId;
    private String method;
    private String url;
    private String requestHeaders;
    private String requestBody;
    private Integer responseStatus;
    private String responseHeaders;
    private String responseBody;
    private Long costMs;
    private String requestHash;
    private LocalDateTime createdAt;
}

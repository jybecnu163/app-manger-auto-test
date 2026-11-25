package com.example.appservice.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("traffic_record")
public class TrafficRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private Long interfaceId;
    private String instanceKey;
    private String traceId;
    private String method;
    private String path;
    private String queryParams;
    private String headers;
    private String body;
    private Integer responseStatus;
    private String responseBody;
    private Long costMs;
    private boolean success = true;
    private String error;
    private String requestHash;
    private LocalDateTime createdAt;
}

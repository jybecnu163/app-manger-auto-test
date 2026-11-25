package com.example.appservice.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_test_case")
public class ApiTestCase {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private Long interfaceId;
    private String name;
    private String pathParams;
    private String queryParams;
    private String headers;
    private String body;
    private String contentHash;
    private String source;
    private LocalDateTime createdAt;
}

package com.example.appservice.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_interface")
public class ApiInterface {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private String method;
    private String path;
    private String controller;
    private String operation;
    private String requestSchema;
    private String responseSchema;
    private String schemaHash;
    private LocalDateTime updatedAt;
}

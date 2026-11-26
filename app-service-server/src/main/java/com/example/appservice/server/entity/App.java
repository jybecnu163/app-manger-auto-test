package com.example.appservice.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app")
public class App {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private String name;
    private String env;
    private String token;
    private boolean trafficRecordEnabled;
    private LocalDateTime trafficRecordStartAt;
    private String interfacesHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

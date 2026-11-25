package com.example.appservice.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_instance")
public class AppInstance {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private String instanceKey;
    private String ip;
    private int port;
    private String hostname;
    private String status;
    private LocalDateTime lastHeartbeatAt;
    private long startTime;
}

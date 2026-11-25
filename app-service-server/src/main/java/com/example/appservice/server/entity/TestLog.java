package com.example.appservice.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("test_log")
public class TestLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private Long interfaceId;
    private String requestSummary;
    private Integer responseStatus;
    private long costMs;
    private LocalDateTime createdAt;
}

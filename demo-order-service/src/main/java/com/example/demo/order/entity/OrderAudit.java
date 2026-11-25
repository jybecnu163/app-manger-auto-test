package com.example.demo.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("demo_order_audit")
public class OrderAudit {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String action;
    private LocalDateTime createdAt;
}

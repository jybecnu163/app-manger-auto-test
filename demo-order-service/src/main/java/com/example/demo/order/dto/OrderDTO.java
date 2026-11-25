package com.example.demo.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class OrderDTO {

    private Long id;
    private String orderNo;
    private String customerId;
    private OrderStatus status;
    private BigDecimal amount;
    private String address;
    private String payMethod;
    private List<OrderItemDTO> items = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

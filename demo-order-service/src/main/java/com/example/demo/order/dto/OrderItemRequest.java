package com.example.demo.order.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemRequest {

    private Long productId;
    private String productName;
    private int quantity;
    private BigDecimal price;
}

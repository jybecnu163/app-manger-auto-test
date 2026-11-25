package com.example.demo.product.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductStockDTO {

    private Long productId;
    private String productName;
    private int stock;
    private ProductStatus status;
    private LocalDateTime updatedAt;
}

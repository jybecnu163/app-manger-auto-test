package com.example.demo.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductDTO {

    private Long id;
    private String name;
    private BigDecimal price;
    private int stock;
    private String category;
    private ProductStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

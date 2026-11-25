package com.example.demo.product.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProductRequest {

    private String name;
    private BigDecimal price;
    private int stock;
    private String category;
    private ProductStatus status;
}

package com.example.demo.product.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductSearchRequest {

    private String keyword;
    private String category;
    private ProductStatus status;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private int page = 1;
    private int size = 10;
}

package com.example.demo.product.dto;

import lombok.Data;

@Data
public class AdjustStockRequest {

    private Integer change;
    private String reason;
}

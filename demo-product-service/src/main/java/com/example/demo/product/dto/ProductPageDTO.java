package com.example.demo.product.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductPageDTO {

    private List<ProductDTO> items = new ArrayList<>();
    private long total;
    private int page;
    private int size;
}

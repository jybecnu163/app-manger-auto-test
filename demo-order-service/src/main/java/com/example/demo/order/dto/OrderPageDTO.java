package com.example.demo.order.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OrderPageDTO {

    private List<OrderDTO> items = new ArrayList<>();
    private long total;
    private int page;
    private int size;
}

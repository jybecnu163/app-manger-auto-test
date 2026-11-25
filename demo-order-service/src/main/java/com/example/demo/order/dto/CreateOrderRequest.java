package com.example.demo.order.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateOrderRequest {

    private String customerId;
    private String address;
    private List<OrderItemRequest> items = new ArrayList<>();
}

package com.example.demo.order.controller;

import com.example.demo.order.dto.CreateOrderRequest;
import com.example.demo.order.dto.OrderDTO;
import com.example.demo.order.dto.OrderPageDTO;
import com.example.demo.order.dto.OrderStatus;
import com.example.demo.order.dto.PayRequest;
import com.example.demo.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/{id}")
    public OrderDTO get(@PathVariable("id") Long id) {
        return orderService.get(id);
    }

    @GetMapping
    public OrderPageDTO page(@RequestParam(value = "customerId", required = false) String customerId,
                             @RequestParam(value = "status", required = false) OrderStatus status,
                             @RequestParam(value = "page", defaultValue = "1") int page,
                             @RequestParam(value = "size", defaultValue = "10") int size) {
        return orderService.page(customerId, status, page, size);
    }

    @PostMapping
    public OrderDTO create(@RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }

    @PostMapping("/{id}/pay")
    public OrderDTO pay(@PathVariable("id") Long id, @RequestBody PayRequest request) {
        return orderService.pay(id, request);
    }

    @PostMapping("/{id}/cancel")
    public OrderDTO cancel(@PathVariable("id") Long id) {
        return orderService.cancel(id);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable("id") Long id) {
        return orderService.delete(id);
    }
}

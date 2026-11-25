package com.example.demo.order.service;

import com.example.demo.order.dto.CreateOrderRequest;
import com.example.demo.order.dto.OrderDTO;
import com.example.demo.order.dto.OrderItemDTO;
import com.example.demo.order.dto.OrderItemRequest;
import com.example.demo.order.dto.OrderPageDTO;
import com.example.demo.order.dto.OrderStatus;
import com.example.demo.order.dto.PayRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final Map<Long, OrderDTO> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong();

    public OrderService() {
        CreateOrderRequest sample1 = new CreateOrderRequest();
        sample1.setCustomerId("C001");
        sample1.setAddress("北京市朝阳区 1 号");
        sample1.getItems().add(item(1L, "无线蓝牙耳机", 1, new BigDecimal("299.00")));
        OrderDTO order1 = create(sample1);
        order1.setStatus(OrderStatus.PAID);

        CreateOrderRequest sample2 = new CreateOrderRequest();
        sample2.setCustomerId("C002");
        sample2.setAddress("上海市浦东新区 2 号");
        sample2.getItems().add(item(2L, "人体工学椅", 1, new BigDecimal("899.00")));
        sample2.getItems().add(item(3L, "机械键盘", 1, new BigDecimal("459.00")));
        create(sample2);
    }

    public OrderDTO get(Long id) {
        OrderDTO order = store.get(id);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在: " + id);
        }
        return order;
    }

    public OrderPageDTO page(String customerId, OrderStatus status, int page, int size) {
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        List<OrderDTO> matched = store.values().stream()
                .filter(order -> customerId == null || customerId.isBlank() || customerId.equals(order.getCustomerId()))
                .filter(order -> status == null || status == order.getStatus())
                .sorted(Comparator.comparing(OrderDTO::getId))
                .collect(Collectors.toList());

        int from = Math.min((currentPage - 1) * pageSize, matched.size());
        int to = Math.min(from + pageSize, matched.size());

        OrderPageDTO result = new OrderPageDTO();
        result.setItems(matched.subList(from, to));
        result.setTotal(matched.size());
        result.setPage(currentPage);
        result.setSize(pageSize);
        return result;
    }

    public OrderDTO create(CreateOrderRequest request) {
        long id = idGenerator.incrementAndGet();
        OrderDTO order = new OrderDTO();
        order.setId(id);
        order.setOrderNo(String.format("ORD%06d", id));
        order.setCustomerId(request.getCustomerId());
        order.setAddress(request.getAddress());
        order.setStatus(OrderStatus.CREATED);

        BigDecimal amount = BigDecimal.ZERO;
        List<OrderItemDTO> items = new ArrayList<>();
        if (request.getItems() != null) {
            for (OrderItemRequest requestItem : request.getItems()) {
                OrderItemDTO item = new OrderItemDTO();
                item.setProductId(requestItem.getProductId());
                item.setProductName(requestItem.getProductName());
                item.setQuantity(requestItem.getQuantity());
                item.setPrice(requestItem.getPrice());
                items.add(item);
                if (requestItem.getPrice() != null) {
                    amount = amount.add(requestItem.getPrice().multiply(
                            BigDecimal.valueOf(Math.max(requestItem.getQuantity(), 0))));
                }
            }
        }
        order.setItems(items);
        order.setAmount(amount);
        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        store.put(id, order);
        return order;
    }

    public OrderDTO pay(Long id, PayRequest request) {
        OrderDTO order = get(id);
        order.setStatus(OrderStatus.PAID);
        order.setPayMethod(request.getPayMethod());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    public OrderDTO cancel(Long id) {
        OrderDTO order = get(id);
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    public String delete(Long id) {
        store.remove(id);
        return "已删除订单 " + id;
    }

    private OrderItemRequest item(Long productId, String name, int quantity, BigDecimal price) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setProductName(name);
        item.setQuantity(quantity);
        item.setPrice(price);
        return item;
    }
}

package com.example.demo.order.controller;

import com.example.demo.order.entity.OrderAudit;
import com.example.demo.order.mapper.OrderAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class DryRunDemoController {

    private final RestTemplate restTemplate;
    private final OrderAuditMapper auditMapper;

    @Value("${demo.product-url:http://localhost:8081/api/products/1}")
    private String productUrl;

    @PostMapping("/dryrun-demo")
    @Transactional
    public Map<String, Object> dryRunDemo(@RequestBody(required = false) Map<String, Object> body) {
        long orderId = 1L;
        if (body != null && body.get("orderId") != null) {
            orderId = Long.parseLong(String.valueOf(body.get("orderId")));
        }

        String product = restTemplate.getForObject(productUrl, String.class);

        OrderAudit audit = new OrderAudit();
        audit.setOrderId(orderId);
        audit.setAction("DRY_RUN_SAMPLE");
        audit.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(audit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("product", product);
        result.put("auditId", audit.getId());
        return result;
    }

    @GetMapping("/dryrun-demo/audit-count")
    public Map<String, Object> auditCount() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", auditMapper.selectCount(null));
        return result;
    }
}

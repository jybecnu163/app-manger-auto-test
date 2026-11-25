package com.example.demo.product.controller;

import com.example.demo.product.dto.AdjustStockRequest;
import com.example.demo.product.dto.CreateProductRequest;
import com.example.demo.product.dto.ProductDTO;
import com.example.demo.product.dto.ProductPageDTO;
import com.example.demo.product.dto.ProductSearchRequest;
import com.example.demo.product.dto.ProductStockDTO;
import com.example.demo.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{id}")
    public ProductDTO get(@PathVariable("id") Long id) {
        return productService.get(id);
    }

    @GetMapping
    public ProductPageDTO page(@RequestParam(value = "keyword", required = false) String keyword,
                               @RequestParam(value = "page", defaultValue = "1") int page,
                               @RequestParam(value = "size", defaultValue = "10") int size) {
        return productService.page(keyword, page, size);
    }

    @PostMapping
    public ProductDTO create(@RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return productService.categories();
    }

    @GetMapping("/{id}/stock")
    public ProductStockDTO stock(@PathVariable("id") Long id) {
        return productService.getStock(id);
    }

    @PostMapping("/{id}/stock/adjust")
    public ProductDTO adjustStock(@PathVariable("id") Long id, @RequestBody AdjustStockRequest request) {
        return productService.adjustStock(id, request);
    }

    @PostMapping("/search")
    public ProductPageDTO search(@RequestBody ProductSearchRequest request) {
        return productService.search(request);
    }
}

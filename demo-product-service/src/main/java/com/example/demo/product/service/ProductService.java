package com.example.demo.product.service;

import com.example.demo.product.dto.AdjustStockRequest;
import com.example.demo.product.dto.CreateProductRequest;
import com.example.demo.product.dto.ProductDTO;
import com.example.demo.product.dto.ProductPageDTO;
import com.example.demo.product.dto.ProductSearchRequest;
import com.example.demo.product.dto.ProductStatus;
import com.example.demo.product.dto.ProductStockDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final Map<Long, ProductDTO> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong();

    public ProductService() {
        CreateProductRequest sample1 = new CreateProductRequest();
        sample1.setName("无线蓝牙耳机");
        sample1.setPrice(new BigDecimal("299.00"));
        sample1.setStock(120);
        sample1.setCategory("数码");
        sample1.setStatus(ProductStatus.ON_SALE);
        create(sample1);

        CreateProductRequest sample2 = new CreateProductRequest();
        sample2.setName("人体工学椅");
        sample2.setPrice(new BigDecimal("899.00"));
        sample2.setStock(35);
        sample2.setCategory("办公");
        sample2.setStatus(ProductStatus.ON_SALE);
        create(sample2);

        CreateProductRequest sample3 = new CreateProductRequest();
        sample3.setName("机械键盘");
        sample3.setPrice(new BigDecimal("459.00"));
        sample3.setStock(0);
        sample3.setCategory("数码");
        sample3.setStatus(ProductStatus.OFF_SALE);
        create(sample3);
    }

    public ProductDTO get(Long id) {
        ProductDTO product = store.get(id);
        if (product == null) {
            throw new IllegalArgumentException("商品不存在: " + id);
        }
        return product;
    }

    public ProductPageDTO page(String keyword, int page, int size) {
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        List<ProductDTO> matched = store.values().stream()
                .filter(product -> keyword == null || keyword.isBlank() || product.getName().contains(keyword))
                .sorted(Comparator.comparing(ProductDTO::getId))
                .collect(Collectors.toList());
        return pageOf(matched, currentPage, pageSize);
    }

    public ProductDTO create(CreateProductRequest request) {
        ProductDTO product = new ProductDTO();
        product.setId(idGenerator.incrementAndGet());
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setCategory(request.getCategory());
        product.setStatus(request.getStatus() == null ? ProductStatus.DRAFT : request.getStatus());
        LocalDateTime now = LocalDateTime.now();
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        store.put(product.getId(), product);
        return product;
    }

    public ProductStockDTO getStock(Long id) {
        ProductDTO product = get(id);
        ProductStockDTO stock = new ProductStockDTO();
        stock.setProductId(product.getId());
        stock.setProductName(product.getName());
        stock.setStock(product.getStock());
        stock.setStatus(product.getStatus());
        stock.setUpdatedAt(product.getUpdatedAt());
        return stock;
    }

    public ProductDTO adjustStock(Long id, AdjustStockRequest request) {
        ProductDTO product = get(id);
        int change = request.getChange() == null ? 0 : request.getChange();
        product.setStock(product.getStock() + change);
        product.setUpdatedAt(LocalDateTime.now());
        return product;
    }

    public List<String> categories() {
        return store.values().stream()
                .map(ProductDTO::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public ProductPageDTO search(ProductSearchRequest request) {
        int currentPage = Math.max(request.getPage(), 1);
        int pageSize = Math.max(request.getSize(), 1);
        List<ProductDTO> matched = store.values().stream()
                .filter(product -> request.getKeyword() == null || request.getKeyword().isBlank()
                        || product.getName().contains(request.getKeyword()))
                .filter(product -> request.getCategory() == null || request.getCategory().isBlank()
                        || request.getCategory().equals(product.getCategory()))
                .filter(product -> request.getStatus() == null || request.getStatus() == product.getStatus())
                .filter(product -> request.getMinPrice() == null
                        || (product.getPrice() != null && product.getPrice().compareTo(request.getMinPrice()) >= 0))
                .filter(product -> request.getMaxPrice() == null
                        || (product.getPrice() != null && product.getPrice().compareTo(request.getMaxPrice()) <= 0))
                .sorted(Comparator.comparing(ProductDTO::getId))
                .collect(Collectors.toList());
        return pageOf(matched, currentPage, pageSize);
    }

    private ProductPageDTO pageOf(List<ProductDTO> matched, int page, int size) {
        int from = Math.min((page - 1) * size, matched.size());
        int to = Math.min(from + size, matched.size());

        ProductPageDTO result = new ProductPageDTO();
        result.setItems(matched.subList(from, to));
        result.setTotal(matched.size());
        result.setPage(page);
        result.setSize(size);
        return result;
    }
}

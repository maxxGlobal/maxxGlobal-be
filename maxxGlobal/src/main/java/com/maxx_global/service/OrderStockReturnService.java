package com.maxx_global.service;

import com.maxx_global.entity.*;
import com.maxx_global.repository.ProductRepository;
import com.maxx_global.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderStockReturnService {
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final StockTrackerService stockTrackerService;

    public OrderStockReturnService(ProductVariantRepository variantRepository,
                                   ProductRepository productRepository,
                                   StockTrackerService stockTrackerService) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.stockTrackerService = stockTrackerService;
    }

    @Transactional
    public void returnOrderStock(Order order, AppUser performedBy, String referenceType) {
        for (OrderItem item : order.getItems()) {
            returnItemStock(order, item, performedBy, referenceType);
        }
    }

    @Transactional
    public void returnItemStock(Order order, OrderItem item, AppUser performedBy, String referenceType) {
        ProductVariant variant = item.getProductVariant();
        if (variant != null) {
            int current = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
            stockTrackerService.trackOrderCancellation(
                    variant, item.getQuantity(), performedBy, order.getOrderNumber(), order.getId(),
                    movementDetail(item, referenceType));
            variant.setStockQuantity(current + item.getQuantity());
            variantRepository.save(variant);
            return;
        }

        Product product = item.getProduct();
        if (product == null) {
            throw new IllegalStateException("Legacy order item has neither variant nor product");
        }
        int current = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        stockTrackerService.trackOrderCancellation(
                product, item.getQuantity(), performedBy, order.getOrderNumber(), order.getId(),
                movementDetail(item, referenceType));
        product.setStockQuantity(current + item.getQuantity());
        productRepository.save(product);
    }

    private String movementDetail(OrderItem item, String referenceType) {
        return referenceType + " / itemId=" + item.getId();
    }
}

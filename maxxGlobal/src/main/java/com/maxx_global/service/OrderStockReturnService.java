package com.maxx_global.service;

import com.maxx_global.entity.*;
import com.maxx_global.repository.ProductRepository;
import com.maxx_global.repository.ProductVariantRepository;
import com.maxx_global.enums.ApiErrorCode;
import com.maxx_global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

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
        List<OrderItem> sortedItems = order.getItems().stream()
                .peek(this::validateLockIdentity)
                .sorted(Comparator.comparingInt((OrderItem item) -> lockType(item))
                        .thenComparing(this::lockId))
                .toList();
        for (OrderItem item : sortedItems) {
            returnItemStock(order, item, performedBy, referenceType);
        }
    }

    @Transactional
    public void returnItemStock(Order order, OrderItem item, AppUser performedBy, String referenceType) {
        ProductVariant variant = item.getProductVariant();
        if (variant != null) {
            ProductVariant lockedVariant = variantRepository.findByIdForUpdate(variant.getId())
                    .orElseThrow(() -> new IllegalStateException("Order item variant no longer exists"));
            int current = lockedVariant.getStockQuantity() != null ? lockedVariant.getStockQuantity() : 0;
            stockTrackerService.trackOrderCancellation(
                    lockedVariant, item.getQuantity(), performedBy, order.getOrderNumber(), order.getId(),
                    movementDetail(item, referenceType));
            lockedVariant.setStockQuantity(current + item.getQuantity());
            variantRepository.save(lockedVariant);
            return;
        }

        Product product = item.getProduct();
        if (product == null) {
            throw new IllegalStateException("Legacy order item has neither variant nor product");
        }
        Product lockedProduct = productRepository.findByIdForUpdate(product.getId())
                .orElseThrow(() -> new IllegalStateException("Legacy order item product no longer exists"));
        int current = lockedProduct.getStockQuantity() != null ? lockedProduct.getStockQuantity() : 0;
        stockTrackerService.trackOrderCancellation(
                lockedProduct, item.getQuantity(), performedBy, order.getOrderNumber(), order.getId(),
                movementDetail(item, referenceType));
        lockedProduct.setStockQuantity(current + item.getQuantity());
        productRepository.save(lockedProduct);
    }

    private String movementDetail(OrderItem item, String referenceType) {
        return referenceType + " / itemId=" + item.getId();
    }

    private void validateLockIdentity(OrderItem item) {
        if (item == null || lockId(item) == null || lockId(item) <= 0) {
            throw new BusinessException(ApiErrorCode.INVALID_ORDER);
        }
    }

    private int lockType(OrderItem item) {
        return item.getProductVariant() != null ? 0 : 1;
    }

    private Long lockId(OrderItem item) {
        if (item == null) {
            return null;
        }
        return item.getProductVariant() != null
                ? item.getProductVariant().getId()
                : item.getProduct() != null ? item.getProduct().getId() : null;
    }
}

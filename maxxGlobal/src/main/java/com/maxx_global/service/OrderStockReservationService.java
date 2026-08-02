package com.maxx_global.service;

import com.maxx_global.entity.*;
import com.maxx_global.enums.ApiErrorCode;
import com.maxx_global.exception.BusinessException;
import com.maxx_global.repository.ProductRepository;
import com.maxx_global.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class OrderStockReservationService {
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final StockTrackerService stockTrackerService;

    public OrderStockReservationService(ProductVariantRepository variantRepository,
                                        ProductRepository productRepository,
                                        StockTrackerService stockTrackerService) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.stockTrackerService = stockTrackerService;
    }

    @Transactional
    public void reserveOrderStock(Order order, Collection<OrderItem> items,
                                  AppUser performedBy, String referenceType) {
        replaceOrderStock(order, List.of(), items, performedBy, referenceType);
    }

    @Transactional
    public void replaceOrderStock(Order order, Collection<OrderItem> originalItems,
                                  Collection<OrderItem> newItems, AppUser performedBy,
                                  String referenceType) {
        Map<StockKey, Integer> returned = quantitiesByKey(originalItems);
        Map<StockKey, Integer> reserved = quantitiesByKey(newItems);
        SortedSet<StockKey> keys = new TreeSet<>();
        keys.addAll(returned.keySet());
        keys.addAll(reserved.keySet());

        Map<StockKey, LockedStock> lockedStocks = new LinkedHashMap<>();
        for (StockKey key : keys) {
            lockedStocks.put(key, lockStock(key));
        }

        Map<StockKey, Integer> targetStocks = new LinkedHashMap<>();
        for (StockKey key : keys) {
            int target = lockedStocks.get(key).quantity()
                    + returned.getOrDefault(key, 0) - reserved.getOrDefault(key, 0);
            if (target < 0) {
                throw new BusinessException(ApiErrorCode.INSUFFICIENT_STOCK);
            }
            targetStocks.put(key, target);
        }

        for (StockKey key : keys) {
            int netChange = targetStocks.get(key) - lockedStocks.get(key).quantity();
            if (netChange > 0) {
                trackReturn(lockedStocks.get(key), netChange, performedBy, order, referenceType);
            } else if (netChange < 0) {
                trackReservation(lockedStocks.get(key), -netChange, performedBy, order);
            }
        }

        for (StockKey key : keys) {
            saveStock(lockedStocks.get(key), targetStocks.get(key));
        }
    }

    private Map<StockKey, Integer> quantitiesByKey(Collection<OrderItem> items) {
        Map<StockKey, Integer> quantities = new HashMap<>();
        for (OrderItem item : items) {
            StockKey key = stockKey(item);
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException(ApiErrorCode.INVALID_QUANTITY);
            }
            quantities.merge(key, item.getQuantity(), Integer::sum);
        }
        return quantities;
    }

    private StockKey stockKey(OrderItem item) {
        if (item == null) {
            throw new BusinessException(ApiErrorCode.INVALID_ORDER);
        }
        if (item.getProductVariant() != null) {
            return requireKey(0, item.getProductVariant().getId());
        }
        return requireKey(1, item.getProduct() != null ? item.getProduct().getId() : null);
    }

    private StockKey requireKey(int type, Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ApiErrorCode.INVALID_ORDER);
        }
        return new StockKey(type, id);
    }

    private LockedStock lockStock(StockKey key) {
        if (key.type() == 0) {
            ProductVariant variant = variantRepository.findByIdForUpdate(key.id())
                    .orElseThrow(() -> new BusinessException(ApiErrorCode.PRODUCT_VARIANT_NOT_FOUND));
            return new LockedStock(variant, null,
                    variant.getStockQuantity() != null ? variant.getStockQuantity() : 0);
        }
        Product product = productRepository.findByIdForUpdate(key.id())
                .orElseThrow(() -> new BusinessException(ApiErrorCode.INVALID_ORDER));
        return new LockedStock(null, product,
                product.getStockQuantity() != null ? product.getStockQuantity() : 0);
    }

    private void trackReservation(LockedStock stock, int quantity, AppUser user, Order order) {
        if (stock.variant() != null) {
            stockTrackerService.trackOrderReservation(
                    stock.variant(), quantity, user, order.getOrderNumber(), order.getId());
        } else {
            stockTrackerService.trackOrderReservation(
                    stock.product(), quantity, user, order.getOrderNumber(), order.getId());
        }
    }

    private void trackReturn(LockedStock stock, int quantity, AppUser user,
                             Order order, String referenceType) {
        String detail = referenceType + " / stock replacement";
        if (stock.variant() != null) {
            stockTrackerService.trackOrderCancellation(
                    stock.variant(), quantity, user, order.getOrderNumber(), order.getId(), detail);
        } else {
            stockTrackerService.trackOrderCancellation(
                    stock.product(), quantity, user, order.getOrderNumber(), order.getId(), detail);
        }
    }

    private void saveStock(LockedStock stock, int target) {
        if (stock.variant() != null) {
            stock.variant().setStockQuantity(target);
            variantRepository.save(stock.variant());
        } else {
            stock.product().setStockQuantity(target);
            productRepository.save(stock.product());
        }
    }

    private record StockKey(int type, Long id) implements Comparable<StockKey> {
        @Override
        public int compareTo(StockKey other) {
            int typeComparison = Integer.compare(type, other.type);
            return typeComparison != 0 ? typeComparison : id.compareTo(other.id);
        }
    }

    private record LockedStock(ProductVariant variant, Product product, int quantity) {}
}

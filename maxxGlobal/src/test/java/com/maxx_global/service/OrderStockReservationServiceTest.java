package com.maxx_global.service;

import com.maxx_global.entity.*;
import com.maxx_global.enums.ApiErrorCode;
import com.maxx_global.exception.BusinessException;
import com.maxx_global.repository.ProductRepository;
import com.maxx_global.repository.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStockReservationServiceTest {
    @Mock ProductVariantRepository variantRepository;
    @Mock ProductRepository productRepository;
    @Mock StockTrackerService tracker;

    @Test
    void consecutiveReservationsUseLockedLatestVariantStock() {
        ProductVariant variant = variant(10L, 10);
        when(variantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(variant));

        service().reserveOrderStock(order(1L), List.of(item(variant, 2)), new AppUser(), "CREATE");
        service().reserveOrderStock(order(2L), List.of(item(variant, 3)), new AppUser(), "CREATE");

        assertEquals(5, variant.getStockQuantity());
        verify(variantRepository, times(2)).findByIdForUpdate(10L);
        verify(tracker).trackOrderReservation(variant, 2, any(), "ORDER-1", 1L);
        verify(tracker).trackOrderReservation(variant, 3, any(), "ORDER-2", 2L);
    }

    @Test
    void insufficientStockThrowsBusinessErrorWithoutClampingOrMutation() {
        ProductVariant variant = variant(10L, 2);
        when(variantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(variant));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().reserveOrderStock(
                        order(1L), List.of(item(variant, 3)), new AppUser(), "CREATE"));

        assertEquals(ApiErrorCode.INSUFFICIENT_STOCK, error.getErrorCode());
        assertEquals(2, variant.getStockQuantity());
        verifyNoInteractions(tracker);
        verify(variantRepository, never()).save(any());
    }

    @Test
    void movementFailureDoesNotMutateVariantStock() {
        ProductVariant variant = variant(10L, 5);
        when(variantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(variant));
        doThrow(new RuntimeException("movement failed")).when(tracker)
                .trackOrderReservation(eq(variant), eq(2), any(), eq("ORDER-1"), eq(1L));

        assertThrows(RuntimeException.class, () -> service().reserveOrderStock(
                order(1L), List.of(item(variant, 2)), new AppUser(), "CREATE"));

        assertEquals(5, variant.getStockQuantity());
        verify(variantRepository, never()).save(any());
    }

    @Test
    void multiItemReservationLocksByTypeAndId() {
        ProductVariant variant20 = variant(20L, 5);
        ProductVariant variant10 = variant(10L, 5);
        Product legacy = product(5L, 5);
        when(variantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(variant10));
        when(variantRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(variant20));
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(legacy));

        service().reserveOrderStock(order(1L), List.of(
                item(legacy, 1), item(variant20, 1), item(variant10, 1)), new AppUser(), "CREATE");

        var locks = inOrder(variantRepository, productRepository);
        locks.verify(variantRepository).findByIdForUpdate(10L);
        locks.verify(variantRepository).findByIdForUpdate(20L);
        locks.verify(productRepository).findByIdForUpdate(5L);
    }

    @Test
    void replacementLocksUnionOnceInDeterministicOrderAndAppliesNetChanges() {
        ProductVariant variant10 = variant(10L, 4);
        ProductVariant variant20 = variant(20L, 8);
        when(variantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(variant10));
        when(variantRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(variant20));

        service().replaceOrderStock(order(1L), List.of(item(variant20, 2)),
                List.of(item(variant10, 3)), new AppUser(), "EDIT");

        var locks = inOrder(variantRepository);
        locks.verify(variantRepository).findByIdForUpdate(10L);
        locks.verify(variantRepository).findByIdForUpdate(20L);
        assertEquals(1, variant10.getStockQuantity());
        assertEquals(10, variant20.getStockQuantity());
    }

    @Test
    void insufficientReplacementDoesNotPermanentlyReturnOriginalStock() {
        ProductVariant original = variant(10L, 4);
        ProductVariant replacement = variant(20L, 1);
        when(variantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(original));
        when(variantRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(replacement));

        assertThrows(BusinessException.class, () -> service().replaceOrderStock(order(1L),
                List.of(item(original, 2)), List.of(item(replacement, 3)), new AppUser(), "EDIT"));

        assertEquals(4, original.getStockQuantity());
        assertEquals(1, replacement.getStockQuantity());
        verifyNoInteractions(tracker);
        verify(variantRepository, never()).save(any());
    }

    @Test
    void variantReservationDoesNotMutateProductStock() {
        Product product = product(5L, 50);
        ProductVariant variant = variant(10L, 6);
        variant.setProduct(product);
        when(variantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(variant));

        service().reserveOrderStock(order(1L), List.of(item(variant, 2)), new AppUser(), "CREATE");

        assertEquals(4, variant.getStockQuantity());
        assertEquals(50, product.getStockQuantity());
        verifyNoInteractions(productRepository);
    }

    @Test
    void legacyReservationUsesOnlyLockedProductFallback() {
        Product product = product(5L, 7);
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(product));

        service().reserveOrderStock(order(1L), List.of(item(product, 2)), new AppUser(), "CREATE");

        assertEquals(5, product.getStockQuantity());
        verify(productRepository).save(product);
        verifyNoInteractions(variantRepository);
    }

    private OrderStockReservationService service() {
        return new OrderStockReservationService(variantRepository, productRepository, tracker);
    }

    private Order order(Long id) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber("ORDER-" + id);
        return order;
    }

    private ProductVariant variant(Long id, int stock) {
        ProductVariant variant = new ProductVariant();
        variant.setId(id);
        variant.setStockQuantity(stock);
        return variant;
    }

    private Product product(Long id, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setStockQuantity(stock);
        return product;
    }

    private OrderItem item(ProductVariant variant, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductVariant(variant);
        item.setQuantity(quantity);
        return item;
    }

    private OrderItem item(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }
}

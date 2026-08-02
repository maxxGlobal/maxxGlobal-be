package com.maxx_global.service;

import com.maxx_global.entity.*;
import com.maxx_global.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStockReturnServiceTest {
    @Mock ProductVariantRepository variantRepository;
    @Mock ProductRepository productRepository;
    @Mock StockTrackerService stockTrackerService;

    @Test
    void variantItemReturnsStockOnceWithoutChangingProductStock() {
        Product product = new Product();
        product.setStockQuantity(40);
        ProductVariant variant = new ProductVariant();
        variant.setStockQuantity(5);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setProductVariant(variant);
        item.setQuantity(2);
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORDER-10");

        new OrderStockReturnService(variantRepository, productRepository, stockTrackerService)
                .returnItemStock(order, item, new AppUser(), "CANCELLED");

        assertEquals(7, variant.getStockQuantity());
        assertEquals(40, product.getStockQuantity());
        verify(stockTrackerService, times(1)).trackOrderCancellation(
                variant, 2, any(), "ORDER-10", 10L, "CANCELLED / itemId=null");
        verify(variantRepository).save(variant);
        verifyNoInteractions(productRepository);
    }

    @Test
    void movementFailureDoesNotMutateVariantStock() {
        ProductVariant variant = new ProductVariant();
        variant.setStockQuantity(5);
        OrderItem item = new OrderItem();
        item.setProductVariant(variant);
        item.setQuantity(2);
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORDER-10");
        doThrow(new RuntimeException("movement failed")).when(stockTrackerService)
                .trackOrderCancellation(eq(variant), eq(2), any(), eq("ORDER-10"), eq(10L), anyString());

        assertThrows(RuntimeException.class, () -> service().returnItemStock(
                order, item, new AppUser(), "CANCELLED"));

        assertEquals(5, variant.getStockQuantity());
        verifyNoInteractions(variantRepository, productRepository);
    }

    private OrderStockReturnService service() {
        return new OrderStockReturnService(variantRepository, productRepository, stockTrackerService);
    }
}

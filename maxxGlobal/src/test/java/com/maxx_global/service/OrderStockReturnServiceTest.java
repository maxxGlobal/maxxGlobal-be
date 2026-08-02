package com.maxx_global.service;

import com.maxx_global.entity.*;
import com.maxx_global.dto.stock.StockMovementMapper;
import com.maxx_global.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.*;
import java.util.Optional;

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
        variant.setId(20L);
        variant.setStockQuantity(5);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setProductVariant(variant);
        item.setQuantity(2);
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORDER-10");
        when(variantRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(variant));

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
        variant.setId(20L);
        variant.setStockQuantity(5);
        OrderItem item = new OrderItem();
        item.setProductVariant(variant);
        item.setQuantity(2);
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORDER-10");
        when(variantRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(variant));
        doThrow(new RuntimeException("movement failed")).when(stockTrackerService)
                .trackOrderCancellation(eq(variant), eq(2), any(), eq("ORDER-10"), eq(10L), anyString());

        assertThrows(RuntimeException.class, () -> service().returnItemStock(
                order, item, new AppUser(), "CANCELLED"));

        assertEquals(5, variant.getStockQuantity());
        verify(variantRepository, never()).save(any());
        verifyNoInteractions(productRepository);
    }

    @Test
    void realStockTrackerPropagatesMovementRepositoryFailureBeforeStockMutation() {
        StockMovementRepository movementRepository = mock(StockMovementRepository.class);
        ProductRepository trackerProductRepository = mock(ProductRepository.class);
        ProductVariantRepository trackerVariantRepository = mock(ProductVariantRepository.class);
        StockMovementMapper mapper = mock(StockMovementMapper.class);
        StockTrackerService realTracker = new StockTrackerService(
                movementRepository, trackerProductRepository, trackerVariantRepository, mapper);
        OrderStockReturnService returnService = new OrderStockReturnService(
                variantRepository, productRepository, realTracker);
        Product product = new Product();
        ProductVariant variant = new ProductVariant();
        variant.setId(20L);
        variant.setProduct(product);
        variant.setStockQuantity(5);
        OrderItem item = new OrderItem();
        item.setProductVariant(variant);
        item.setQuantity(2);
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORDER-10");
        when(variantRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(variant));
        when(movementRepository.save(any(StockMovement.class)))
                .thenThrow(new RuntimeException("movement database unavailable"));

        assertThrows(RuntimeException.class,
                () -> returnService.returnItemStock(order, item, new AppUser(), "CANCELLED"));

        assertEquals(5, variant.getStockQuantity());
        verify(variantRepository, never()).save(any());
    }

    @Test
    void differentOrdersReturningSameVariantUseLockedLatestStockWithoutLostUpdate() {
        StockMovementRepository movementRepository = mock(StockMovementRepository.class);
        Product product = new Product();
        ProductVariant variant = new ProductVariant();
        variant.setId(20L);
        variant.setProduct(product);
        variant.setStockQuantity(10);
        when(variantRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(variant));
        StockTrackerService realTracker = new StockTrackerService(movementRepository, productRepository,
                variantRepository, mock(StockMovementMapper.class));
        OrderStockReturnService returnService = new OrderStockReturnService(
                variantRepository, productRepository, realTracker);

        returnService.returnItemStock(order(1L, "ORDER-1"), item(variant, 2), new AppUser(), "CANCELLED");
        returnService.returnItemStock(order(2L, "ORDER-2"), item(variant, 3), new AppUser(), "CANCELLED");

        var movementCaptor = org.mockito.ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository, times(2)).save(movementCaptor.capture());
        assertEquals(15, variant.getStockQuantity());
        assertAll(
                () -> assertEquals(10, movementCaptor.getAllValues().get(0).getPreviousStock()),
                () -> assertEquals(12, movementCaptor.getAllValues().get(0).getNewStock()),
                () -> assertEquals(12, movementCaptor.getAllValues().get(1).getPreviousStock()),
                () -> assertEquals(15, movementCaptor.getAllValues().get(1).getNewStock())
        );
    }

    private Order order(Long id, String number) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber(number);
        return order;
    }

    private OrderItem item(ProductVariant variant, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductVariant(variant);
        item.setQuantity(quantity);
        return item;
    }

    private OrderStockReturnService service() {
        return new OrderStockReturnService(variantRepository, productRepository, stockTrackerService);
    }
}

package com.maxx_global.job;

import com.maxx_global.entity.*;
import com.maxx_global.enums.OrderStatus;
import com.maxx_global.event.OrderAutoCancelledEvent;
import com.maxx_global.dto.stock.StockMovementMapper;
import com.maxx_global.repository.*;
import com.maxx_global.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderAutoCancellationProcessorTest {
    @Mock OrderRepository orderRepository;
    @Mock OrderStockReturnService stockReturnService;
    @Mock DiscountService discountService;
    @Mock ApplicationEventPublisher publisher;

    @Test
    void unpricedEditedOrderIsCancelledOnceAndPublishesOneEvent() {
        Order order = order(OrderStatus.EDITED_PENDING_APPROVAL);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        OrderAutoCancellationProcessor processor = processor();

        assertTrue(processor.cancelExpiredOrder(1L, "expired", 48, true));
        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        assertNull(order.getTotalAmount());
        verify(stockReturnService).returnOrderStock(order, order.getUser(), "AUTO_CANCELLED");
        verify(publisher, times(1)).publishEvent(any(OrderAutoCancelledEvent.class));

        assertFalse(processor.cancelExpiredOrder(1L, "expired", 48, true));
        verifyNoMoreInteractions(stockReturnService, publisher);
    }

    @Test
    void discountRemovalFailurePreventsStatusAndEvent() {
        Order order = order(OrderStatus.EDITED_PENDING_APPROVAL);
        order.setAppliedDiscount(new Discount());
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        doThrow(new RuntimeException("db failure")).when(discountService).removeDiscountUsage(1L);

        assertThrows(RuntimeException.class,
                () -> processor().cancelExpiredOrder(1L, "expired", 48, true));
        assertEquals(OrderStatus.EDITED_PENDING_APPROVAL, order.getOrderStatus());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void discountedOrderRemovesUsageWithinCancellationFlow() {
        Order order = order(OrderStatus.EDITED_PENDING_APPROVAL);
        order.setAppliedDiscount(new Discount());
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        assertTrue(processor().cancelExpiredOrder(1L, "expired", 48, true));

        verify(discountService).removeDiscountUsage(1L);
        verify(orderRepository).save(order);
        verify(publisher).publishEvent(any(OrderAutoCancelledEvent.class));
    }

    @Test
    void movementPersistenceFailureLeavesOrderStatusAndStockUnchangedAndPublishesNoEvent() {
        ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        StockMovementRepository movementRepository = mock(StockMovementRepository.class);
        ProductVariant variant = new ProductVariant();
        variant.setId(5L);
        variant.setProduct(new Product());
        variant.setStockQuantity(10);
        OrderItem item = new OrderItem();
        item.setId(7L);
        item.setProductVariant(variant);
        item.setQuantity(2);
        Order order = order(OrderStatus.EDITED_PENDING_APPROVAL);
        order.setItems(Set.of(item));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(variantRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(variant));
        when(movementRepository.save(any(StockMovement.class)))
                .thenThrow(new RuntimeException("movement database unavailable"));
        StockTrackerService tracker = new StockTrackerService(movementRepository, productRepository,
                variantRepository, mock(StockMovementMapper.class));
        OrderStockReturnService realStockReturn = new OrderStockReturnService(
                variantRepository, productRepository, tracker);
        OrderAutoCancellationProcessor processor = new OrderAutoCancellationProcessor(
                orderRepository, realStockReturn, discountService, publisher);

        assertThrows(RuntimeException.class,
                () -> processor.cancelExpiredOrder(1L, "expired", 48, true));

        assertEquals(OrderStatus.EDITED_PENDING_APPROVAL, order.getOrderStatus());
        assertEquals(10, variant.getStockQuantity());
        verify(variantRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    private OrderAutoCancellationProcessor processor() {
        return new OrderAutoCancellationProcessor(orderRepository, stockReturnService, discountService, publisher);
    }

    private Order order(OrderStatus status) {
        AppUser user = new AppUser();
        Order order = new Order();
        order.setId(1L);
        order.setUser(user);
        order.setOrderNumber("ORDER-1");
        order.setOrderStatus(status);
        order.setTotalAmount(null);
        return order;
    }
}

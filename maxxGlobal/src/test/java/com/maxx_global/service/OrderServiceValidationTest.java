package com.maxx_global.service;

import com.maxx_global.dto.order.*;
import com.maxx_global.entity.AppUser;
import com.maxx_global.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class OrderServiceValidationTest {
    @Mock OrderRepository orderRepository;
    @Mock ProductPriceRepository productPriceRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductVariantRepository productVariantRepository;
    @Mock OrderMapper orderMapper;
    @Mock OrderItemRepository orderItemRepository;
    @Mock DealerService dealerService;
    @Mock DiscountService discountService;
    @Mock OrderPdfService orderPdfService;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock StockTrackerService stockTrackerService;
    @Mock CategoryService categoryService;
    @Mock CartService cartService;
    @Mock LocalizationService localizationService;
    @Spy @InjectMocks OrderService orderService;

    @Test
    void createOrderWithValidationAcceptsMissingPriceAndReturnsPendingOrder() {
        OrderCalculationResponse calculation = calculation(null, null, null, null, null);
        OrderResponse pending = response("PENDING", null, null, null);
        doReturn(calculation).when(orderService).calculateOrderTotal(any(), any());
        doReturn(pending).when(orderService).createOrder(any(), any());

        OrderResponse result = orderService.createOrderWithValidation(request(), user());

        assertEquals("PENDING", result.orderStatus());
        assertNull(result.subtotal());
        assertNull(result.discountAmount());
        assertNull(result.totalAmount());
    }

    @Test
    void createOrderWithValidationRejectsNullTotalForFullyPricedItems() {
        doReturn(calculation(BigDecimal.TEN, BigDecimal.ZERO, null, BigDecimal.TEN, BigDecimal.TEN))
                .when(orderService).calculateOrderTotal(any(), any());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrderWithValidation(request(), user()));
        assertEquals("Fiyatlı sipariş için toplam tutar hesaplanamadı", error.getMessage());
    }

    @Test
    void createOrderWithValidationStillRejectsZeroAndNegativePricedTotals() {
        for (BigDecimal invalidTotal : List.of(BigDecimal.ZERO, BigDecimal.ONE.negate())) {
            doReturn(calculation(BigDecimal.TEN, BigDecimal.ZERO, invalidTotal, BigDecimal.TEN, BigDecimal.TEN))
                    .when(orderService).calculateOrderTotal(any(), any());

            assertThrows(IllegalArgumentException.class,
                    () -> orderService.createOrderWithValidation(request(), user()));
        }
    }

    private OrderCalculationResponse calculation(BigDecimal subtotal, BigDecimal discount, BigDecimal total,
                                                 BigDecimal unitPrice, BigDecimal itemTotal) {
        OrderItemCalculation item = new OrderItemCalculation(
                1L, "Product", 2L, "SKU", "M", "P", 1,
                unitPrice, itemTotal, true, 5, null, "IN_STOCK");
        return new OrderCalculationResponse(subtotal, discount, total, "TRY", 1,
                List.of(item), List.of(), null);
    }

    private OrderRequest request() {
        return new OrderRequest(1L, List.of(new OrderProductRequest(2L, null, 1)), null, null, null);
    }

    private AppUser user() {
        AppUser user = new AppUser();
        user.setId(7L);
        return user;
    }

    private OrderResponse response(String status, BigDecimal subtotal, BigDecimal discount, BigDecimal total) {
        return new OrderResponse(1L, "ORDER-1", "Dealer", 1L, null, List.of(), null,
                status, subtotal, discount, total, "TRY", null, null, "ACTIVE", null, false, null);
    }
}

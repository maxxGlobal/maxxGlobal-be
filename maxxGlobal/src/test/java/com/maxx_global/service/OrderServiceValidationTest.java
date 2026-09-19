package com.maxx_global.service;

import com.maxx_global.dto.order.*;
import com.maxx_global.entity.*;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.OrderStatus;
import com.maxx_global.enums.ApiErrorCode;
import com.maxx_global.exception.BusinessException;
import com.maxx_global.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import java.math.BigDecimal;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    @Mock OrderStockReturnService orderStockReturnService;
    @Mock OrderStockReservationService orderStockReservationService;
    @Spy @InjectMocks OrderService orderService;

    @Test
    void originalItemSnapshotBuildAndParseRoundTripPreservesOldVariants() {
        Order order = new Order();
        order.setCurrency(CurrencyType.TRY);

        Product sizedProduct = new Product();
        sizedProduct.setName("Plaka");
        ProductVariant sizedVariant = new ProductVariant();
        sizedVariant.setId(41L);
        sizedVariant.setSize("10 x 20 mm");
        sizedVariant.setSku("PL-10X20");
        OrderItem sizedItem = item(order, sizedProduct, sizedVariant, 2, new BigDecimal("50.00"));

        Product nullableProduct = new Product();
        nullableProduct.setName("Vidasız Ürün");
        ProductVariant nullableVariant = new ProductVariant();
        nullableVariant.setId(42L);
        nullableVariant.setSize(null);
        nullableVariant.setSku(null);
        OrderItem unpricedItem = item(order, nullableProduct, nullableVariant, 1, null);

        String builtItems = ReflectionTestUtils.invokeMethod(
                orderService, "buildItemsInfoString", new HashSet<>(Set.of(sizedItem, unpricedItem)));
        String notes = "Önceki kalemler: " + builtItems + " (Toplam: Fiyat bilgisi bulunmuyor)";

        LocalizationService mailLocalization = mock(LocalizationService.class);
        when(mailLocalization.getMessage(eq("mail.price.unavailable"), eq(Locale.ENGLISH), any(Object[].class)))
                .thenReturn("Price information is unavailable");
        MailService mailService = new MailService(mock(ResendEmailService.class), mock(TemplateEngine.class),
                mock(AppUserRepository.class), mock(OrderPdfService.class), mailLocalization);

        List<Map<String, Object>> parsed = ReflectionTestUtils.invokeMethod(
                mailService, "extractOriginalItemsFromAdminNotes", notes, Locale.ENGLISH, CurrencyType.TRY);

        assertEquals(2, parsed.size());
        Map<String, Object> sized = parsed.stream()
                .filter(item -> Long.valueOf(41L).equals(item.get("productVariantId")))
                .findFirst().orElseThrow();
        assertEquals("10 x 20 mm", sized.get("variantSize"));
        assertEquals("PL-10X20", sized.get("variantSku"));
        assertEquals(2, sized.get("quantity"));
        assertEquals(new BigDecimal("50.00"), sized.get("totalPrice"));

        Map<String, Object> nullable = parsed.stream()
                .filter(item -> Long.valueOf(42L).equals(item.get("productVariantId")))
                .findFirst().orElseThrow();
        assertNull(nullable.get("variantSize"));
        assertNull(nullable.get("variantSku"));
        assertNull(nullable.get("totalPrice"));
    }

    private OrderItem item(Order order, Product product, ProductVariant variant,
                           int quantity, BigDecimal totalPrice) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductVariant(variant);
        item.setQuantity(quantity);
        item.setTotalPrice(totalPrice);
        return item;
    }

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

    @Test
    void adminCanEditUnpricedOrderWithoutLookingUpNullPriceId() {
        Dealer dealer = new Dealer();
        dealer.setId(1L);
        dealer.setPreferredCurrency(CurrencyType.TRY);
        AppUser customer = user();
        customer.setDealer(dealer);
        Product product = new Product();
        product.setStatus(EntityStatus.ACTIVE);
        ProductVariant variant = new ProductVariant();
        variant.setId(2L);
        variant.setStatus(EntityStatus.ACTIVE);
        variant.setStockQuantity(10);
        variant.setProduct(product);
        Order order = new Order();
        order.setId(5L);
        order.setUser(customer);
        order.setOrderNumber("ORDER-5");
        order.setOrderStatus(OrderStatus.PENDING);
        order.setCurrency(CurrencyType.TRY);
        order.setTotalAmount(null);
        order.setAppliedDiscount(mock(Discount.class));
        OrderItem oldItem = new OrderItem();
        oldItem.setOrder(order);
        oldItem.setProduct(product);
        oldItem.setProductVariant(variant);
        oldItem.setQuantity(1);
        order.setItems(new HashSet<>(Set.of(oldItem)));
        when(orderRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(order));
        when(dealerService.findById(1L)).thenReturn(dealer);
        when(productVariantRepository.findById(2L)).thenReturn(Optional.of(variant));
        when(productPriceRepository.findByVariantIdAndDealerIdAndCurrency(2L, 1L, CurrencyType.TRY))
                .thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.editOrderByAdmin(5L,
                new OrderRequest(1L, List.of(new OrderProductRequest(2L, null, 2)), null, null, null),
                user(), "Miktar güncellendi");

        OrderItem edited = order.getItems().iterator().next();
        assertEquals(2L, edited.getProductVariant().getId());
        assertEquals(2, edited.getQuantity());
        assertNull(edited.getProductPriceId());
        assertNull(edited.getUnitPrice());
        assertNull(edited.getTotalPrice());
        assertNull(order.getDiscountAmount());
        assertNull(order.getTotalAmount());
        assertNull(order.getAppliedDiscount());
        assertEquals(OrderStatus.EDITED_PENDING_APPROVAL, order.getOrderStatus());
        verify(productPriceRepository, never()).findById(isNull());
        verify(productPriceRepository, never()).findByIdAndStatus(isNull(), any());
        verify(discountService).removeDiscountUsage(5L);
        verify(discountService, never()).getDiscountEntityById(any());
        verify(discountService, never()).canUseDiscount(any(), any(), any());
    }

    @Test
    void nullOrderAndItemIdsFailBeforeRepositoryCalls() {
        BusinessException orderError = assertThrows(BusinessException.class,
                () -> orderService.editOrderByAdmin(null, request(), user(), null));
        assertEquals(ApiErrorCode.ORDER_NOT_FOUND, orderError.getErrorCode());

        BusinessException itemError = assertThrows(BusinessException.class,
                () -> orderService.removeItemFromOrder(1L, null, user(), null));
        assertEquals(ApiErrorCode.INVALID_ORDER, itemError.getErrorCode());
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void mixedPriceItemsHaveUnknownSubtotalAndPricedRemainderRecalculates() {
        OrderItem priced = new OrderItem();
        priced.setTotalPrice(new BigDecimal("25.00"));
        OrderItem unpriced = new OrderItem();
        unpriced.setTotalPrice(null);

        BigDecimal mixedSubtotal = ReflectionTestUtils.invokeMethod(
                orderService, "calculateSubtotal", new HashSet<>(Set.of(priced, unpriced)));
        BigDecimal pricedSubtotal = ReflectionTestUtils.invokeMethod(
                orderService, "calculateSubtotal", new HashSet<>(Set.of(priced)));

        assertNull(mixedSubtotal);
        assertEquals(new BigDecimal("25.00"), pricedSubtotal);
    }

    @Test
    void suppliedPriceFromAnotherDealerStillFailsWithPriceMismatch() {
        Dealer dealer = new Dealer();
        dealer.setId(1L);
        dealer.setPreferredCurrency(CurrencyType.TRY);
        Dealer otherDealer = new Dealer();
        otherDealer.setId(9L);
        Product product = new Product();
        product.setStatus(EntityStatus.ACTIVE);
        ProductVariant variant = new ProductVariant();
        variant.setId(2L);
        variant.setStatus(EntityStatus.ACTIVE);
        variant.setProduct(product);
        ProductPrice supplied = new ProductPrice(variant, otherDealer, CurrencyType.TRY, BigDecimal.TEN);
        supplied.setId(99L);
        supplied.setStatus(EntityStatus.ACTIVE);

        when(productVariantRepository.findById(2L)).thenReturn(Optional.of(variant));
        when(productPriceRepository.findByVariantIdAndDealerIdAndCurrency(2L, 1L, CurrencyType.TRY))
                .thenReturn(Optional.empty());
        when(productPriceRepository.findById(99L)).thenReturn(Optional.of(supplied));

        BusinessException error = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(orderService, "resolveProductItems",
                        List.of(new OrderProductRequest(2L, 99L, 1)), dealer, false));

        assertEquals(ApiErrorCode.PRICE_MISMATCH, error.getErrorCode());
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

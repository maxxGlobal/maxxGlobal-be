package com.maxx_global.service;

import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Discount;
import com.maxx_global.entity.Order;
import com.maxx_global.entity.OrderItem;
import com.maxx_global.entity.Product;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.DiscountType;
import com.maxx_global.enums.Language;
import com.maxx_global.enums.OrderStatus;
import com.maxx_global.repository.AppUserRepository;
import com.maxx_global.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThymeleafTemplateRenderTest {
    private TemplateEngine templateEngine;
    private LocalizationService localizationService;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCacheable(false);
        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        localizationService = mock(LocalizationService.class);
        when(localizationService.getLanguage(any(Locale.class))).thenReturn(Language.EN);
        when(localizationService.getMessage(anyString(), any(Locale.class), any(Object[].class)))
                .thenAnswer(invocation -> "mail.price.unavailable".equals(invocation.getArgument(0))
                        ? "Price information is unavailable" : invocation.getArgument(0));
    }

    @Test
    void mailTemplatesRenderWithScalarVariantsAndUnavailablePrice() {
        Order order = unpricedOrder();
        MailService service = new MailService(mock(ResendEmailService.class), templateEngine,
                mock(AppUserRepository.class), mock(OrderPdfService.class), localizationService);
        ReflectionTestUtils.setField(service, "baseUrl", "https://example.test");

        String newOrder = ReflectionTestUtils.invokeMethod(
                service, "generateNewOrderEmailTemplate", order, Locale.ENGLISH, true, false);
        String approved = ReflectionTestUtils.invokeMethod(
                service, "generateOrderApprovedEmailTemplate", order, Locale.ENGLISH);
        String cancelled = ReflectionTestUtils.invokeMethod(
                service, "generateOrderAutoCancelledEmailTemplate", order, "Timeout", Locale.ENGLISH);

        assertTrue(newOrder.contains("Price information is unavailable"));
        assertTrue(approved.contains("Price information is unavailable"));
        assertTrue(cancelled.contains("info@nafx.com.tr"));
        assertFalse(newOrder.contains("item.productVariant"));
    }

    @Test
    void invoiceTemplateRendersLocalizedUnavailablePricesWithoutFormattingNull() {
        Order order = unpricedOrder();
        when(localizationService.getPreferredLocaleOrDefault(any(AppUser.class))).thenReturn(Locale.ENGLISH);
        OrderPdfService service = new OrderPdfService(
                mock(OrderRepository.class), templateEngine, localizationService);

        String html = ReflectionTestUtils.invokeMethod(
                service, "generateOrderHtmlContent", order, Locale.ENGLISH);

        assertTrue(html.contains("Price information is unavailable"));
    }

    @Test
    void discountExpressionRendersInNewAndApprovedTemplates() {
        Order order = unpricedOrder();
        OrderItem item = order.getItems().iterator().next();
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setTotalPrice(new BigDecimal("100.00"));
        order.setTotalAmount(new BigDecimal("85.00"));
        order.setDiscountAmount(new BigDecimal("15.00"));
        Discount discount = new Discount();
        discount.setName("Campaign");
        discount.setNameEn("Campaign");
        discount.setDiscountType(DiscountType.PERCENTAGE);
        discount.setDiscountValue(new BigDecimal("15"));
        order.setAppliedDiscount(discount);

        MailService service = new MailService(mock(ResendEmailService.class), templateEngine,
                mock(AppUserRepository.class), mock(OrderPdfService.class), localizationService);
        ReflectionTestUtils.setField(service, "baseUrl", "https://example.test");

        String newOrder = ReflectionTestUtils.invokeMethod(
                service, "generateNewOrderEmailTemplate", order, Locale.ENGLISH, true, false);
        String approved = ReflectionTestUtils.invokeMethod(
                service, "generateOrderApprovedEmailTemplate", order, Locale.ENGLISH);

        assertTrue(newOrder.contains("15%"));
        assertTrue(approved.contains("15%"));
    }

    private Order unpricedOrder() {
        Dealer dealer = new Dealer();
        dealer.setName("Test Dealer");
        AppUser user = new AppUser();
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setEmail("ada@example.test");
        user.setDealer(dealer);

        Product product = new Product();
        product.setName("Implant");
        product.setCode("IMP-1");
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(null);
        item.setTotalPrice(null);

        Order order = new Order();
        order.setId(7L);
        order.setOrderNumber("ORDER-7");
        order.setOrderDate(LocalDateTime.of(2026, 9, 19, 12, 0));
        order.setOrderStatus(OrderStatus.PENDING);
        order.setCurrency(CurrencyType.USD);
        order.setUser(user);
        order.setItems(new LinkedHashSet<>());
        order.getItems().add(item);
        item.setOrder(order);
        return order;
    }
}

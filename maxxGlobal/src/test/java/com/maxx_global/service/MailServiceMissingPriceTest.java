package com.maxx_global.service;

import com.maxx_global.entity.*;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.Language;
import com.maxx_global.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceMissingPriceTest {
    @Mock ResendEmailService resendEmailService;
    @Mock TemplateEngine templateEngine;
    @Mock AppUserRepository appUserRepository;
    @Mock OrderPdfService orderPdfService;
    @Mock LocalizationService localizationService;
    private MailService mailService;

    @BeforeEach
    void setUp() {
        mailService = new MailService(resendEmailService, templateEngine, appUserRepository,
                orderPdfService, localizationService);
        lenient().when(localizationService.getMessage(anyString(), any(Locale.class), any(Object[].class)))
                .thenAnswer(invocation -> localized(invocation.getArgument(0), invocation.getArgument(1)));
    }

    @Test
    void unpricedItemSummaryDoesNotThrowAndShowsTurkishUnavailableMessage() {
        String summary = summary(order(item(null, null)), Language.TR);
        assertTrue(summary.contains("Fiyat bilgisi bulunmuyor"));
        assertTrue(summary.contains("ÖDEME ÖZETİ"));
    }

    @Test
    void unpricedItemSummaryUsesEnglishLocale() {
        String summary = summary(order(item(null, null)), Language.EN);
        assertTrue(summary.contains("Price information is unavailable"));
        assertTrue(summary.contains("PAYMENT SUMMARY"));
    }

    @Test
    void mixedPriceSummaryDoesNotExposePartialTotalOrDiscount() {
        Order order = order(item(new BigDecimal("10"), new BigDecimal("10")), item(null, null));
        Discount discount = mock(Discount.class);
        order.setAppliedDiscount(discount);
        order.setDiscountAmount(BigDecimal.ONE);
        order.setTotalAmount(new BigDecimal("9"));

        String summary = summary(order, Language.TR);

        assertFalse(summary.contains("Ara Toplam"));
        assertFalse(summary.contains("İndirim ("));
        assertFalse(summary.contains("TASARRUF"));
        assertTrue(summary.endsWith("Fiyat bilgisi bulunmuyor"));
    }

    @Test
    void fullyPricedDiscountSummaryIsPreserved() {
        Order order = order(item(new BigDecimal("10"), new BigDecimal("10")));
        Discount discount = mock(Discount.class);
        when(discount.getLocalizedName(Language.TR)).thenReturn("Test indirimi");
        order.setAppliedDiscount(discount);
        order.setDiscountAmount(BigDecimal.ONE);
        order.setTotalAmount(new BigDecimal("9"));

        String summary = summary(order, Language.TR);

        assertTrue(summary.contains("Ara Toplam"));
        assertTrue(summary.contains("Test indirimi"));
        assertTrue(summary.contains("GENEL TOPLAM"));
        assertTrue(summary.contains("TASARRUF"));
    }

    @Test
    void baseContextMarksUnpricedOrderAsHavingNoDiscount() {
        Order order = order(item(null, null));
        order.setAppliedDiscount(mock(Discount.class));
        order.setDiscountAmount(BigDecimal.ONE);
        when(localizationService.getLanguage(Locale.ENGLISH)).thenReturn(Language.EN);

        Context context = ReflectionTestUtils.invokeMethod(mailService, "createBaseContext",
                order, Locale.ENGLISH, true);

        assertEquals("Price information is unavailable", context.getVariable("formattedTotal"));
        assertEquals("Price information is unavailable", context.getVariable("formattedSubtotal"));
        assertEquals(false, context.getVariable("hasDiscount"));
    }

    @Test
    void newOrderNotificationCompletesForUnpricedOrder() throws Exception {
        AppUser recipient = new AppUser();
        recipient.setEmail("admin@example.com");
        recipient.setEmailNotifications(true);
        recipient.setPreferredLanguage(Language.TR);
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        recipient.setRoles(Set.of(adminRole));
        Order order = order(item(null, null));
        order.setUser(recipient);
        order.setOrderNumber("ORDER-1");

        ReflectionTestUtils.setField(mailService, "mailEnabled", true);
        ReflectionTestUtils.setField(mailService, "newOrderNotificationEnabled", true);
        ReflectionTestUtils.setField(mailService, "pdfAttachmentEnabled", false);
        ReflectionTestUtils.setField(mailService, "fromEmail", "sender@example.com");
        when(appUserRepository.findUsersWithUserPermissions(anyList())).thenReturn(List.of());
        when(localizationService.getPreferredLocaleOrDefault(recipient)).thenReturn(Language.TR.toLocale());
        when(localizationService.getLanguage(Language.TR.toLocale())).thenReturn(Language.TR);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>mail</html>");
        when(resendEmailService.sendEmail(eq("admin@example.com"), anyString(), anyString())).thenReturn(true);

        assertTrue(mailService.sendNewOrderNotificationToAdmins(order).get());
    }

    private String summary(Order order, Language language) {
        return ReflectionTestUtils.invokeMethod(mailService, "generateOrderItemsSummary", order, true, language);
    }

    private Order order(OrderItem... items) {
        Order order = new Order();
        order.setItems(new LinkedHashSet<>(Set.of(items)));
        order.setCurrency(CurrencyType.TRY);
        order.setOrderStatus(com.maxx_global.enums.OrderStatus.PENDING);
        return order;
    }

    private OrderItem item(BigDecimal unitPrice, BigDecimal totalPrice) {
        Product product = mock(Product.class);
        when(product.getLocalizedName(any())).thenReturn("Ürün");
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(unitPrice);
        item.setTotalPrice(totalPrice);
        return item;
    }

    private String localized(String key, Locale locale) {
        boolean english = Locale.ENGLISH.getLanguage().equals(locale.getLanguage());
        return switch (key) {
            case "mail.price.unavailable" -> english ? "Price information is unavailable" : "Fiyat bilgisi bulunmuyor";
            case "mail.payment.summary" -> english ? "PAYMENT SUMMARY" : "ÖDEME ÖZETİ";
            case "mail.payment.total" -> english ? "Total" : "Toplam";
            case "mail.status.PENDING" -> english ? "Pending" : "Beklemede";
            default -> key;
        };
    }
}

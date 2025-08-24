// src/main/java/com/maxx_global/service/MailTemplateService.java
package com.maxx_global.service;

import com.maxx_global.entity.Order;
import com.maxx_global.entity.OrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class MailTemplateService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Order için ortak template değişkenlerini hazırlar
     */
    public Map<String, Object> prepareOrderTemplateVariables(Order order) {
        Map<String, Object> variables = new HashMap<>();

        variables.put("order", order);
        variables.put("orderItems", order.getItems());
        variables.put("customer", order.getUser());
        variables.put("dealer", order.getUser().getDealer());
        variables.put("formattedDate", order.getOrderDate().format(DATE_FORMATTER));
        variables.put("formattedTotal", formatCurrency(order.getTotalAmount()));
        variables.put("orderStatus", getStatusDisplayName(order.getOrderStatus().name()));

        return variables;
    }

    /**
     * Para formatı
     */
    public String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0.00 TL";
        }
        return String.format("%.2f TL", amount);
    }

    /**
     * Durum adlarının Türkçe karşılığı
     */
    public String getStatusDisplayName(String status) {
        if (status == null) {
            return "Bilinmeyen";
        }

        return switch (status.toUpperCase()) {
            case "PENDING" -> "Beklemede";
            case "APPROVED" -> "Onaylandı";
            case "REJECTED" -> "Reddedildi";
            case "SHIPPED" -> "Kargoya Verildi";
            case "COMPLETED" -> "Tamamlandı";
            case "CANCELLED" -> "İptal Edildi";
            case "EDITED_PENDING_APPROVAL" -> "Düzenleme Onayı Bekliyor";
            default -> status;
        };
    }

    /**
     * Sipariş kalemleri özeti oluşturur
     */
    public String generateOrderItemsSummary(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return "Sipariş kalemi bulunamadı";
        }

        StringBuilder summary = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            summary.append("• ")
                    .append(item.getProduct().getName())
                    .append(" x")
                    .append(item.getQuantity())
                    .append(" adet (")
                    .append(formatCurrency(item.getTotalPrice()))
                    .append(")\n");
        }

        return summary.toString();
    }

    /**
     * Email subject oluşturur
     */
    public String generateEmailSubject(String type, String orderNumber) {
        String prefix = switch (type.toUpperCase()) {
            case "NEW_ORDER" -> "Yeni Sipariş Bildirimi";
            case "ORDER_APPROVED" -> "Siparişiniz Onaylandı";
            case "ORDER_REJECTED" -> "Siparişiniz Reddedildi";
            case "ORDER_EDITED" -> "Siparişiniz Düzenlendi";
            case "ORDER_STATUS_CHANGED" -> "Sipariş Durumu Güncellendi";
            default -> "Sipariş Bildirimi";
        };

        return prefix + " - " + orderNumber;
    }

    /**
     * Admin için sipariş özeti
     */
    public String generateAdminOrderSummary(Order order) {
        return String.format(
                "Sipariş: %s | Müşteri: %s %s | Bayi: %s | Tutar: %s | Durum: %s",
                order.getOrderNumber(),
                order.getUser().getFirstName(),
                order.getUser().getLastName(),
                order.getUser().getDealer().getName(),
                formatCurrency(order.getTotalAmount()),
                getStatusDisplayName(order.getOrderStatus().name())
        );
    }

    /**
     * Müşteri için sipariş özeti
     */
    public String generateCustomerOrderSummary(Order order) {
        return String.format(
                "Sipariş Numaranız: %s | Toplam Tutar: %s | Durum: %s",
                order.getOrderNumber(),
                formatCurrency(order.getTotalAmount()),
                getStatusDisplayName(order.getOrderStatus().name())
        );
    }

    /**
     * Email footer bilgisi
     */
    public String generateEmailFooter() {
        return "Bu e-posta otomatik olarak gönderilmiştir. Lütfen yanıtlamayınız.\n" +
                "Sorularınız için müşteri hizmetlerimizle iletişime geçebilirsiniz.";
    }
}
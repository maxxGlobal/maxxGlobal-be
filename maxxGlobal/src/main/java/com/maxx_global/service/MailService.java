// src/main/java/com/maxx_global/service/MailService.java
package com.maxx_global.service;

import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Discount;
import com.maxx_global.entity.Order;
import com.maxx_global.entity.OrderItem;
import com.maxx_global.enums.DiscountType;
import com.maxx_global.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class MailService {

    private static final Logger logger = Logger.getLogger(MailService.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    @Value("${app.mail.pdf-attachment.enabled:true}") // ✅ EKLE
    private Boolean pdfAttachmentEnabled;
    @Value("${app.notifications.email.order-edit-rejected.enabled:true}")
    private Boolean orderEditRejectedNotificationEnabled;
    private final ResendEmailService resendEmailService;

    private final TemplateEngine templateEngine;
    private final AppUserRepository appUserRepository;
    private final OrderPdfService orderPdfService;

    @Value("${aws.ses.from-email}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.mail.enabled:true}")
    private Boolean mailEnabled;

    @Value("${app.mail.async-enabled:true}")
    private Boolean asyncEnabled;

    @Value("${app.notifications.email.new-order.enabled:true}")
    private Boolean newOrderNotificationEnabled;

    @Value("${app.notifications.email.order-approved.enabled:true}")
    private Boolean orderApprovedNotificationEnabled;

    @Value("${app.notifications.email.order-rejected.enabled:true}")
    private Boolean orderRejectedNotificationEnabled;

    @Value("${app.notifications.email.order-edited.enabled:true}")
    private Boolean orderEditedNotificationEnabled;

    @Value("${app.notifications.email.status-change.enabled:true}")
    private Boolean statusChangeNotificationEnabled;

    public MailService(  ResendEmailService resendEmailService,
                       TemplateEngine templateEngine,
                       AppUserRepository appUserRepository, OrderPdfService orderPdfService) {
        this.resendEmailService = resendEmailService;
        this.templateEngine = templateEngine;
        this.appUserRepository = appUserRepository;
        this.orderPdfService = orderPdfService;
    }

    // ==================== PUBLIC MAIL METHODS ====================

    /**
     * Yeni sipariş oluşturulduğunda admin/super admin'lere mail gönder
     */
    @Async("mailTaskExecutor")
    public CompletableFuture<Boolean> sendNewOrderNotificationToAdmins(Order order) {
        if (!isMailEnabled() || !newOrderNotificationEnabled) {
            logger.info("New order notification disabled, skipping");
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Sending new order notification to admins for order: " + order.getOrderNumber());

        try {
            // Admin ve super admin kullanıcılarını getir
            List<AppUser> adminUsers = appUserRepository.findAdminUsersForEmailNotification();

            if (adminUsers.isEmpty()) {
                logger.warning("No admin users found for email notification");
                return CompletableFuture.completedFuture(false);
            }

            String subject = generateSubject("NEW_ORDER", order.getOrderNumber());
            String htmlContent = generateNewOrderEmailTemplate(order);

            byte[] pdfAttachment = null;
            if (pdfAttachmentEnabled) {
                try {
                    pdfAttachment = orderPdfService.generateOrderPdf(order);
                    logger.info("PDF attachment prepared for order: " + order.getOrderNumber());
                } catch (Exception e) {
                    logger.warning("Could not generate PDF attachment: " + e.getMessage());
                }
            }

            int successCount = 0;
            for (AppUser admin : adminUsers) {
                try {
                    boolean sent = sendEmailWithAttachment(
                            admin.getEmail(),
                            subject,
                            htmlContent,
                            pdfAttachment,
                            generatePdfFileName(order)
                    );
                    if (sent) {
                        successCount++;
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to send new order notification to admin: " + admin.getEmail(), e);
                }
            }

            logger.info("New order notification sent to " + successCount + "/" + adminUsers.size() + " admin users");
            return CompletableFuture.completedFuture(successCount > 0);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending new order notification", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Sipariş onaylandığında müşteriye mail gönder
     */
    @Async("mailTaskExecutor")
    public CompletableFuture<Boolean> sendOrderApprovedNotificationToCustomer(Order order) {
        if (!isMailEnabled() || !orderApprovedNotificationEnabled) {
            logger.info("Order approved notification disabled, skipping");
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Sending order approved notification for order: " + order.getOrderNumber());

        try {
            AppUser customer = order.getUser();

            if (!isCustomerNotificationEnabled(customer)) {
                logger.info("Customer email notifications disabled for: " + customer.getEmail());
                return CompletableFuture.completedFuture(false);
            }

            String subject = generateSubject("ORDER_APPROVED", order.getOrderNumber());
            String htmlContent = generateOrderApprovedEmailTemplate(order);

            boolean sent = sendEmailWithRetry(customer.getEmail(), subject, htmlContent);

            if (sent) {
                logger.info("Order approved notification sent to customer: " + customer.getEmail());
            }

            return CompletableFuture.completedFuture(sent);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending order approved notification", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Sipariş reddedildiğinde müşteriye mail gönder
     */
    @Async("mailTaskExecutor")
    public CompletableFuture<Boolean> sendOrderRejectedNotificationToCustomer(Order order) {
        if (!isMailEnabled() || !orderRejectedNotificationEnabled) {
            logger.info("Order rejected notification disabled, skipping");
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Sending order rejected notification for order: " + order.getOrderNumber());

        try {
            AppUser customer = order.getUser();

            if (!isCustomerNotificationEnabled(customer)) {
                logger.info("Customer email notifications disabled for: " + customer.getEmail());
                return CompletableFuture.completedFuture(false);
            }

            String subject = generateSubject("ORDER_REJECTED", order.getOrderNumber());
            String htmlContent = generateOrderRejectedEmailTemplate(order);

            boolean sent = sendEmailWithRetry(customer.getEmail(), subject, htmlContent);

            if (sent) {
                logger.info("Order rejected notification sent to customer: " + customer.getEmail());
            }

            return CompletableFuture.completedFuture(sent);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending order rejected notification", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Sipariş düzenlendiğinde müşteriye mail gönder (PDF eki ile)
     */
    @Async("mailTaskExecutor")
    public CompletableFuture<Boolean> sendOrderEditedNotificationToCustomer(Order order) {
        if (!isMailEnabled() || !orderEditedNotificationEnabled) {
            logger.info("Order edited notification disabled, skipping");
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Sending order edited notification for order: " + order.getOrderNumber());

        try {
            AppUser customer = order.getUser();

            if (!isCustomerNotificationEnabled(customer)) {
                logger.info("Customer email notifications disabled for: " + customer.getEmail());
                return CompletableFuture.completedFuture(false);
            }

            String subject = generateSubject("ORDER_EDITED", order.getOrderNumber());
            String htmlContent = generateOrderEditedEmailTemplate(order);

            // PDF attachment için kontrol
            byte[] pdfAttachment = null;
            if (pdfAttachmentEnabled) {
                try {
                    pdfAttachment = orderPdfService.generateOrderPdf(order);
                    logger.info("PDF attachment prepared for edited order: " + order.getOrderNumber());
                } catch (Exception e) {
                    logger.warning("Could not generate PDF attachment for edited order: " + e.getMessage());
                }
            }

            // PDF eki ile mail gönder
            boolean sent = sendEmailWithAttachment(
                    customer.getEmail(),
                    subject,
                    htmlContent,
                    pdfAttachment,
                    generatePdfFileName(order)
            );

            if (sent) {
                logger.info("Order edited notification sent to customer: " + customer.getEmail());
            }

            return CompletableFuture.completedFuture(sent);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending order edited notification", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Sipariş durumu değiştiğinde müşteriye mail gönder
     */
    @Async("mailTaskExecutor")
    public CompletableFuture<Boolean> sendOrderStatusChangeNotificationToCustomer(Order order, String previousStatus) {
        if (!isMailEnabled() || !statusChangeNotificationEnabled) {
            logger.info("Order status change notification disabled, skipping");
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Sending order status change notification for order: " + order.getOrderNumber());

        try {
            AppUser customer = order.getUser();

            if (!isCustomerNotificationEnabled(customer)) {
                logger.info("Customer email notifications disabled for: " + customer.getEmail());
                return CompletableFuture.completedFuture(false);
            }

            String subject = generateSubject("STATUS_CHANGE", order.getOrderNumber());
            String htmlContent = generateOrderStatusChangeEmailTemplate(order, previousStatus);

            boolean sent = sendEmailWithRetry(customer.getEmail(), subject, htmlContent);

            if (sent) {
                logger.info("Order status change notification sent to customer: " + customer.getEmail());
            }

            return CompletableFuture.completedFuture(sent);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending order status change notification", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * PDF eki ile mail gönder (SES Raw Message kullanarak)
     */
    private boolean sendEmailWithAttachment(String toEmail, String subject, String htmlContent,
                                            byte[] pdfAttachment, String attachmentName) {
        if (pdfAttachment == null || !pdfAttachmentEnabled) {
            return sendEmailWithRetry(toEmail, subject, htmlContent);
        }

        try {
            return resendEmailService.sendEmailWithAttachment(
                    toEmail, subject, htmlContent, pdfAttachment, attachmentName
            );
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to send email with attachment to " + toEmail, e);
            // Fallback: PDF olmadan gönder
            return sendEmailWithRetry(toEmail, subject, htmlContent);
        }
    }

    /**
     * String'i Quoted-Printable formatına çevir
     */
    private String toQuotedPrintable(String input) {
        StringBuilder result = new StringBuilder();
        byte[] bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        int lineLength = 0;
        for (byte b : bytes) {
            int unsigned = b & 0xFF;

            // Printable ASCII karakterler (32-126 arası, = hariç)
            if (unsigned >= 32 && unsigned <= 126 && unsigned != 61) {
                result.append((char) unsigned);
                lineLength++;
            } else if (unsigned == 13 || unsigned == 10) {
                // CRLF
                if (unsigned == 13) {
                    result.append("\r\n");
                    lineLength = 0;
                }
            } else {
                // Encode edilmesi gereken karakterler
                String hex = String.format("=%02X", unsigned);
                result.append(hex);
                lineLength += 3;
            }

            // Satır uzunluğu 75'i geçerse soft line break ekle
            if (lineLength >= 73) {
                result.append("=\r\n");
                lineLength = 0;
            }
        }

        return result.toString();
    }

    /**
     * PDF dosya adı oluştur
     */
    private String generatePdfFileName(Order order) {
        return "Siparis_" + order.getOrderNumber().replace("-", "_") + "_" +
                order.getOrderDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    // ==================== SYNCHRONOUS METHODS (For Testing) ====================

    /**
     * Test için senkron mail gönderimi
     */
    public boolean sendTestEmail(String toEmail, String subject, String content) {
        if (!isMailEnabled()) {
            logger.warning("Mail service is disabled");
            return false;
        }

        try {
            return sendEmailWithRetry(toEmail, subject, content);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending test email", e);
            return false;
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Retry mekanizması ile mail gönder
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    private boolean sendEmailWithRetry(String toEmail, String subject, String htmlContent) {
        return sendEmail(toEmail, subject, htmlContent);
    }

    private boolean sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            validateEmailParams(toEmail, subject, htmlContent);
            return resendEmailService.sendEmail(toEmail, subject, htmlContent);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to send email to " + toEmail, e);
            return false;
        }
    }

    /**
     * Email parametrelerini validate et
     */
    private void validateEmailParams(String toEmail, String subject, String content) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("To email cannot be empty");
        }
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be empty");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }
        if (fromEmail == null || fromEmail.trim().isEmpty()) {
            throw new IllegalStateException("From email is not configured");
        }
    }

    /**
     * Mail servisi aktif mi kontrol et
     */
    private boolean isMailEnabled() {
        return mailEnabled != null && mailEnabled;
    }

    /**
     * Müşteri bildirimleri aktif mi kontrol et
     */
    private boolean isCustomerNotificationEnabled(AppUser customer) {
        return customer != null &&
                customer.getEmail() != null &&
                !customer.getEmail().trim().isEmpty() &&
                customer.isEmailNotificationsEnabled();
    }

    /**
     * Subject oluştur
     */
    private String generateSubject(String type, String orderNumber) {
        String prefix = switch (type) {
            case "NEW_ORDER" -> "Yeni Sipariş Bildirimi";
            case "ORDER_APPROVED" -> "Siparişiniz Onaylandı";
            case "ORDER_REJECTED" -> "Siparişiniz Reddedildi";
            case "ORDER_EDITED" -> "Siparişiniz Düzenlendi";
            case "STATUS_CHANGE" -> "Sipariş Durumu Güncellendi";
            default -> "Sipariş Bildirimi";
        };
        return prefix + " - " + orderNumber;
    }

    // ==================== TEMPLATE GENERATION METHODS ====================

    /**
     * Yeni sipariş email template'i
     */
    private String generateNewOrderEmailTemplate(Order order) {
        Context context = createBaseContext(order);
        context.setVariable("orderDetailUrlNew", baseUrl + "/admin/orders/" + order.getId());
        return processTemplate("emails/new-order-notification", context);
    }

    /**
     * Sipariş onaylandı email template'i
     */
    private String generateOrderApprovedEmailTemplate(Order order) {
        Context context = createBaseContext(order);
        // ✅ DOĞRU URL'ler
        context.setVariable("orderDetailUrlApproved", baseUrl + "/homepage/my-orders");
        context.setVariable("baseUrlApproved", baseUrl + "/homepage");
        return processTemplate("emails/order-approved-notification", context);
    }

    /**
     * Sipariş reddedildi email template'i
     */
    private String generateOrderRejectedEmailTemplate(Order order) {
        Context context = createBaseContext(order);
        context.setVariable("rejectionReason", order.getAdminNotes());
        context.setVariable("baseUrlRejected", baseUrl + "/homepage"); // ✅
        return processTemplate("emails/order-rejected-notification", context);
    }


    /**
     * Sipariş durumu değişti email template'i
     */
    private String generateOrderStatusChangeEmailTemplate(Order order, String previousStatus) {
        Context context = createBaseContext(order);
        context.setVariable("previousStatus", getStatusDisplayName(previousStatus));
        context.setVariable("currentStatus", getStatusDisplayName(order.getOrderStatus().name()));
        context.setVariable("orderDetailUrlStatus", baseUrl + "/homepage/my-orders"); // ✅
        return processTemplate("emails/order-status-change-notification", context);
    }

    /**
     * Base template context oluştur
     */
    private Context createBaseContext(Order order) {
        Context context = new Context(new Locale("tr", "TR"));

        context.setVariable("order", order);
        context.setVariable("orderItems", order.getItems());
        context.setVariable("customer", order.getUser());
        context.setVariable("dealer", order.getUser().getDealer());
        context.setVariable("formattedDate", formatDate(order.getOrderDate()));
        context.setVariable("formattedTotal", formatCurrency(order.getTotalAmount()));
        context.setVariable("orderStatus", getStatusDisplayName(order.getOrderStatus().name()));
        context.setVariable("baseUrl", baseUrl);
        context.setVariable("orderItemsSummary", generateOrderItemsSummary(order));
        addDiscountInfoToContext(context, order);

        return context;
    }
    private void addDiscountInfoToContext(Context context, Order order) {
        // Discount var mı kontrol et
        boolean hasDiscount = order.getAppliedDiscount() != null &&
                order.getDiscountAmount() != null &&
                order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0;

        context.setVariable("hasDiscount", hasDiscount);

        if (hasDiscount) {
            Discount discount = order.getAppliedDiscount();

            // Discount detay bilgileri
            context.setVariable("discount", discount);
            context.setVariable("discountName", discount.getName());
            context.setVariable("discountType", getDiscountTypeDisplayName(discount.getDiscountType()));
            context.setVariable("discountValue", discount.getDiscountValue());
            context.setVariable("discountAmount", order.getDiscountAmount());
            context.setVariable("formattedDiscountAmount", formatCurrency(order.getDiscountAmount()));

            // Orijinal ve indirimli tutarlar
            BigDecimal subtotal = calculateSubtotal(order);
            context.setVariable("subtotal", subtotal);
            context.setVariable("formattedSubtotal", formatCurrency(subtotal));
            context.setVariable("savingsAmount", order.getDiscountAmount());
            context.setVariable("formattedSavingsAmount", formatCurrency(order.getDiscountAmount()));

            // İndirim açıklaması
            String discountDescription = generateDiscountDescription(discount, order.getDiscountAmount());
            context.setVariable("discountDescription", discountDescription);

            logger.info("Added discount info to email context - Name: " + discount.getName() +
                    ", Amount: " + order.getDiscountAmount());
        } else {
            // Discount yok - default değerler
            context.setVariable("discount", null);
            context.setVariable("discountName", null);
            context.setVariable("discountType", null);
            context.setVariable("discountValue", null);
            context.setVariable("discountAmount", BigDecimal.ZERO);
            context.setVariable("formattedDiscountAmount", formatCurrency(BigDecimal.ZERO));
            context.setVariable("subtotal", order.getTotalAmount());
            context.setVariable("formattedSubtotal", formatCurrency(order.getTotalAmount()));
            context.setVariable("savingsAmount", BigDecimal.ZERO);
            context.setVariable("formattedSavingsAmount", formatCurrency(BigDecimal.ZERO));
            context.setVariable("discountDescription", null);
        }
    }

    /**
     * Subtotal hesapla (discount öncesi tutar)
     */
    private BigDecimal calculateSubtotal(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Discount tipi Türkçe açıklama
     */
    private String getDiscountTypeDisplayName( DiscountType discountType) {
        if (discountType == null) return "Bilinmeyen";

        return switch (discountType) {
            case PERCENTAGE -> "Yüzde İndirim";
            case FIXED_AMOUNT -> "Sabit Tutar İndirim";
        };
    }

    /**
     * İndirim açıklaması oluştur
     */
    private String generateDiscountDescription(Discount discount, BigDecimal appliedAmount) {
        StringBuilder description = new StringBuilder();
        description.append(discount.getName()).append(" - ");

        if (discount.getDiscountType() ==  DiscountType.PERCENTAGE) {
            description.append("%").append(discount.getDiscountValue()).append(" indirim");
        } else {
            description.append(formatCurrency(discount.getDiscountValue())).append(" indirim");
        }

        description.append(" (Tasarruf: ").append(formatCurrency(appliedAmount)).append(")");

        return description.toString();
    }

    /**
     * Sipariş kalemleri özeti - discount bilgisi ile
     */
    private String generateOrderItemsSummary(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return "Sipariş kalemi bulunamadı";
        }

        StringBuilder summary = new StringBuilder();
        BigDecimal itemsTotal = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {
            summary.append("• ")
                    .append(item.getProduct().getName())
                    .append(" x")
                    .append(item.getQuantity())
                    .append(" adet - ")
                    .append(formatCurrency(item.getTotalPrice()))
                    .append("\n");
            itemsTotal = itemsTotal.add(item.getTotalPrice());
        }

        // Discount varsa ekle
        if (order.getAppliedDiscount() != null && order.getDiscountAmount() != null &&
                order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {

            summary.append("\n--- ÖDEME ÖZETİ ---\n");
            summary.append("Ara Toplam: ").append(formatCurrency(itemsTotal)).append("\n");
            summary.append("İndirim (").append(order.getAppliedDiscount().getName()).append("): -")
                    .append(formatCurrency(order.getDiscountAmount())).append("\n");
            summary.append("GENEL TOPLAM: ").append(formatCurrency(order.getTotalAmount())).append("\n");
            summary.append("✅ TASARRUF: ").append(formatCurrency(order.getDiscountAmount()));
        }

        return summary.toString();
    }
    /**
     * Template işle
     */
    private String processTemplate(String templateName, Context context) {
        try {
            return templateEngine.process(templateName, context);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing template: " + templateName, e);
            return generateFallbackEmailContent(context);
        }
    }

    /**
     * Fallback email içeriği (template hata durumunda)
     */
    private String generateFallbackEmailContent(Context context) {
        Order order = (Order) context.getVariable("order");
        AppUser customer = (AppUser) context.getVariable("customer");

        return "<html><body>" +
                "<h2>Sipariş Bildirimi</h2>" +
                "<p>Sayın " + customer.getFirstName() + " " + customer.getLastName() + ",</p>" +
                "<p>Sipariş No: " + order.getOrderNumber() + "</p>" +
                "<p>Durum: " + getStatusDisplayName(order.getOrderStatus().name()) + "</p>" +
                "<p>Toplam: " + formatCurrency(order.getTotalAmount()) + "</p>" +
                "<p>Bu otomatik bir bildirimdir.</p>" +
                "</body></html>";
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Tarih formatla
     */
    private String formatDate(java.time.LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_FORMATTER) : "";
    }

    /**
     * Para birimi formatla
     */
    private String formatCurrency(BigDecimal amount) {
        return amount != null ? String.format("%.2f TL", amount) : "0.00 TL";
    }

    /**
     * Durum adlarının Türkçe karşılığı
     */
    private String getStatusDisplayName(String status) {
        if (status == null) return "Bilinmeyen";

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
     * Sipariş kalemleri özeti
     */
//    private String generateOrderItemsSummary(Order order) {
//        if (order.getItems() == null || order.getItems().isEmpty()) {
//            return "Sipariş kalemi bulunamadı";
//        }
//
//        StringBuilder summary = new StringBuilder();
//        for (OrderItem item : order.getItems()) {
//            summary.append("• ")
//                    .append(item.getProduct().getName())
//                    .append(" x")
//                    .append(item.getQuantity())
//                    .append(" adet - ")
//                    .append(formatCurrency(item.getTotalPrice()))
//                    .append("\n");
//        }
//        return summary.toString();
//    }

    // ==================== HEALTH CHECK METHODS ====================

    /**
     * Mail servisi sağlık kontrolü
     */
    public boolean isHealthy() {
        try {
            return isMailEnabled();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Mail service health check failed", e);
            return false;
        }
    }

    public Object getMailServiceInfo() {
        try {
            if (!isMailEnabled()) {
                return "Mail service is disabled";
            }

            return new Object() {
                public final boolean enabled = mailEnabled;
                public final boolean isHealthy = isHealthy();
                public final boolean pdfAttachmentEnabled = MailService.this.pdfAttachmentEnabled;
                public final String provider = "Resend";
                public final String fromEmail = MailService.this.fromEmail;
            };
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting mail service info", e);
            return "Error: " + e.getMessage();
        }
    }
    /**
     * Sipariş düzenlendi email template'i
     */
    private String generateOrderEditedEmailTemplate(Order order) {
        logger.info("Generating order edited email template for: " + order.getOrderNumber());

        Context context = createBaseContext(order);
        context.setVariable("orderDetailUrlEdit", baseUrl + "homepage/my-orders");
        context.setVariable("approveEditUrl", baseUrl + "/api/orders/edited/" + order.getId() + "/approve");

        // ✅ DÜZELTME: Null-safe parsing
        String editReason = extractEditReasonFromAdminNotes(order.getAdminNotes());
        BigDecimal originalTotal = extractOriginalTotalFromAdminNotes(order.getAdminNotes());
        List<Map<String, Object>> originalItems = extractOriginalItemsFromAdminNotes(order.getAdminNotes());

        BigDecimal currentTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal totalDifference = currentTotal.subtract(originalTotal);

        // Context'e güvenli değerler ekle
        context.setVariable("editReason", editReason);
        context.setVariable("originalTotal", originalTotal);
        context.setVariable("formattedOriginalTotal", formatCurrency(originalTotal));
        context.setVariable("totalDifference", totalDifference);
        context.setVariable("formattedTotalDifference", formatCurrency(totalDifference));
        context.setVariable("originalItems", originalItems);
        context.setVariable("hasOriginalData", !originalItems.isEmpty() && originalTotal.compareTo(BigDecimal.ZERO) > 0);

        // ✅ Değişiklik durumu bilgisi
        context.setVariable("totalIncreased", totalDifference.compareTo(BigDecimal.ZERO) > 0);
        context.setVariable("totalDecreased", totalDifference.compareTo(BigDecimal.ZERO) < 0);
        context.setVariable("totalUnchanged", totalDifference.compareTo(BigDecimal.ZERO) == 0);

        // ✅ Debug log
        logger.info("Email context prepared - editReason: " + editReason +
                ", originalTotal: " + originalTotal +
                ", originalItems: " + originalItems.size() +
                ", currentTotal: " + currentTotal +
                ", difference: " + totalDifference);

        try {
            String processedTemplate = processTemplate("emails/order-edited-notification", context);
            logger.info("Template processed successfully for order edited email");
            return processedTemplate;
        } catch (Exception e) {
            logger.severe("Template processing failed for order edited email: " + e.getMessage());
            return generateFallbackEditedOrderEmail(order, editReason, originalTotal, currentTotal);
        }
    }


    /**
     * Template hata durumunda fallback email
     */
    private String generateFallbackEditedOrderEmail(Order order, String editReason,
                                                    BigDecimal originalTotal, BigDecimal currentTotal) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h2>✏️ Siparişiniz Düzenlendi</h2>");
        html.append("<p><strong>Sipariş No:</strong> ").append(order.getOrderNumber()).append("</p>");
        html.append("<p><strong>Durum:</strong> Düzenleme Onayı Bekleniyor</p>");

        if (editReason != null && !editReason.equals("Düzenleme nedeni belirtilmemiş")) {
            html.append("<p><strong>Düzenleme Nedeni:</strong> ").append(editReason).append("</p>");
        }

        html.append("<h3>💰 Tutar Değişimi</h3>");
        html.append("<p>Önceki Tutar: ").append(formatCurrency(originalTotal)).append("</p>");
        html.append("<p>Yeni Tutar: ").append(formatCurrency(currentTotal)).append("</p>");

        BigDecimal difference = currentTotal.subtract(originalTotal);
        if (difference.compareTo(BigDecimal.ZERO) != 0) {
            html.append("<p><strong>Fark: ").append(formatCurrency(difference)).append("</strong></p>");
        }

        html.append("<h3>📦 Yeni Sipariş Kalemleri</h3>");
        html.append("<ul>");
        for (OrderItem item : order.getItems()) {
            html.append("<li>").append(item.getProduct().getName())
                    .append(" x").append(item.getQuantity())
                    .append(" - ").append(formatCurrency(item.getTotalPrice()))
                    .append("</li>");
        }
        html.append("</ul>");

        html.append("<p><strong>Lütfen değişiklikleri onaylayın veya reddedin.</strong></p>");
        html.append("<p>Bu otomatik bir bildirimdir.</p>");
        html.append("</body></html>");

        return html.toString();
    }
    // Helper metodlar
    private String extractEditReasonFromAdminNotes(String adminNotes) {
        if (adminNotes == null || !adminNotes.contains("düzenledi:")) {
            return "Düzenleme nedeni belirtilmemiş";
        }

        String[] lines = adminNotes.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.contains("düzenledi:")) {
                try {
                    String reasonStart = line.substring(line.indexOf("düzenledi:") + "düzenledi:".length());
                    String reason = reasonStart.substring(0, reasonStart.indexOf("]")).trim();
                    return reason.isEmpty() ? "Düzenleme nedeni belirtilmemiş" : reason;
                } catch (Exception e) {
                    logger.warning("Could not parse edit reason: " + e.getMessage());
                }
            }
        }

        return "Düzenleme nedeni belirtilmemiş";
    }

    private BigDecimal extractOriginalTotalFromAdminNotes(String adminNotes) {
        if (adminNotes == null) {
            return BigDecimal.ZERO;
        }

        try {
            String[] lines = adminNotes.split("\n");
            for (String line : lines) {
                // ✅ DÜZELTME: Doğru format aranıyor
                if (line.contains("Önceki kalemler:") && line.contains("(Toplam:")) {
                    // "Önceki kalemler: ... (Toplam: 1234.56 TRY)" formatından parse et
                    String totalPart = line.substring(line.indexOf("(Toplam:") + 8); // "(Toplam:" uzunluğu 8
                    totalPart = totalPart.substring(0, totalPart.indexOf(" ")).trim(); // İlk boşluktan önce al
                    return new BigDecimal(totalPart);
                }
            }
        } catch (Exception e) {
            logger.warning("Could not parse original total from admin notes: " + e.getMessage());
        }

        return BigDecimal.ZERO;
    }

    private List<Map<String, Object>> extractOriginalItemsFromAdminNotes(String adminNotes) {
        List<Map<String, Object>> originalItems = new ArrayList<>();

        if (adminNotes == null || !adminNotes.contains("Önceki kalemler:")) {
            logger.warning("No 'Önceki kalemler:' found in admin notes");
            return originalItems;
        }

        try {
            String[] lines = adminNotes.split("\n");
            for (String line : lines) {
                if (line.contains("Önceki kalemler:")) {
                    // "Önceki kalemler: Ürün A x2 (100.00 TRY), Ürün B x1 (50.00 TRY) (Toplam: 150.00 TRY)"
                    String itemsText = line.substring(line.indexOf("Önceki kalemler:") + "Önceki kalemler:".length());

                    // "(Toplam:" dan önceki kısmı al
                    if (itemsText.contains("(Toplam:")) {
                        itemsText = itemsText.substring(0, itemsText.indexOf("(Toplam:")).trim();
                    }

                    // Virgülle ayrılmış ürünleri parse et
                    String[] items = itemsText.split(", ");
                    for (String item : items) {
                        try {
                            // "Ürün Adı x2 (100.00 TRY)" formatını parse et
                            if (item.contains(" x") && item.contains("(") && item.contains(")")) {
                                int xIndex = item.lastIndexOf(" x");
                                int openParenIndex = item.lastIndexOf("(");
                                int closeParenIndex = item.lastIndexOf(")");

                                if (xIndex > 0 && openParenIndex > xIndex && closeParenIndex > openParenIndex) {
                                    String productName = item.substring(0, xIndex).trim();
                                    String quantityStr = item.substring(xIndex + 2, openParenIndex).trim();
                                    String priceStr = item.substring(openParenIndex + 1, closeParenIndex).trim();

                                    // Quantity parse et
                                    int quantity = Integer.parseInt(quantityStr);

                                    // Price parse et (format: "100.00 TRY")
                                    String[] priceParts = priceStr.split(" ");
                                    if (priceParts.length >= 1) {
                                        BigDecimal totalPrice = new BigDecimal(priceParts[0]);
                                        Map<String, Object> itemInfo = itemInfo(totalPrice, quantity, productName);
                                        originalItems.add(itemInfo);

                                        logger.info("Parsed original item: " + productName + " x" + quantity + " = " + totalPrice);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.warning("Could not parse item: " + item + " - " + e.getMessage());
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.warning("Error parsing original items: " + e.getMessage());
        }

        logger.info("Extracted " + originalItems.size() + " original items from admin notes");
        return originalItems;
    }

    private Map<String, Object> itemInfo(BigDecimal totalPrice, int quantity, String productName) {
        BigDecimal unitPrice = totalPrice.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);

        Map<String, Object> itemInfo = new HashMap<>();
        itemInfo.put("productName", productName);
        itemInfo.put("quantity", quantity);
        itemInfo.put("unitPrice", formatCurrency(unitPrice));
        itemInfo.put("totalPrice", formatCurrency(totalPrice));
        return itemInfo;
    }

    @Async("mailTaskExecutor")
    public CompletableFuture<Boolean> sendOrderAutoCancelledNotificationToCustomer(Order order, String reason) {
        if (!isMailEnabled()) {
            logger.info("Auto-cancelled customer notification disabled, skipping");
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Sending auto-cancelled notification to customer for order: " + order.getOrderNumber());

        try {
            AppUser customer = order.getUser();

            if (!isCustomerNotificationEnabled(customer)) {
                logger.info("Customer email notifications disabled for: " + customer.getEmail());
                return CompletableFuture.completedFuture(false);
            }

            String subject = generateSubject("ORDER_AUTO_CANCELLED", order.getOrderNumber());
            String htmlContent = generateOrderAutoCancelledEmailTemplate(order, reason);

            boolean sent = sendEmailWithRetry(customer.getEmail(), subject, htmlContent);

            if (sent) {
                logger.info("Auto-cancelled notification sent to customer: " + customer.getEmail());
            }

            return CompletableFuture.completedFuture(sent);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending auto-cancelled customer notification", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Admin'lere otomatik iptal bildirimi gönder
     */
    @Async("mailTaskExecutor")
    public CompletableFuture<Boolean> sendOrderAutoCancelledNotificationToAdmins(Order order, String reason) {
        if (!isMailEnabled()) {
            logger.info("Auto-cancelled admin notification disabled, skipping");
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Sending auto-cancelled notification to admins for order: " + order.getOrderNumber());

        try {
            List<AppUser> adminUsers = appUserRepository.findAdminUsersForEmailNotification();

            if (adminUsers.isEmpty()) {
                logger.warning("No admin users found for auto-cancel notification");
                return CompletableFuture.completedFuture(false);
            }

            String subject = "SİSTEM OTOMATIK İPTALİ - " + order.getOrderNumber();
            String htmlContent = generateOrderAutoCancelledAdminEmailTemplate(order, reason);

            int successCount = 0;
            for (AppUser admin : adminUsers) {
                try {
                    boolean sent = sendEmailWithRetry(admin.getEmail(), subject, htmlContent);
                    if (sent) successCount++;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to send auto-cancel notification to admin: " + admin.getEmail(), e);
                }
            }

            logger.info("Auto-cancel notification sent to " + successCount + "/" + adminUsers.size() + " admins");
            return CompletableFuture.completedFuture(successCount > 0);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending auto-cancelled admin notification", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    @Async("mailTaskExecutor")
    public CompletableFuture<Boolean> sendOrderEditRejectedNotificationToAdmins(Order order, String rejectionReason) {
        if (!isMailEnabled() || !orderEditRejectedNotificationEnabled) {
            logger.info("Order edit rejected notification disabled, skipping");
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Sending order edit rejected notification to admins for order: " + order.getOrderNumber());

        try {
            // Admin ve super admin kullanıcılarını getir
            List<AppUser> adminUsers = appUserRepository.findAdminUsersForEmailNotification();

            if (adminUsers.isEmpty()) {
                logger.warning("No admin users found for order edit rejected notification");
                return CompletableFuture.completedFuture(false);
            }

            String subject = "MÜŞTERİ RED - Düzenlenmiş Sipariş Reddedildi - " + order.getOrderNumber();
            String htmlContent = generateOrderEditRejectedEmailTemplate(order, rejectionReason);

            int successCount = 0;
            for (AppUser admin : adminUsers) {
                try {
                    boolean sent = sendEmailWithRetry(admin.getEmail(), subject, htmlContent);
                    if (sent) {
                        successCount++;
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to send order edit rejected notification to admin: " + admin.getEmail(), e);
                }
            }

            logger.info("Order edit rejected notification sent to " + successCount + "/" + adminUsers.size() + " admin users");
            return CompletableFuture.completedFuture(successCount > 0);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending order edit rejected notification", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Sipariş düzenleme reddi email template'i (Admin için)
     */
    private String generateOrderEditRejectedEmailTemplate(Order order, String rejectionReason) {
        Context context = createBaseContext(order);
        context.setVariable("rejectionReason", rejectionReason);
        context.setVariable("orderDetailUrlRejectedAdmin", baseUrl + "/admin/orders/" + order.getId());
        context.setVariable("customerName", order.getUser().getFirstName() + " " + order.getUser().getLastName());
        context.setVariable("rejectionTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));

        try {
            return processTemplate("emails/order-edit-rejected-admin", context);
        } catch (Exception e) {
            logger.warning("Template processing failed for order edit rejected admin email: " + e.getMessage());
            return generateFallbackOrderEditRejectedAdminEmail(order, rejectionReason);
        }
    }

    /**
     * Fallback admin email'i (müşteri düzenleme reddi)
     */
    private String generateFallbackOrderEditRejectedAdminEmail(Order order, String rejectionReason) {
        return "<html><body>" +
                "<h2>🚫 MÜŞTERİ DÜZENLEMEYİ REDDETTİ</h2>" +
                "<p><strong>Sipariş No:</strong> " + order.getOrderNumber() + "</p>" +
                "<p><strong>Müşteri:</strong> " + order.getUser().getFirstName() + " " + order.getUser().getLastName() + "</p>" +
                "<p><strong>Bayi:</strong> " + order.getUser().getDealer().getName() + "</p>" +
                "<p><strong>Toplam Tutar:</strong> " + formatCurrency(order.getTotalAmount()) + "</p>" +
                "<p><strong>Red Tarihi:</strong> " + formatDate(LocalDateTime.now()) + "</p>" +
                "<hr>" +
                "<p><strong>Red Nedeni:</strong> " + rejectionReason + "</p>" +
                "<p>Müşteri admin tarafından düzenlenen siparişi reddetti ve sipariş iptal edildi.</p>" +
                "<p>Stoklar otomatik olarak iade edildi.</p>" +
                "<hr>" +
                "<h3>📋 Alınacak Aksiyonlar</h3>" +
                "<ul>" +
                "<li>Müşteri ile iletişime geçilebilir</li>" +
                "<li>Alternatif çözümler sunulabilir</li>" +
                "<li>Yeni teklif hazırlanabilir</li>" +
                "</ul>" +
                "</body></html>";
    }

    /**
     * Müşteri otomatik iptal email template'i
     */
    private String generateOrderAutoCancelledEmailTemplate(Order order, String reason) {
        Context context = createBaseContext(order);
        context.setVariable("cancellationReason", reason);
        context.setVariable("supportEmail", "info@nafx.com.tr");
        context.setVariable("supportPhone", "+90 312 750 04 16");
        context.setVariable("mobilePhone", "+90 507 916 42 73");
        context.setVariable("hoursWaited", 48);

        try {
            return processTemplate("emails/order-auto-cancelled-customer", context);
        } catch (Exception e) {
            logger.warning("Template processing failed for auto-cancelled customer email: " + e.getMessage());
            return generateFallbackAutoCancelledCustomerEmail(order, reason);
        }
    }

    /**
     * Admin otomatik iptal email template'i
     */
    private String generateOrderAutoCancelledAdminEmailTemplate(Order order, String reason) {
        Context context = createBaseContext(order);
        context.setVariable("cancellationReason", reason);
        context.setVariable("systemAction", "OTOMATIK İPTAL");
        context.setVariable("hoursWaited", 48);

        try {
            return processTemplate("emails/order-auto-cancelled-admin", context);
        } catch (Exception e) {
            logger.warning("Template processing failed for auto-cancelled admin email: " + e.getMessage());
            return generateFallbackAutoCancelledAdminEmail(order, reason);
        }
    }

    /**
     * Fallback müşteri email'i
     */
    private String generateFallbackAutoCancelledCustomerEmail(Order order, String reason) {
        return "<html><body>" +
                "<h2>⏰ Siparişiniz Otomatik İptal Edildi</h2>" +
                "<p><strong>Sipariş No:</strong> " + order.getOrderNumber() + "</p>" +
                "<p><strong>Müşteri:</strong> " + order.getUser().getFirstName() + " " + order.getUser().getLastName() + "</p>" +
                "<p><strong>Toplam Tutar:</strong> " + formatCurrency(order.getTotalAmount()) + "</p>" +
                "<hr>" +
                "<p><strong>Otomatik İptal Nedeni:</strong></p>" +
                "<p>" + reason + "</p>" +
                "<p>Siparişiniz 2 gün içinde onaylanmadığı için sistem tarafından otomatik olarak iptal edilmiştir.</p>" +
                "<hr>" +
                "<h3>📞 İletişim</h3>" +
                "<p>Sorularınız için:</p>" +
                "<ul>" +
                "<li>E-posta: destek@maxxglobal.com.tr</li>" +
                "<li>Telefon: +90 312 XXX XX XX</li>" +
                "</ul>" +
                "<p>Bu otomatik bir bildirimdir.</p>" +
                "</body></html>";
    }

    /**
     * Fallback admin email'i
     */
    private String generateFallbackAutoCancelledAdminEmail(Order order, String reason) {
        return "<html><body>" +
                "<h2>🤖 SİSTEM OTOMATIK İPTALİ</h2>" +
                "<p><strong>Sipariş No:</strong> " + order.getOrderNumber() + "</p>" +
                "<p><strong>Müşteri:</strong> " + order.getUser().getFirstName() + " " + order.getUser().getLastName() + "</p>" +
                "<p><strong>Bayi:</strong> " + order.getUser().getDealer().getName() + "</p>" +
                "<p><strong>Toplam Tutar:</strong> " + formatCurrency(order.getTotalAmount()) + "</p>" +
                "<p><strong>İptal Tarihi:</strong> " + formatDate(LocalDateTime.now()) + "</p>" +
                "<hr>" +
                "<p><strong>İptal Nedeni:</strong> " + reason + "</p>" +
                "<p>Bu sipariş sistem tarafından otomatik olarak iptal edildi.</p>" +
                "<p>Stoklar iade edildi ve müşteriye bildirim gönderildi.</p>" +
                "</body></html>";
    }
}
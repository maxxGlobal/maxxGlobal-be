// src/main/java/com/maxx_global/service/MailService.java
package com.maxx_global.service;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.model.*;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Order;
import com.maxx_global.entity.OrderItem;
import com.maxx_global.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class MailService {

    private static final Logger logger = Logger.getLogger(MailService.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    @Value("${app.mail.pdf-attachment.enabled:true}") // ✅ EKLE
    private Boolean pdfAttachmentEnabled;

    private final AmazonSimpleEmailService sesClient;
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

    public MailService(AmazonSimpleEmailService sesClient,
                       TemplateEngine templateEngine,
                       AppUserRepository appUserRepository, OrderPdfService orderPdfService) {
        this.sesClient = sesClient;
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
     * Sipariş düzenlendiğinde müşteriye mail gönder
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

            boolean sent = sendEmailWithRetry(customer.getEmail(), subject, htmlContent);

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
            // PDF yok ise normal mail gönder
            return sendEmailWithRetry(toEmail, subject, htmlContent);
        }

        try {
            // RFC 2822 formatında raw email oluştur
            String rawEmail = createRawEmailWithAttachment(
                    toEmail, subject, htmlContent, pdfAttachment, attachmentName);

            // SES Raw Message ile gönder
            RawMessage rawMessage = new RawMessage();
            rawMessage.setData(java.nio.ByteBuffer.wrap(rawEmail.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

            SendRawEmailRequest request = new SendRawEmailRequest()
                    .withSource(fromEmail)
                    .withDestinations(toEmail)
                    .withRawMessage(rawMessage);

            SendRawEmailResult result = sesClient.sendRawEmail(request);

            logger.info("Email with PDF attachment sent successfully to " + toEmail +
                    ", MessageId: " + result.getMessageId() +
                    ", PDF size: " + pdfAttachment.length + " bytes");
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to send email with attachment to " + toEmail, e);
            // PDF eki ile gönderilemezse normal mail göndermeyi dene
            logger.info("Falling back to sending email without PDF attachment");
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
     * RFC 2822 formatında raw email oluştur (PDF eki ile)
     */
    private String createRawEmailWithAttachment(String toEmail, String subject,
                                                String htmlContent, byte[] pdfAttachment,
                                                String attachmentName) {
        String boundary = "----=_NextPart_" + System.currentTimeMillis();

        StringBuilder rawEmail = new StringBuilder();

        // Email headers
        rawEmail.append("From: ").append(fromEmail).append("\r\n");
        rawEmail.append("To: ").append(toEmail).append("\r\n");
        rawEmail.append("Subject: =?UTF-8?B?").append(java.util.Base64.getEncoder().encodeToString(subject.getBytes(java.nio.charset.StandardCharsets.UTF_8))).append("?=").append("\r\n");
        rawEmail.append("MIME-Version: 1.0").append("\r\n");
        rawEmail.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"").append("\r\n");
        rawEmail.append("\r\n");

        // HTML content part
        rawEmail.append("--").append(boundary).append("\r\n");
        rawEmail.append("Content-Type: text/html; charset=UTF-8").append("\r\n");
        rawEmail.append("Content-Transfer-Encoding: quoted-printable").append("\r\n");
        rawEmail.append("\r\n");
        rawEmail.append(toQuotedPrintable(htmlContent)).append("\r\n");
        rawEmail.append("\r\n");

        // PDF attachment part
        if (pdfAttachment != null && pdfAttachment.length > 0) {
            String base64Pdf = java.util.Base64.getEncoder().encodeToString(pdfAttachment);

            rawEmail.append("--").append(boundary).append("\r\n");
            rawEmail.append("Content-Type: application/pdf; name=\"").append(attachmentName).append(".pdf\"").append("\r\n");
            rawEmail.append("Content-Disposition: attachment; filename=\"").append(attachmentName).append(".pdf\"").append("\r\n");
            rawEmail.append("Content-Transfer-Encoding: base64").append("\r\n");
            rawEmail.append("\r\n");

            // Base64'ü 76 karakter satırlar halinde böl
            int index = 0;
            while (index < base64Pdf.length()) {
                int endIndex = Math.min(index + 76, base64Pdf.length());
                rawEmail.append(base64Pdf.substring(index, endIndex)).append("\r\n");
                index = endIndex;
            }
            rawEmail.append("\r\n");
        }

        // Email sonlandırma
        rawEmail.append("--").append(boundary).append("--").append("\r\n");

        return rawEmail.toString();
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

    /**
     * Temel mail gönderim metodu
     */
    private boolean sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            validateEmailParams(toEmail, subject, htmlContent);

            Destination destination = new Destination().withToAddresses(toEmail);

            Content subjectContent = new Content()
                    .withData(subject)
                    .withCharset("UTF-8");

            Content bodyContent = new Content()
                    .withData(htmlContent)
                    .withCharset("UTF-8");

            Body body = new Body().withHtml(bodyContent);

            Message message = new Message()
                    .withSubject(subjectContent)
                    .withBody(body);

            SendEmailRequest emailRequest = new SendEmailRequest()
                    .withSource(fromEmail)
                    .withDestination(destination)
                    .withMessage(message);

            SendEmailResult result = sesClient.sendEmail(emailRequest);

            logger.info("Email sent successfully to " + toEmail + ", MessageId: " + result.getMessageId());
            return true;

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
        context.setVariable("orderDetailUrl", baseUrl + "/admin/orders/" + order.getId());
        return processTemplate("emails/new-order-notification", context);
    }

    /**
     * Sipariş onaylandı email template'i
     */
    private String generateOrderApprovedEmailTemplate(Order order) {
        Context context = createBaseContext(order);
        context.setVariable("orderDetailUrl", baseUrl + "/orders/" + order.getId());
        return processTemplate("emails/order-approved-notification", context);
    }

    /**
     * Sipariş reddedildi email template'i
     */
    private String generateOrderRejectedEmailTemplate(Order order) {
        Context context = createBaseContext(order);
        context.setVariable("rejectionReason", order.getAdminNotes());
        return processTemplate("emails/order-rejected-notification", context);
    }

    /**
     * Sipariş düzenlendi email template'i
     */
    private String generateOrderEditedEmailTemplate(Order order) {
        Context context = createBaseContext(order);
        context.setVariable("orderDetailUrl", baseUrl + "/orders/" + order.getId());
        context.setVariable("approveEditUrl", baseUrl + "/orders/edited/" + order.getId());
        return processTemplate("emails/order-edited-notification", context);
    }

    /**
     * Sipariş durumu değişti email template'i
     */
    private String generateOrderStatusChangeEmailTemplate(Order order, String previousStatus) {
        Context context = createBaseContext(order);
        context.setVariable("previousStatus", getStatusDisplayName(previousStatus));
        context.setVariable("currentStatus", getStatusDisplayName(order.getOrderStatus().name()));
        context.setVariable("orderDetailUrl", baseUrl + "/orders/" + order.getId());
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

        return context;
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
    private String generateOrderItemsSummary(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return "Sipariş kalemi bulunamadı";
        }

        StringBuilder summary = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            summary.append("• ")
                    .append(item.getProduct().getName())
                    .append(" x")
                    .append(item.getQuantity())
                    .append(" adet - ")
                    .append(formatCurrency(item.getTotalPrice()))
                    .append("\n");
        }
        return summary.toString();
    }

    // ==================== HEALTH CHECK METHODS ====================

    /**
     * Mail servisi sağlık kontrolü
     */
    public boolean isHealthy() {
        try {
            if (!isMailEnabled()) {
                return false;
            }

            // SES bağlantısını test et
            GetSendQuotaResult quotaResult = sesClient.getSendQuota();
            return quotaResult != null;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Mail service health check failed", e);
            return false;
        }
    }

    /**
     * SES quota bilgilerini getir
     */
    public Object getMailServiceInfo() {
        try {
            if (!isMailEnabled()) {
                return "Mail service is disabled";
            }

            GetSendQuotaResult quota = sesClient.getSendQuota();
            GetSendStatisticsResult stats = sesClient.getSendStatistics();

            return new Object() {
                public final double maxSend24Hour = quota.getMax24HourSend();
                public final double maxSendRate = quota.getMaxSendRate();
                public final double sentLast24Hours = quota.getSentLast24Hours();
                public final boolean isHealthy = isHealthy();
                public final int statisticsCount = stats.getSendDataPoints().size();
                public final boolean pdfAttachmentEnabled = MailService.this.pdfAttachmentEnabled; // ✅ EKLE

            };

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting mail service info", e);
            return "Error: " + e.getMessage();
        }
    }
}
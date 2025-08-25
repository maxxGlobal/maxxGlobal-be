package com.maxx_global.service;

import com.lowagie.text.pdf.BaseFont;
import com.maxx_global.entity.Order;
import com.maxx_global.entity.OrderItem;
import com.maxx_global.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Logger;

@Service
public class OrderPdfService {

    private static final Logger logger = Logger.getLogger(OrderPdfService.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // Şirket bilgileri - static fields
    private static final String COMPANY_NAME = "MAXX GLOBAL";
    private static final String COMPANY_ADDRESS = "Merkez Ofis: Atatürk Bulvarı No:123 Çankaya/ANKARA";
    private static final String COMPANY_PHONE = "Tel: +90 312 XXX XX XX";
    private static final String COMPANY_EMAIL = "E-posta: info@maxxglobal.com.tr";
    private static final String COMPANY_MERSIS = "Mersis No: 0123456789012345";

    // Footer bilgileri
    private static final String FOOTER_COMPANY = "MAXX GLOBAL A.Ş.";
    private static final String FOOTER_SUPPORT = "Müşteri hizmetleri: destek@maxxglobal.com.tr | +90 312 XXX XX XX";

    private final OrderRepository orderRepository;
    private final TemplateEngine templateEngine;

    public OrderPdfService(OrderRepository orderRepository, TemplateEngine templateEngine) {
        this.orderRepository = orderRepository;
        this.templateEngine = templateEngine;
    }

    /**
     * Sipariş PDF'ini oluşturur
     */
    public byte[] generateOrderPdf(Long orderId) {
        logger.info("Generating PDF for order: " + orderId);

        try {
            // Siparişi getir
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));

            // HTML içeriğini oluştur
            String htmlContent = generateOrderHtmlContent(order);

            // PDF'i oluştur ve byte array olarak dön
            return convertHtmlToPdf(htmlContent);

        } catch (Exception e) {
            logger.severe("Error generating PDF for order " + orderId + ": " + e.getMessage());
            throw new RuntimeException("PDF oluşturulurken hata oluştu: " + e.getMessage(), e);
        }
    }

    /**
     * Sipariş HTML içeriğini oluşturur
     */
    private String generateOrderHtmlContent(Order order) {
        Context context = new Context(new Locale("tr", "TR"));

        // Sipariş bilgileri
        context.setVariable("order", order);
        context.setVariable("orderItems", order.getItems());

        // Şirket bilgileri
        context.setVariable("companyName", COMPANY_NAME);
        context.setVariable("companyAddress", COMPANY_ADDRESS);
        context.setVariable("companyPhone", COMPANY_PHONE);
        context.setVariable("companyEmail", COMPANY_EMAIL);
        context.setVariable("companyMersis", COMPANY_MERSIS);

        // Footer bilgileri
        context.setVariable("footerCompany", FOOTER_COMPANY);
        context.setVariable("footerSupport", FOOTER_SUPPORT);

        // Bayi bilgileri (Order'dan)
        context.setVariable("dealer", order.getUser().getDealer());
        context.setVariable("dealerContact", order.getUser());

        // Formatlanmış tarih
        context.setVariable("formattedDate", order.getOrderDate().format(DATE_FORMATTER));
        context.setVariable("formattedTime", order.getOrderDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));

        // Finansal bilgiler
        context.setVariable("subtotal", calculateSubtotal(order));
        context.setVariable("formattedSubtotal", formatCurrency(calculateSubtotal(order)));
        context.setVariable("formattedDiscount", formatCurrency(order.getDiscountAmount()));
        context.setVariable("formattedTotal", formatCurrency(order.getTotalAmount()));

        // İndirim var mı?
        context.setVariable("hasDiscount", order.getDiscountAmount() != null &&
                order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0);

        // KDV hesaplama (toplam tutarın %20'si)
        BigDecimal kdv = calculateKdv(order.getTotalAmount());
        context.setVariable("kdv", kdv);
        context.setVariable("formattedKdv", formatCurrency(kdv));

        return templateEngine.process("pdf/order-invoice", context);
    }

    /**
     * HTML'i PDF'e çevirir
     */
    private byte[] convertHtmlToPdf(String htmlContent) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            ITextRenderer renderer = new ITextRenderer();

            // Türkçe karakter desteği için font ekle
            renderer.getFontResolver()
                    .addFont("src/main/resources/fonts/DejaVuSans.ttf",
                            BaseFont.IDENTITY_H, BaseFont.EMBEDDED);

            renderer.setDocumentFromString(htmlContent);
            renderer.layout();
            renderer.createPDF(outputStream);

            logger.info("PDF generated successfully");
            return outputStream.toByteArray();

        } catch (Exception e) {
            logger.severe("Error converting HTML to PDF: " + e.getMessage());
            throw new RuntimeException("HTML to PDF conversion failed", e);
        }
    }

    /**
     * Ara toplam hesapla
     */
    private BigDecimal calculateSubtotal(Order order) {
        BigDecimal calculated = order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return calculated.subtract(calculateKdv(order.getTotalAmount()));
    }

    /**
     * KDV hesapla (%20)
     */
    private BigDecimal calculateKdv(BigDecimal amount) {
        if (amount == null) return BigDecimal.ZERO;
        return amount.multiply(new BigDecimal("0.20")).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Para formatı
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "₺0,00";
        return String.format("₺%,.2f", amount).replace(',', 'X').replace('.', ',').replace('X', '.');
    }

    /**
     * Sipariş PDF dosya adı oluştur
     */
    public String generatePdfFileName(Order order) {
        return String.format("Siparis_%s_%s.pdf",
                order.getOrderNumber().replace("-", "_"),
                order.getOrderDate().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
    }
}
// OrderPdfService.java - Kapsamlı düzeltme

package com.maxx_global.service;

import com.lowagie.text.pdf.BaseFont;
import com.maxx_global.entity.Discount;
import com.maxx_global.entity.Order;
import com.maxx_global.entity.OrderItem;
import com.maxx_global.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Logger;

@Service
public class OrderPdfService {

    private static final Logger logger = Logger.getLogger(OrderPdfService.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // ✅ Türkçe karakter sorunu çözümü - Statik metinler
    private static final String COMPANY_NAME = "MEDİNTERA";
    private static final String COMPANY_ADDRESS = "Ehlibeyt Mah. Tekstilciler Cd. 35 / 7 Çankaya, Ankara – Türkiye";
    private static final String COMPANY_PHONE = "Tel: 0(312) 750 04 16";
    private static final String COMPANY_EMAIL = "Mail: bilgi@medintera.com.tr";
    private static final String COMPANY_MERSIS = "VKN: 6141586045-BAŞKENT";

    private static final String FOOTER_COMPANY = "MEDİNTERA MİMARLIK TASARIM MEDİKAL SAN. VE TİC. LTD. ŞTİ";
    private static final String FOOTER_SUPPORT = "Destek: +90 507 916 42 73 | bilgi@medintera.com.tr";

    private final OrderRepository orderRepository;
    private final TemplateEngine templateEngine;

    public OrderPdfService(OrderRepository orderRepository, TemplateEngine templateEngine) {
        this.orderRepository = orderRepository;
        this.templateEngine = templateEngine;
    }

    public byte[] generateOrderPdf(Long orderId) {
        logger.info("Generating PDF for order: " + orderId);

        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new EntityNotFoundException("Siparis bulunamadi: " + orderId));

            String htmlContent = generateOrderHtmlContent(order);
            return convertHtmlToPdf(htmlContent);

        } catch (Exception e) {
            logger.severe("Error generating PDF for order " + orderId + ": " + e.getMessage());
            throw new RuntimeException("PDF olusturulurken hata olustu: " + e.getMessage(), e);
        }
    }

    public byte[] generateOrderPdf(Order order) {
        try {
            String htmlContent = generateOrderHtmlContent(order);
            return convertHtmlToPdf(htmlContent);
        } catch (Exception e) {
            throw new RuntimeException("PDF olusturulurken hata olustu: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ HTML içeriği oluştururken Türkçe karakterleri düzelt
     */
    private String generateOrderHtmlContent(Order order) {
        Context context = new Context(new Locale("tr", "TR"));

        // Sipariş bilgileri
        context.setVariable("order", order);
        context.setVariable("orderItems", order.getItems());

        // ✅ Türkçe karakter problemini çözmek için özel değişkenler
        context.setVariable("invoiceTitle", "SIPARIS FATURASI"); // İ harfleri ASCII olarak
        context.setVariable("dealerInfoTitle", "BAYI BILGILERI");
        context.setVariable("productNameHeader", "URUN ADI");
        context.setVariable("quantityHeader", "MIKTAR");
        context.setVariable("unitPriceHeader", "BIRIM FIYAT");
        context.setVariable("totalHeader", "TOPLAM");
        context.setVariable("productsTotal", "Urunler Toplami");
        context.setVariable("netAmountLabel", "Net Tutar (KDV Haric)");
        context.setVariable("vatLabel", "KDV (%20)");
        context.setVariable("grandTotalLabel", "GENEL TOPLAM");

        // Şirket bilgileri
        context.setVariable("companyName", COMPANY_NAME);
        context.setVariable("companyAddress", COMPANY_ADDRESS);
        context.setVariable("companyPhone", COMPANY_PHONE);
        context.setVariable("companyEmail", COMPANY_EMAIL);
        context.setVariable("companyMersis", COMPANY_MERSIS);

        // Footer bilgileri
        context.setVariable("footerCompany", FOOTER_COMPANY);
        context.setVariable("footerSupport", FOOTER_SUPPORT);

        // Bayi bilgileri
        context.setVariable("dealer", order.getUser().getDealer());
        context.setVariable("dealerContact", order.getUser());

        // ✅ Bayi bilgi etiketleri - Türkçe karakter sorununu önle
        context.setVariable("dealerNameLabel", "Bayi Adi");
        context.setVariable("contactPersonLabel", "Yetkili");
        context.setVariable("addressLabel", "Adres");
        context.setVariable("phoneLabel", "Telefon");
        context.setVariable("emailLabel", "E-posta");

        // Formatlanmış tarih
        context.setVariable("formattedDate", order.getOrderDate().format(DATE_FORMATTER));
        context.setVariable("formattedTime", order.getOrderDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));

        // Finansal bilgiler
        addFinancialInfoToContext(context, order);

        return templateEngine.process("pdf/order-invoice", context);
    }

    /**
     * ✅ Gelişmiş HTML'den PDF'e çevirme - Font desteği ile
     */
    private byte[] convertHtmlToPdf(String htmlContent) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            ITextRenderer renderer = new ITextRenderer();

            // ✅ YENİ: Font resolver ile Türkçe karakter desteği
            configureFonts(renderer);

            renderer.setDocumentFromString(htmlContent);
            renderer.layout();
            renderer.createPDF(outputStream);

            byte[] pdfBytes = outputStream.toByteArray();
            logger.info("✅ PDF generated successfully, size: " + pdfBytes.length + " bytes");

            return pdfBytes;

        } catch (Exception e) {
            logger.severe("❌ PDF generation failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("PDF olusturulamadi: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ YENİ: Font yapılandırması
     */
    /**
     * ✅ DÜZELTME: Font yapılandırması - Doğru IText API kullanımı
     */
    private void configureFonts(ITextRenderer renderer) {
        try {
            logger.info("🔍 Font loading started...");

            // ✅ YÖNTEM 1: ClassPathResource ile doğru font yükleme
            try {
                ClassPathResource fontResource = new ClassPathResource("fonts/DejaVuSans.ttf");
                logger.info("📂 Font resource path: " + fontResource.getPath());
                logger.info("📄 Font resource exists: " + fontResource.exists());

                if (fontResource.exists()) {
                    // ✅ DÜZELTME: URL üzerinden font yükleme
                    String fontPath = fontResource.getURL().toString();
                    renderer.getFontResolver().addFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    logger.info("✅ DejaVu Sans font loaded successfully from classpath via URL");
                    return;
                }
            } catch (Exception e) {
                logger.warning("❌ Could not load DejaVu Sans from classpath URL: " + e.getMessage());
            }

            // ✅ YÖNTEM 2: Temporary file approach (ClassPathResource için)
            try {
                ClassPathResource fontResource = new ClassPathResource("fonts/DejaVuSans.ttf");
                if (fontResource.exists()) {
                    try (InputStream fontStream = fontResource.getInputStream()) {
                        // ✅ Geçici dosya oluştur
                        java.io.File tempFontFile = java.io.File.createTempFile("dejavu-sans", ".ttf");
                        tempFontFile.deleteOnExit(); // JVM çıkışında sil

                        // InputStream'i geçici dosyaya yaz
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFontFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fontStream.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                        }

                        // Geçici dosyayı font resolver'a ekle
                        renderer.getFontResolver().addFont(tempFontFile.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                        logger.info("✅ DejaVu Sans font loaded via temporary file: " + tempFontFile.getAbsolutePath());
                        return;
                    }
                }
            } catch (Exception e) {
                logger.warning("❌ Could not load DejaVu Sans via temporary file: " + e.getMessage());
            }

            // ✅ YÖNTEM 3: Target/classes klasöründen doğrudan yükleme
            try {
                String targetFontPath = "target/classes/fonts/DejaVuSans.ttf";
                java.io.File targetFont = new java.io.File(targetFontPath);
                logger.info("🔍 Checking target font path: " + targetFont.getAbsolutePath());
                logger.info("📄 Target font exists: " + targetFont.exists());

                if (targetFont.exists()) {
                    renderer.getFontResolver().addFont(targetFont.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    logger.info("✅ Font loaded from target directory: " + targetFont.getAbsolutePath());
                    return;
                }
            } catch (Exception e) {
                logger.warning("❌ Could not load font from target directory: " + e.getMessage());
            }

            // ✅ YÖNTEM 4: Çalışma dizini + relative path
            try {
                String workingDir = System.getProperty("user.dir");
                String[] relativePaths = {
                        workingDir + "/target/classes/fonts/DejaVuSans.ttf",
                        workingDir + "/src/main/resources/fonts/DejaVuSans.ttf",
                        "fonts/DejaVuSans.ttf"
                };

                for (String path : relativePaths) {
                    java.io.File fontFile = new java.io.File(path);
                    logger.info("🔍 Trying relative path: " + fontFile.getAbsolutePath());

                    if (fontFile.exists()) {
                        renderer.getFontResolver().addFont(fontFile.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                        logger.info("✅ Font loaded from relative path: " + fontFile.getAbsolutePath());
                        return;
                    }
                }
            } catch (Exception e) {
                logger.warning("❌ Relative path loading failed: " + e.getMessage());
            }

            // ✅ YÖNTEM 5: Sistem fontlarını dene
            try {
                String[] systemFontPaths = getSystemFontPaths();

                for (String systemPath : systemFontPaths) {
                    try {
                        java.io.File systemFont = new java.io.File(systemPath);
                        if (systemFont.exists()) {
                            renderer.getFontResolver().addFont(systemPath, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                            logger.info("✅ System font loaded: " + systemPath);
                            return;
                        }
                    } catch (Exception e) {
                        // Sessizce devam et
                    }
                }
            } catch (Exception e) {
                logger.warning("❌ System font loading failed: " + e.getMessage());
            }

            // ✅ Son durum
            logger.warning("⚠️ Using default font - Turkish characters may not display correctly");
            logger.warning("💡 To fix this issue:");
            logger.warning("   1. Ensure fonts exist in: target/classes/fonts/DejaVuSans.ttf");
            logger.warning("   2. Run: mvn clean compile");
            logger.warning("   3. Check file permissions");

        } catch (Exception e) {
            logger.severe("💥 Font configuration completely failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ Sistem font yollarını döndür
     */
    private String[] getSystemFontPaths() {
        return new String[] {
                // Windows paths
                "C:/Windows/Fonts/arial.ttf",
                "C:/Windows/Fonts/calibri.ttf",
                "C:/Windows/Fonts/tahoma.ttf",
                "C:/Windows/Fonts/verdana.ttf",
                // Linux paths
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/TTF/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
                "/usr/share/fonts/ubuntu/Ubuntu-R.ttf",
                // Mac paths
                "/System/Library/Fonts/Arial.ttf",
                "/System/Library/Fonts/Helvetica.ttc",
                "/Library/Fonts/Arial.ttf"
        };
    }

    /**
     * ✅ Para formatı - TL sembolü yerine TL yazısı
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0,00 TL";
        return String.format("%,.2f TL", amount).replace('.', ',').replace(',', '.').replace('.', ',');
    }

    // ✅ Finansal bilgileri context'e ekleme
    private void addFinancialInfoToContext(Context context, Order order) {
        BigDecimal itemsSubtotal = calculateItemsSubtotal(order);

        boolean hasDiscount = order.getAppliedDiscount() != null &&
                order.getDiscountAmount() != null &&
                order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0;

        context.setVariable("hasDiscount", hasDiscount);

        if (hasDiscount) {
            Discount discount = order.getAppliedDiscount();
            context.setVariable("discount", discount);
            context.setVariable("discountName", discount.getName());
            context.setVariable("discountType", getDiscountTypeDisplayName(discount.getDiscountType()));
            context.setVariable("discountValue", discount.getDiscountValue());
            context.setVariable("discountAmount", order.getDiscountAmount());
            context.setVariable("formattedDiscountAmount", formatCurrency(order.getDiscountAmount()));

            context.setVariable("itemsSubtotal", itemsSubtotal);
            context.setVariable("formattedItemsSubtotal", formatCurrency(itemsSubtotal));
            context.setVariable("discountedSubtotal", itemsSubtotal.subtract(order.getDiscountAmount()));
            context.setVariable("formattedDiscountedSubtotal", formatCurrency(itemsSubtotal.subtract(order.getDiscountAmount())));

            BigDecimal discountedAmount = itemsSubtotal.subtract(order.getDiscountAmount());
            BigDecimal netAmount = discountedAmount.divide(new BigDecimal("1.20"), 2, BigDecimal.ROUND_HALF_UP);
            BigDecimal kdv = discountedAmount.subtract(netAmount);

            context.setVariable("netAmount", netAmount);
            context.setVariable("formattedNetAmount", formatCurrency(netAmount));
            context.setVariable("kdv", kdv);
            context.setVariable("formattedKdv", formatCurrency(kdv));
            context.setVariable("savingsAmount", order.getDiscountAmount());
            context.setVariable("formattedSavingsAmount", formatCurrency(order.getDiscountAmount()));

        } else {
            // Normal hesaplama
            context.setVariable("itemsSubtotal", itemsSubtotal);
            context.setVariable("formattedItemsSubtotal", formatCurrency(itemsSubtotal));
            context.setVariable("discountedSubtotal", itemsSubtotal);
            context.setVariable("formattedDiscountedSubtotal", formatCurrency(itemsSubtotal));

            BigDecimal netAmount = itemsSubtotal.divide(new BigDecimal("1.20"), 2, BigDecimal.ROUND_HALF_UP);
            BigDecimal kdv = itemsSubtotal.subtract(netAmount);

            context.setVariable("netAmount", netAmount);
            context.setVariable("formattedNetAmount", formatCurrency(netAmount));
            context.setVariable("kdv", kdv);
            context.setVariable("formattedKdv", formatCurrency(kdv));
            context.setVariable("savingsAmount", BigDecimal.ZERO);
            context.setVariable("formattedSavingsAmount", formatCurrency(BigDecimal.ZERO));
        }

        context.setVariable("totalAmount", order.getTotalAmount());
        context.setVariable("formattedTotal", formatCurrency(order.getTotalAmount()));
        context.setVariable("subtotal", calculateSubtotal(order));
        context.setVariable("formattedSubtotal", formatCurrency(calculateSubtotal(order)));
    }

    private BigDecimal calculateItemsSubtotal(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateSubtotal(Order order) {
        BigDecimal calculated = order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return calculated.subtract(calculateKdv(order.getTotalAmount()));
    }

    private BigDecimal calculateKdv(BigDecimal amount) {
        if (amount == null) return BigDecimal.ZERO;
        return amount.multiply(new BigDecimal("0.20")).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private String getDiscountTypeDisplayName(com.maxx_global.enums.DiscountType discountType) {
        if (discountType == null) return "";
        return switch (discountType) {
            case PERCENTAGE -> "Yuzde Indirim";
            case FIXED_AMOUNT -> "Sabit Tutar Indirim";
        };
    }

    public String generatePdfFileName(Order order) {
        return String.format("Siparis_%s_%s.pdf",
                order.getOrderNumber().replace("-", "_"),
                order.getOrderDate().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
    }
}
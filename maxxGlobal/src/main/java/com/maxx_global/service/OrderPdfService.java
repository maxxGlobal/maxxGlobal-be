// OrderPdfService.java - Kapsamlı düzeltme

package com.maxx_global.service;

import com.lowagie.text.pdf.BaseFont;
import com.maxx_global.entity.Discount;
import com.maxx_global.entity.Order;
import com.maxx_global.entity.OrderItem;
import com.maxx_global.enums.CurrencyType;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Logger;

@Service
public class OrderPdfService {

    private static final Logger logger = Logger.getLogger(OrderPdfService.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final BigDecimal VAT_RATE = new BigDecimal("0.10");
    private static final BigDecimal VAT_DIVISOR = BigDecimal.ONE.add(VAT_RATE);

    // ✅ Türkçe karakter sorunu çözümü - Statik metinler
    private static final String COMPANY_NAME = "MEDİNTERA";
    private static final String COMPANY_ADDRESS = "Ehlibeyt Mah. Tekstilciler Cd. 35 / 7 Çankaya, Ankara – Türkiye";
    private static final String COMPANY_PHONE = "Tel: 0(312) 750 04 16";
    private static final String COMPANY_EMAIL = "Mail: bilgi@medintera.com.tr";
    private static final String COMPANY_MERSIS = "VKN: 6141586045-BAŞKENT";

    private static final String FOOTER_COMPANY = "MEDİNTERA MİMARLIK TASARIM MEDİKAL SAN. VE TİC. LTD. ŞTİ";
    private static final String FOOTER_SUPPORT = "Destek: +90 507 916 42 73 | bilgi@medintera.com.tr";
    private static final String COMPANY_LOGO_PATH = "static/medintera-logo-1.png";
    private static final String TEMPLATE_LOGO_PATH = "../../static/medintera-logo-1.png";

    private final OrderRepository orderRepository;
    private final TemplateEngine templateEngine;
    private final LocalizationService localizationService;

    public OrderPdfService(OrderRepository orderRepository,
                          TemplateEngine templateEngine,
                          LocalizationService localizationService) {
        this.orderRepository = orderRepository;
        this.templateEngine = templateEngine;
        this.localizationService = localizationService;
    }

    public byte[] generateOrderPdf(Long orderId) {
        return generateOrderPdf(orderId, null);
    }

    public byte[] generateOrderPdf(Long orderId, Locale locale) {
        logger.info("Generating PDF for order: " + orderId);

        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new EntityNotFoundException("Siparis bulunamadi: " + orderId));

            String htmlContent = generateOrderHtmlContent(order, locale);
            return convertHtmlToPdf(htmlContent);

        } catch (Exception e) {
            logger.severe("Error generating PDF for order " + orderId + ": " + e.getMessage());
            throw new RuntimeException("PDF olusturulurken hata olustu: " + e.getMessage(), e);
        }
    }

    public byte[] generateOrderPdf(Order order) {
        return generateOrderPdf(order, null);
    }

    public byte[] generateOrderPdf(Order order, Locale locale) {
        try {
            String htmlContent = generateOrderHtmlContent(order, locale);
            return convertHtmlToPdf(htmlContent);
        } catch (Exception e) {
            throw new RuntimeException("PDF olusturulurken hata olustu: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ HTML içeriği oluştururken Türkçe karakterleri düzelt
     */
    private String generateOrderHtmlContent(Order order, Locale locale) {
        Locale templateLocale = locale != null
                ? locale
                : localizationService.getPreferredLocaleOrDefault(order.getUser());
        Context context = new Context(templateLocale);

        // Sipariş bilgileri
        context.setVariable("order", order);
        context.setVariable("orderItems", order.getItems());
        context.setVariable("currency", order.getCurrency());
        context.setVariable("currencySymbol", getCurrencySymbol(order.getCurrency()));

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

        // Formatlanmış tarih
        context.setVariable("formattedDate", order.getOrderDate().format(DATE_FORMATTER.withLocale(templateLocale)));
        context.setVariable("formattedTime", order.getOrderDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", templateLocale)));

        addLocalizedLabels(context, order, templateLocale);
        addFinancialInfoToContext(context, order, templateLocale);

        String htmlContent = templateEngine.process("pdf/order-invoice", context);
        return replaceLogoPathForPdf(htmlContent);
    }

    private String replaceLogoPathForPdf(String htmlContent) {
        String logoUrl = getCompanyLogoFileUrl();
        if (logoUrl == null) {
            return htmlContent;
        }
        return htmlContent.replace(TEMPLATE_LOGO_PATH, logoUrl);
    }

    private String getCompanyLogoFileUrl() {
        try (InputStream inputStream = new ClassPathResource(COMPANY_LOGO_PATH).getInputStream()) {
            Path tempLogoFile = Files.createTempFile("medintera-logo-", ".png");
            Files.copy(inputStream, tempLogoFile, StandardCopyOption.REPLACE_EXISTING);
            tempLogoFile.toFile().deleteOnExit();
            return tempLogoFile.toUri().toString();
        } catch (IOException e) {
            logger.warning("Company logo could not be loaded for PDF: " + e.getMessage());
            return null;
        }
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
        return formatCurrency(amount, null);
    }

    private String formatCurrency(BigDecimal amount, CurrencyType currency) {
        if (amount == null) return "Fiyat bilgisi bulunmuyor";
        return String.format("%,.2f %s", amount, getCurrencySymbol(currency))
                .replace('.', ',')
                .replace(',', '.')
                .replace('.', ',');
    }

    private String getCurrencySymbol(CurrencyType currency) {
        if (currency == null) {
            return "₺";
        }

        return switch (currency) {
            case USD -> "$";
            case EUR -> "€";
            case TRY -> "₺";
        };
    }

    // ✅ Finansal bilgileri context'e ekleme
    private void addFinancialInfoToContext(Context context, Order order, Locale templateLocale) {
        BigDecimal itemsSubtotal = calculateItemsSubtotal(order);
        boolean hasMissingPrice = itemsSubtotal == null;

        boolean hasDiscount = order.getAppliedDiscount() != null &&
                order.getDiscountAmount() != null &&
                order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0;

        context.setVariable("hasDiscount", hasDiscount);

        if (hasMissingPrice) {
            context.setVariable("itemsSubtotal", null);
            context.setVariable("formattedItemsSubtotal", "Fiyat bilgisi bulunmuyor");
            context.setVariable("discountedSubtotal", null);
            context.setVariable("formattedDiscountedSubtotal", "Fiyat bilgisi bulunmuyor");
            context.setVariable("netAmount", null);
            context.setVariable("formattedNetAmount", "Fiyat bilgisi bulunmuyor");
            context.setVariable("kdv", null);
            context.setVariable("formattedKdv", "Fiyat bilgisi bulunmuyor");
        } else if (hasDiscount) {
            Discount discount = order.getAppliedDiscount();
            context.setVariable("discount", discount);
            context.setVariable("discountName", discount.getLocalizedName(localizationService.getLanguage(templateLocale)));
            context.setVariable("discountType", getDiscountTypeDisplayName(discount.getDiscountType(), templateLocale));
            context.setVariable("discountValue", discount.getDiscountValue());
            context.setVariable("discountAmount", order.getDiscountAmount());
            context.setVariable("formattedDiscountAmount", formatCurrency(order.getDiscountAmount(), order.getCurrency()));

            context.setVariable("itemsSubtotal", itemsSubtotal);
            context.setVariable("formattedItemsSubtotal", formatCurrency(itemsSubtotal, order.getCurrency()));
            context.setVariable("discountedSubtotal", itemsSubtotal.subtract(order.getDiscountAmount()));
            context.setVariable("formattedDiscountedSubtotal", formatCurrency(itemsSubtotal.subtract(order.getDiscountAmount()), order.getCurrency()));

            BigDecimal discountedAmount = itemsSubtotal.subtract(order.getDiscountAmount());
            BigDecimal netAmount = discountedAmount.divide(VAT_DIVISOR, 2, BigDecimal.ROUND_HALF_UP);
            BigDecimal kdv = discountedAmount.subtract(netAmount);

            context.setVariable("netAmount", netAmount);
            context.setVariable("formattedNetAmount", formatCurrency(netAmount, order.getCurrency()));
            context.setVariable("kdv", kdv);
            context.setVariable("formattedKdv", formatCurrency(kdv, order.getCurrency()));
            context.setVariable("savingsAmount", order.getDiscountAmount());
            context.setVariable("formattedSavingsAmount", formatCurrency(order.getDiscountAmount(), order.getCurrency()));

        } else {
            // Normal hesaplama
            context.setVariable("itemsSubtotal", itemsSubtotal);
            context.setVariable("formattedItemsSubtotal", formatCurrency(itemsSubtotal, order.getCurrency()));
            context.setVariable("discountedSubtotal", itemsSubtotal);
            context.setVariable("formattedDiscountedSubtotal", formatCurrency(itemsSubtotal, order.getCurrency()));

            BigDecimal netAmount = itemsSubtotal.divide(VAT_DIVISOR, 2, BigDecimal.ROUND_HALF_UP);
            BigDecimal kdv = itemsSubtotal.subtract(netAmount);

            context.setVariable("netAmount", netAmount);
            context.setVariable("formattedNetAmount", formatCurrency(netAmount, order.getCurrency()));
            context.setVariable("kdv", kdv);
            context.setVariable("formattedKdv", formatCurrency(kdv, order.getCurrency()));
            context.setVariable("savingsAmount", BigDecimal.ZERO);
            context.setVariable("formattedSavingsAmount", formatCurrency(BigDecimal.ZERO, order.getCurrency()));
        }

        context.setVariable("totalAmount", order.getTotalAmount());
        context.setVariable("formattedTotal", formatCurrency(order.getTotalAmount(), order.getCurrency()));
        context.setVariable("subtotal", calculateSubtotal(order));
        context.setVariable("formattedSubtotal", formatCurrency(calculateSubtotal(order), order.getCurrency()));
    }

    private BigDecimal calculateItemsSubtotal(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return null;
        }
        if (order.getItems().stream().anyMatch(item -> item.getTotalPrice() == null)) return null;
        return order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateSubtotal(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()
                || order.getItems().stream().anyMatch(item -> item.getTotalPrice() == null)) return null;
        BigDecimal calculated = order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return calculated.subtract(calculateKdv(order.getTotalAmount()));
    }

    private BigDecimal calculateKdv(BigDecimal amount) {
        if (amount == null) return BigDecimal.ZERO;
        return amount.multiply(VAT_RATE).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private String getDiscountTypeDisplayName(com.maxx_global.enums.DiscountType discountType, Locale locale) {
        if (discountType == null) return "";
        Locale targetLocale = locale != null ? locale : localizationService.getCurrentRequestLocale();
        return switch (discountType) {
            case PERCENTAGE -> localizationService.getMessage("pdf.invoice.discount.type.percentage", targetLocale);
            case FIXED_AMOUNT -> localizationService.getMessage("pdf.invoice.discount.type.fixedAmount", targetLocale);
        };
    }

    private void addLocalizedLabels(Context context, Order order, Locale locale) {
        Locale targetLocale = locale != null ? locale : localizationService.getCurrentRequestLocale();

        context.setVariable("htmlLang", targetLocale.getLanguage());
        context.setVariable("pageTitle", localizationService.getMessage("pdf.invoice.pageTitle", targetLocale, order.getOrderNumber()));
        context.setVariable("invoiceTitle", localizationService.getMessage("pdf.invoice.header.title", targetLocale));
        context.setVariable("orderNumberLabel", localizationService.getMessage("pdf.invoice.orderNumber", targetLocale));
        context.setVariable("dateLabel", localizationService.getMessage("pdf.invoice.date", targetLocale));

        context.setVariable("dealerInfoTitle", localizationService.getMessage("pdf.invoice.dealer.info", targetLocale));
        context.setVariable("dealerNameLabel", localizationService.getMessage("pdf.invoice.dealer.name", targetLocale));
        context.setVariable("contactPersonLabel", localizationService.getMessage("pdf.invoice.dealer.contact", targetLocale));
        context.setVariable("addressLabel", localizationService.getMessage("pdf.invoice.dealer.address", targetLocale));
        context.setVariable("phoneLabel", localizationService.getMessage("pdf.invoice.dealer.phone", targetLocale));
        context.setVariable("emailLabel", localizationService.getMessage("pdf.invoice.dealer.email", targetLocale));

        context.setVariable("productNameHeader", localizationService.getMessage("pdf.invoice.table.product", targetLocale));
        context.setVariable("quantityHeader", localizationService.getMessage("pdf.invoice.table.quantity", targetLocale));
        context.setVariable("unitPriceHeader", localizationService.getMessage("pdf.invoice.table.unitPrice", targetLocale));
        context.setVariable("totalHeader", localizationService.getMessage("pdf.invoice.table.total", targetLocale));
        context.setVariable("productCodeLabel", localizationService.getMessage("pdf.invoice.table.productCode", targetLocale));
        context.setVariable("variantSizeLabel", localizationService.getMessage("pdf.invoice.table.variantSize", targetLocale));
        context.setVariable("skuLabel", localizationService.getMessage("pdf.invoice.table.sku", targetLocale));
        context.setVariable("defaultUnitLabel", localizationService.getMessage("pdf.invoice.table.unit.default", targetLocale));

        context.setVariable("productsTotalLabel", localizationService.getMessage("pdf.invoice.total.products", targetLocale));
        context.setVariable("discountLabel", localizationService.getMessage("pdf.invoice.total.discount", targetLocale));
        context.setVariable("discountedSubtotalLabel", localizationService.getMessage("pdf.invoice.total.discountedSubtotal", targetLocale));
        context.setVariable("netAmountLabel", localizationService.getMessage("pdf.invoice.total.netAmount", targetLocale));
        context.setVariable("vatLabel", localizationService.getMessage("pdf.invoice.total.vat", targetLocale));
        context.setVariable("grandTotalLabel", localizationService.getMessage("pdf.invoice.total.grandTotal", targetLocale));
        context.setVariable("totalSavingsLabel", localizationService.getMessage("pdf.invoice.total.savings", targetLocale));

        context.setVariable("discountDetailsTitle", localizationService.getMessage("pdf.invoice.discount.details.title", targetLocale));
        context.setVariable("discountNameLabel", localizationService.getMessage("pdf.invoice.discount.name", targetLocale));
        context.setVariable("discountTypeLabel", localizationService.getMessage("pdf.invoice.discount.type", targetLocale));
        context.setVariable("discountValueLabel", localizationService.getMessage("pdf.invoice.discount.value", targetLocale));
        context.setVariable("discountSavingsPrefix", localizationService.getMessage("pdf.invoice.discount.savingsPrefix", targetLocale));

        context.setVariable("footerPageInfoLabel", localizationService.getMessage("pdf.invoice.footer.pageInfo", targetLocale));
    }

    public String generatePdfFileName(Order order) {
        return String.format("Siparis_%s_%s.pdf",
                order.getOrderNumber().replace("-", "_"),
                order.getOrderDate().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
    }
}

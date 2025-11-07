package com.maxx_global.service;

import com.maxx_global.dto.productPriceExcell.ExcelPriceData;
import com.maxx_global.dto.productPriceExcell.PriceImportError;
import com.maxx_global.dto.productPriceExcell.PriceImportResult;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Product;
import com.maxx_global.entity.ProductPrice;
import com.maxx_global.entity.ProductVariant;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.DealerRepository;
import com.maxx_global.repository.ProductPriceRepository;
import com.maxx_global.repository.ProductRepository;
import com.maxx_global.repository.ProductVariantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductPriceExcelService {

    private static final Logger logger = Logger.getLogger(ProductPriceExcelService.class.getName());

    // ✅ VARYANT BAZLI Excel sütun indeksleri
    private static final int COL_PRODUCT_ID = 0;        // ✅ YENİ: Product ID (locked)
    private static final int COL_VARIANT_ID = 1;        // ✅ YENİ: Variant ID (locked)
    private static final int COL_PRODUCT_CODE = 2;
    private static final int COL_PRODUCT_NAME = 3;
    private static final int COL_VARIANT_SIZE = 4;      // ✅ Varyant boyutu
    private static final int COL_SKU = 5;               // ✅ Varyant SKU
    private static final int COL_VARIANT_STOCK = 6;     // ✅ Varyant stoğu
    private static final int COL_PRICE_TRY = 7;
    private static final int COL_PRICE_USD = 8;
    private static final int COL_PRICE_EUR = 9;
    private static final int COL_VALID_FROM = 10;
    private static final int COL_VALID_UNTIL = 11;
    private static final int COL_IS_ACTIVE = 12;

    private static final short[] PRODUCT_ROW_COLORS = new short[]{
            IndexedColors.LEMON_CHIFFON.getIndex(),
            IndexedColors.LIGHT_TURQUOISE.getIndex(),
            IndexedColors.LIGHT_GREEN.getIndex(),
            IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex(),
            IndexedColors.LIGHT_ORANGE.getIndex(),
            IndexedColors.LIGHT_YELLOW.getIndex(),
            IndexedColors.PALE_BLUE.getIndex(),
            IndexedColors.ROSE.getIndex(),
            IndexedColors.LAVENDER.getIndex(),
            IndexedColors.CORNFLOWER_BLUE.getIndex()
    };

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;  // ✅ YENİ
    private final ProductPriceRepository productPriceRepository;
    private final DealerRepository dealerRepository;

    public ProductPriceExcelService(ProductRepository productRepository,
                                    ProductVariantRepository productVariantRepository,  // ✅ YENİ
                                    ProductPriceRepository productPriceRepository,
                                    DealerRepository dealerRepository) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;  // ✅ YENİ
        this.productPriceRepository = productPriceRepository;
        this.dealerRepository = dealerRepository;
    }

    /**
     * ✅ VARYANT BAZLI: Mevcut fiyatları dolu olarak template oluştur
     * Bayi için fiyat şablonu oluştur - Her varyant için ayrı satır, mevcut fiyatlar dolu gelir
     */
    public byte[] generatePriceTemplate(Long dealerId) throws IOException {
        logger.info("Generating VARIANT-BASED price template with existing prices for dealer: " + dealerId);

        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new EntityNotFoundException("Dealer not found with id: " + dealerId));

        List<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Ürün Fiyatları");

            // Tek seferlik stil oluştur (performans için)
            CellStyle baseLockedStyle = createLockedCellStyle(workbook);
            CellStyle baseUnlockedStyle = createUnlockedCellStyle(workbook);
            int colorCursor = 0;

            // Header ve talimatları oluştur
            createHeaderSection(workbook, sheet, dealer);

            // Sütun başlıklarını oluştur
            createColumnHeaders(workbook, sheet, 4); // 5. satırdan başla

            // ✅ VARYANT BAZLI: Her ürünün her varyantı için ayrı satır (aynı ürünün varyantları aynı renkte)
            int rowIndex = 5;
            int totalVariants = 0;

            for (Product product : products) {
                // Ürünün tüm aktif varyantlarını al
                List<ProductVariant> variants = productVariantRepository
                        .findByProductIdAndStatusOrderBySizeAsc(product.getId(), EntityStatus.ACTIVE);

                if (variants.isEmpty()) {
                    logger.warning("Product " + product.getCode() + " has no variants, skipping");
                    continue;
                }

                // Aynı ürünün tüm varyantları için aynı renk
                short color = PRODUCT_ROW_COLORS[colorCursor % PRODUCT_ROW_COLORS.length];
                CellStyle lockedStyle = cloneWithFill(workbook, baseLockedStyle, color);
                CellStyle unlockedStyle = cloneWithFill(workbook, baseUnlockedStyle, color);
                colorCursor++;

                for (ProductVariant variant : variants) {
                    // Bu varyant için bayi fiyatlarını al
                    Map<CurrencyType, ProductPrice> priceMap = getVariantPricesForDealer(
                            variant.getId(), dealerId, true); // Sadece aktif fiyatlar

                    createVariantRow(sheet, rowIndex++, product, variant,
                            priceMap.get(CurrencyType.TRY),
                            priceMap.get(CurrencyType.USD),
                            priceMap.get(CurrencyType.EUR),
                            lockedStyle,
                            unlockedStyle);

                    totalVariants++;
                }
            }

            // Koruma: sadece locked hücreler korunsun, unlocked olanlar düzenlenebilir
            sheet.protectSheet("");

            // Sütun genişliklerini ayarla
            autoSizeColumns(sheet);

            // Excel dosyasını byte array'e çevir
            workbook.write(outputStream);

            logger.info("Price template generated successfully with " + products.size() +
                    " products, " + totalVariants + " variants (existing prices included)");
            return outputStream.toByteArray();
        }
    }


    /**
     * ✅ VARYANT BAZLI: Bayi fiyatlarını Excel'e aktar (her varyant için ayrı satır)
     */
    public byte[] exportDealerPrices(Long dealerId, boolean activeOnly) throws IOException {
        logger.info("Exporting VARIANT-BASED dealer prices - dealerId: " + dealerId + ", activeOnly: " + activeOnly);

        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new EntityNotFoundException("Dealer not found with id: " + dealerId));

        List<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Ürün Fiyatları");

            CellStyle baseLockedStyle = createLockedCellStyle(workbook);
            CellStyle baseUnlockedStyle = createUnlockedCellStyle(workbook);
            int colorCursor = 0;

            // Header ve talimatları oluştur
            createHeaderSection(workbook, sheet, dealer);

            // Sütun başlıklarını oluştur
            createColumnHeaders(workbook, sheet, 4);

            // ✅ VARYANT BAZLI: Her ürünün her varyantı için ayrı satır (aynı ürünün varyantları aynı renkte)
            int rowIndex = 5;
            int totalVariants = 0;

            for (Product product : products) {
                // Ürünün tüm aktif varyantlarını al
                List<ProductVariant> variants = productVariantRepository
                        .findByProductIdAndStatusOrderBySizeAsc(product.getId(), EntityStatus.ACTIVE);

                if (variants.isEmpty()) {
                    logger.warning("Product " + product.getCode() + " has no variants, skipping");
                    continue;
                }

                // Aynı ürünün tüm varyantları için aynı renk
                short color = PRODUCT_ROW_COLORS[colorCursor % PRODUCT_ROW_COLORS.length];
                CellStyle lockedStyle = cloneWithFill(workbook, baseLockedStyle, color);
                CellStyle unlockedStyle = cloneWithFill(workbook, baseUnlockedStyle, color);
                colorCursor++;

                for (ProductVariant variant : variants) {
                    // Bu varyant için bayi fiyatlarını al
                    Map<CurrencyType, ProductPrice> priceMap = getVariantPricesForDealer(
                            variant.getId(), dealerId, activeOnly);

                    createVariantRow(sheet, rowIndex++, product, variant,
                            priceMap.get(CurrencyType.TRY),
                            priceMap.get(CurrencyType.USD),
                            priceMap.get(CurrencyType.EUR),
                            lockedStyle,
                            unlockedStyle);

                    totalVariants++;
                }
            }

            // Sheet koruması
            sheet.protectSheet("");

            // Sütun genişliklerini ayarla
            autoSizeColumns(sheet);

            workbook.write(outputStream);

            logger.info("Dealer prices exported successfully - " + products.size() +
                    " products, " + totalVariants + " variants");
            return outputStream.toByteArray();
        }
    }


    /**
     * ÖZELLİK 2: Esnek currency import - sadece girilen para birimlerini işle
     * Excel'den fiyat verilerini import et
     */
    @Transactional
    public PriceImportResult importPricesFromExcel(Long dealerId, MultipartFile file,
                                                   boolean updateExisting, boolean skipErrors) throws IOException {
        logger.info("Importing prices from Excel with flexible currency support - dealerId: " + dealerId +
                ", updateExisting: " + updateExisting + ", skipErrors: " + skipErrors);

        // Dealer kontrolü
        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new EntityNotFoundException("Dealer not found with id: " + dealerId));

        List<PriceImportError> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;
        int updatedCount = 0;
        int createdCount = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // Veri satırlarını bul (header'ları atla)
            int startRow = findDataStartRow(sheet);
            if (startRow == -1) {
                throw new IllegalArgumentException("Excel dosyasında veri satırları bulunamadı");
            }

            // Product code -> Product mapping oluştur (performans için)
            Map<String, Product> productMap = createProductCodeMap();
            Map<String, ProductVariant> variantCache = new HashMap<>();

            // Her satırı işle
            for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                totalRows++;

                try {
                    // ÖZELLİK 2: Excel satırından sadece dolu fiyat verilerini oku
                    List<ExcelPriceData> priceDataList = parseRowToPriceDataFlexible(row, rowIndex + 1);

                    if (priceDataList.isEmpty()) {
                        continue; // Fiyat verisi yok, atla (hata değil)
                    }

                    // Her currency için fiyat işle
                    for (ExcelPriceData priceData : priceDataList) {
                        if (!priceData.isValid()) {
                            errors.add(new PriceImportError(
                                    priceData.getRowNumber(),
                                    priceData.getProductCode(),
                                    priceData.getValidationError(),
                                    getRowDataAsString(row)
                            ));
                            if (!skipErrors) {
                                throw new IllegalArgumentException("Satır " + (rowIndex + 1) + ": " +
                                        priceData.getValidationError());
                            }
                            continue;
                        }

                        // Product'ı bul
                        Product product = productMap.get(priceData.getProductCode().toUpperCase());
                        if (product == null) {
                            String error = "Ürün bulunamadı: " + priceData.getProductCode();
                            errors.add(new PriceImportError(
                                    priceData.getRowNumber(),
                                    priceData.getProductCode(),
                                    error,
                                    getRowDataAsString(row)
                            ));
                            if (!skipErrors) {
                                throw new IllegalArgumentException("Satır " + (rowIndex + 1) + ": " + error);
                            }
                            continue;
                        }

                        // Varyantı bul
                        String sku = priceData.getVariantSku() != null ? priceData.getVariantSku().trim() : "";
                        if (sku.isEmpty()) {
                            String error = "SKU bulunamadı veya boş";
                            errors.add(new PriceImportError(
                                    priceData.getRowNumber(),
                                    priceData.getProductCode(),
                                    error,
                                    getRowDataAsString(row)
                            ));
                            if (!skipErrors) {
                                throw new IllegalArgumentException("Satır " + (rowIndex + 1) + ": " + error);
                            }
                            continue;
                        }

                        ProductVariant variant = variantCache.get(sku.toUpperCase());
                        if (variant == null) {
                            variant = productVariantRepository.findBySkuAndStatus(sku, EntityStatus.ACTIVE)
                                    .orElse(null);
                            if (variant != null) {
                                variantCache.put(sku.toUpperCase(), variant);
                            }
                        }

                        if (variant == null) {
                            String error = "Varyant bulunamadı (SKU: " + sku + ")";
                            errors.add(new PriceImportError(
                                    priceData.getRowNumber(),
                                    priceData.getProductCode(),
                                    error,
                                    getRowDataAsString(row)
                            ));
                            if (!skipErrors) {
                                throw new IllegalArgumentException("Satır " + (rowIndex + 1) + ": " + error);
                            }
                            continue;
                        }

                        if (!variant.getProduct().getId().equals(product.getId())) {
                            String error = "SKU " + sku + " belirtilen ürünle eşleşmiyor";
                            errors.add(new PriceImportError(
                                    priceData.getRowNumber(),
                                    priceData.getProductCode(),
                                    error,
                                    getRowDataAsString(row)
                            ));
                            if (!skipErrors) {
                                throw new IllegalArgumentException("Satır " + (rowIndex + 1) + ": " + error);
                            }
                            continue;
                        }

                        // Fiyatı kaydet veya güncelle
                        boolean isUpdate = saveOrUpdatePrice(variant, dealer, priceData, updateExisting);
                        if (isUpdate) {
                            updatedCount++;
                        } else {
                            createdCount++;
                        }
                    }

                    successCount++;

                } catch (Exception e) {
                    String error = "Satır işleme hatası: " + e.getMessage();
                    errors.add(new PriceImportError(
                            rowIndex + 1,
                            getCellValueAsString(row.getCell(COL_PRODUCT_CODE)),
                            error,
                            getRowDataAsString(row)
                    ));

                    if (!skipErrors) {
                        throw new IllegalArgumentException("Satır " + (rowIndex + 1) + ": " + error);
                    }

                    logger.warning("Row " + (rowIndex + 1) + " failed: " + error);
                }
            }

            boolean success = errors.isEmpty() || (skipErrors && successCount > 0);
            String message = String.format("Import tamamlandı. Toplam: %d, Başarılı: %d, Hatalı: %d, " +
                            "Güncellenen: %d, Yeni: %d",
                    totalRows, successCount, errors.size(), updatedCount, createdCount);

            logger.info(message);

            return new PriceImportResult(
                    totalRows,
                    successCount,
                    errors.size(),
                    updatedCount,
                    createdCount,
                    errors,
                    success,
                    message
            );

        } catch (Exception e) {
            logger.severe("Excel import failed: " + e.getMessage());
            throw new RuntimeException("Excel import hatası: " + e.getMessage(), e);
        }
    }

    /**
     * Genel import şablonu oluştur
     */
    public byte[] generateImportTemplate() throws IOException {
        logger.info("Generating general import template");

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Fiyat Import Şablonu");

            // Genel talimatlar
            createGeneralInstructions(workbook, sheet);

            // Sütun başlıkları
            createColumnHeaders(workbook, sheet, 8);

            // Örnek veriler
            createSampleData(sheet, 9);

            autoSizeColumns(sheet);
            workbook.write(outputStream);

            return outputStream.toByteArray();
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private void createHeaderSection(Workbook workbook, Sheet sheet, Dealer dealer) {
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle infoStyle = createInfoStyle(workbook);

        // Başlık
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ÜRÜN FİYAT ŞABLONU");
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

        // Bayi bilgisi
        Row dealerRow = sheet.createRow(1);
        Cell dealerCell = dealerRow.createCell(0);
        dealerCell.setCellValue("Bayi: " + dealer.getName() + " (ID: " + dealer.getId() + ")");
        dealerCell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 8));

        // Tarih
        Row dateRow = sheet.createRow(2);
        Cell dateCell = dateRow.createCell(0);
        dateCell.setCellValue("Oluşturma Tarihi: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        dateCell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 8));

        // Güncellenmiş talimatlar
        Row instructionRow = sheet.createRow(3);
        Cell instructionCell = instructionRow.createCell(0);
        instructionCell.setCellValue("Talimatlar: İstediğiniz para birimlerinde fiyat girin (TRY, USD, EUR). " +
                "Mevcut fiyatlar dolu geliyor. Tüm kurları doldurmak zorunda değilsiniz! " +
                "Tarih formatı: dd.MM.yyyy HH:mm (opsiyonel). Aktif: EVET/HAYIR");
        instructionCell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 8));
    }

    private void createColumnHeaders(Workbook workbook, Sheet sheet, int rowIndex) {
        CellStyle headerStyle = createColumnHeaderStyle(workbook);

        Row headerRow = sheet.createRow(rowIndex);

        // ✅ VARYANT BAZLI sütun başlıkları (ID'ler eklendi)
        String[] headers = {
                "Product ID", "Variant ID", "Ürün Kodu", "Ürün Adı", "Varyant Boyutu", "SKU Kodu", "Varyant Stoğu",
                "Fiyat (TRY)", "Fiyat (USD)", "Fiyat (EUR)",
                "Geçerli Başlangıç", "Geçerli Bitiş", "Aktif"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private CellStyle createLockedCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorders(style);
        style.setLocked(true);
        return style;
    }

    private CellStyle createUnlockedCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorders(style);
        style.setLocked(false);
        return style;
    }

    private void applyThinBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private CellStyle cloneWithFill(Workbook workbook, CellStyle baseStyle, short colorIndex) {
        CellStyle cloned = workbook.createCellStyle();
        cloned.cloneStyleFrom(baseStyle);
        cloned.setFillForegroundColor(colorIndex);
        cloned.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return cloned;
    }

    /**
     * ✅ VARYANT BAZLI satır oluşturma
     */
    private void createVariantRow(Sheet sheet, int rowIndex, Product product, ProductVariant variant,
                                  ProductPrice tryPrice, ProductPrice usdPrice, ProductPrice eurPrice,
                                  CellStyle lockedStyle, CellStyle unlockedStyle) {
        Row row = sheet.createRow(rowIndex);

        // ✅ ID bilgileri (kilitli) - GÜVENLİK İÇİN
        Cell productIdCell = row.createCell(COL_PRODUCT_ID);
        productIdCell.setCellValue(product.getId());
        productIdCell.setCellStyle(lockedStyle);

        Cell variantIdCell = row.createCell(COL_VARIANT_ID);
        variantIdCell.setCellValue(variant.getId());
        variantIdCell.setCellStyle(lockedStyle);

        // Ürün bilgileri (kilitli)
        Cell codeCell = row.createCell(COL_PRODUCT_CODE);
        codeCell.setCellValue(product.getCode());
        codeCell.setCellStyle(lockedStyle);

        Cell nameCell = row.createCell(COL_PRODUCT_NAME);
        nameCell.setCellValue(product.getName());
        nameCell.setCellStyle(lockedStyle);

        // ✅ VARYANT bilgileri (kilitli)
        Cell variantSizeCell = row.createCell(COL_VARIANT_SIZE);
        variantSizeCell.setCellValue(variant.getSize() != null ? variant.getSize() : "");
        variantSizeCell.setCellStyle(lockedStyle);

        Cell skuCell = row.createCell(COL_SKU);
        skuCell.setCellValue(variant.getSku() != null ? variant.getSku() : "");
        skuCell.setCellStyle(lockedStyle);

        Cell variantStockCell = row.createCell(COL_VARIANT_STOCK);
        variantStockCell.setCellValue(variant.getStockQuantity() != null ? variant.getStockQuantity() : 0);
        variantStockCell.setCellStyle(lockedStyle);

        // TRY fiyat (yazılabilir)
        Cell tryCell = row.createCell(COL_PRICE_TRY);
        if (tryPrice != null && tryPrice.getAmount() != null) {
            tryCell.setCellValue(tryPrice.getAmount().doubleValue());
        }
        tryCell.setCellStyle(unlockedStyle);

        // USD fiyat (yazılabilir)
        Cell usdCell = row.createCell(COL_PRICE_USD);
        if (usdPrice != null && usdPrice.getAmount() != null) {
            usdCell.setCellValue(usdPrice.getAmount().doubleValue());
        }
        usdCell.setCellStyle(unlockedStyle);

        // EUR fiyat (yazılabilir)
        Cell eurCell = row.createCell(COL_PRICE_EUR);
        if (eurPrice != null && eurPrice.getAmount() != null) {
            eurCell.setCellValue(eurPrice.getAmount().doubleValue());
        }
        eurCell.setCellStyle(unlockedStyle);

        // Tarih ve aktif kolonları (yazılabilir - fiyat bilgisi olduğu için)
        Cell fromCell = row.createCell(COL_VALID_FROM);
        Cell untilCell = row.createCell(COL_VALID_UNTIL);
        Cell activeCell = row.createCell(COL_IS_ACTIVE);

        fromCell.setCellStyle(unlockedStyle);
        untilCell.setCellStyle(unlockedStyle);
        activeCell.setCellStyle(unlockedStyle);

        ProductPrice activePrice = tryPrice != null ? tryPrice : (usdPrice != null ? usdPrice : eurPrice);
        if (activePrice != null) {
            if (activePrice.getValidFrom() != null) {
                fromCell.setCellValue(activePrice.getValidFrom().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            }
            if (activePrice.getValidUntil() != null) {
                untilCell.setCellValue(activePrice.getValidUntil().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            }
            activeCell.setCellValue(activePrice.getIsActive() ? "EVET" : "HAYIR");
        } else {
            activeCell.setCellValue("EVET");
        }
    }



    private void setCurrencyPriceCell(Row row, int columnIndex, ProductPrice price) {
        Cell cell = row.createCell(columnIndex);
        if (price != null && price.getAmount() != null) {
            cell.setCellValue(price.getAmount().doubleValue());
        }
        // ÖZELLİK 1: Fiyat yoksa boş bırak (null değil, boş hücre)
    }

    /**
     * ✅ VARYANT BAZLI: Varyant için bayi fiyatlarını getir
     */
    private Map<CurrencyType, ProductPrice> getVariantPricesForDealer(Long variantId, Long dealerId, boolean activeOnly) {
        List<ProductPrice> prices = productPriceRepository.findByProductVariantIdAndDealerIdAndStatus(
                variantId, dealerId, EntityStatus.ACTIVE);

        if (activeOnly) {
            prices = prices.stream()
                    .filter(ProductPrice::isValidNow)
                    .collect(Collectors.toList());
        }

        return prices.stream()
                .collect(Collectors.toMap(
                        ProductPrice::getCurrency,
                        price -> price,
                        (existing, replacement) -> existing // İlkini tut
                ));
    }



    private int findDataStartRow(Sheet sheet) {
        // "Product ID" header'ını ara (yeni format)
        for (int i = 0; i <= 10; i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell firstCell = row.getCell(0);
                String cellValue = getCellValueAsString(firstCell);
                if (firstCell != null && ("Product ID".equals(cellValue) || "Ürün Kodu".equals(cellValue))) {
                    return i + 1; // Data bir sonraki satırdan başlar
                }
            }
        }
        return -1; // Bulunamadı
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) return true;

        for (int i = 0; i <= COL_IS_ACTIVE; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && !getCellValueAsString(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * ÖZELLİK 2: Esnek fiyat parse - sadece dolu olanları al
     */
    private List<ExcelPriceData> parseRowToPriceDataFlexible(Row row, int rowNumber) {
        List<ExcelPriceData> priceDataList = new ArrayList<>();

        // ✅ ID'leri oku (GÜVENLİK İÇİN - çoklanmayı engeller)
        String productIdStr = getCellValueAsString(row.getCell(COL_PRODUCT_ID));
        String variantIdStr = getCellValueAsString(row.getCell(COL_VARIANT_ID));

        Long productId = null;
        Long variantId = null;

        try {
            if (!productIdStr.trim().isEmpty()) {
                productId = Long.parseLong(productIdStr.trim());
            }
            if (!variantIdStr.trim().isEmpty()) {
                variantId = Long.parseLong(variantIdStr.trim());
            }
        } catch (NumberFormatException e) {
            logger.warning("Invalid ID format in row " + rowNumber + ": " + e.getMessage());
        }

        String productCode = getCellValueAsString(row.getCell(COL_PRODUCT_CODE));
        if (productCode.trim().isEmpty()) {
            return priceDataList; // Ürün kodu yoksa atla
        }

        String variantSku = getCellValueAsString(row.getCell(COL_SKU));
        String variantSize = getCellValueAsString(row.getCell(COL_VARIANT_SIZE));

        // ÖZELLİK 2: Her currency için kontrol et - Sadece dolu olanları işle
        BigDecimal tryAmount = getCellValueAsBigDecimal(row.getCell(COL_PRICE_TRY));
        BigDecimal usdAmount = getCellValueAsBigDecimal(row.getCell(COL_PRICE_USD));
        BigDecimal eurAmount = getCellValueAsBigDecimal(row.getCell(COL_PRICE_EUR));

        // En az bir fiyat girilmiş mi kontrol et
        if ((tryAmount == null || tryAmount.compareTo(BigDecimal.ZERO) <= 0) &&
                (usdAmount == null || usdAmount.compareTo(BigDecimal.ZERO) <= 0) &&
                (eurAmount == null || eurAmount.compareTo(BigDecimal.ZERO) <= 0)) {
            return priceDataList; // Hiç fiyat yok, bu satırı atla (hata değil)
        }

        // Tarih bilgileri
        LocalDateTime validFrom = getCellValueAsDateTime(row.getCell(COL_VALID_FROM));
        LocalDateTime validUntil = getCellValueAsDateTime(row.getCell(COL_VALID_UNTIL));
        Boolean isActive = getCellValueAsBoolean(row.getCell(COL_IS_ACTIVE));

        // TRY fiyat - Sadece geçerli fiyat varsa ekle
        if (tryAmount != null && tryAmount.compareTo(BigDecimal.ZERO) > 0) {
            ExcelPriceData priceData = new ExcelPriceData(rowNumber);
            priceData.setProductId(productId); // ✅ ID ekle
            priceData.setVariantId(variantId); // ✅ ID ekle
            priceData.setProductCode(productCode);
            priceData.setVariantSku(variantSku);
            priceData.setVariantSize(variantSize);
            priceData.setCurrency(CurrencyType.TRY);
            priceData.setAmount(tryAmount);
            priceData.setValidFrom(validFrom);
            priceData.setValidUntil(validUntil);
            priceData.setIsActive(isActive != null ? isActive : true);
            priceDataList.add(priceData);
        }

        // USD fiyat - Sadece geçerli fiyat varsa ekle
        if (usdAmount != null && usdAmount.compareTo(BigDecimal.ZERO) > 0) {
            ExcelPriceData priceData = new ExcelPriceData(rowNumber);
            priceData.setProductId(productId); // ✅ ID ekle
            priceData.setVariantId(variantId); // ✅ ID ekle
            priceData.setProductCode(productCode);
            priceData.setVariantSku(variantSku);
            priceData.setVariantSize(variantSize);
            priceData.setCurrency(CurrencyType.USD);
            priceData.setAmount(usdAmount);
            priceData.setValidFrom(validFrom);
            priceData.setValidUntil(validUntil);
            priceData.setIsActive(isActive != null ? isActive : true);
            priceDataList.add(priceData);
        }

        // EUR fiyat - Sadece geçerli fiyat varsa ekle
        if (eurAmount != null && eurAmount.compareTo(BigDecimal.ZERO) > 0) {
            ExcelPriceData priceData = new ExcelPriceData(rowNumber);
            priceData.setProductId(productId); // ✅ ID ekle
            priceData.setVariantId(variantId); // ✅ ID ekle
            priceData.setProductCode(productCode);
            priceData.setVariantSku(variantSku);
            priceData.setVariantSize(variantSize);
            priceData.setCurrency(CurrencyType.EUR);
            priceData.setAmount(eurAmount);
            priceData.setValidFrom(validFrom);
            priceData.setValidUntil(validUntil);
            priceData.setIsActive(isActive != null ? isActive : true);
            priceDataList.add(priceData);
        }

        return priceDataList;
    }

    private Map<String, Product> createProductCodeMap() {
        List<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);
        return products.stream()
                .collect(Collectors.toMap(
                        product -> product.getCode().toUpperCase(), // Case-insensitive
                        product -> product,
                        (existing, replacement) -> existing
                ));
    }

    private boolean saveOrUpdatePrice(ProductVariant variant, Dealer dealer, ExcelPriceData priceData, boolean updateExisting) {
        // ✅ GÜVENLİK: ID bazlı validasyon - çoklanmayı engeller
        if (priceData.getVariantId() != null && !priceData.getVariantId().equals(variant.getId())) {
            logger.warning("Variant ID mismatch! Excel variantId: " + priceData.getVariantId() +
                ", Found variantId: " + variant.getId() + " for SKU: " + variant.getSku());
            throw new IllegalArgumentException("Varyant ID uyuşmuyor! Excel'deki ID'ler değiştirilmemiş olmalı.");
        }

        // ✅ ÇOKLANMAYI ENGELLER: ACTIVE status ile birlikte ara
        List<ProductPrice> existingPrices = productPriceRepository.findByProductVariantIdAndDealerIdAndStatus(
                variant.getId(), dealer.getId(), EntityStatus.ACTIVE);

        // ✅ Bu currency için aktif fiyat var mı?
        Optional<ProductPrice> existingPriceOpt = existingPrices.stream()
                .filter(p -> p.getCurrency() == priceData.getCurrency())
                .findFirst();

        if (existingPriceOpt.isPresent()) {
            // Güncelle
            ProductPrice price = existingPriceOpt.get();
            price.setAmount(priceData.getAmount());
            price.setValidFrom(priceData.getValidFrom());
            price.setValidUntil(priceData.getValidUntil());
            price.setIsActive(priceData.getIsActive());
            productPriceRepository.save(price);

            logger.info("✅ Updated price for variant ID: " + variant.getId() +
                ", SKU: " + variant.getSku() + ", currency: " + priceData.getCurrency());
            return true;

        } else {
            // Yeni oluştur
            ProductPrice price = new ProductPrice();
            price.setProduct(variant.getProduct());
            price.setProductVariant(variant);
            price.setDealer(dealer);
            price.setCurrency(priceData.getCurrency());
            price.setAmount(priceData.getAmount());
            price.setValidFrom(priceData.getValidFrom());
            price.setValidUntil(priceData.getValidUntil());
            price.setIsActive(priceData.getIsActive());
            price.setStatus(EntityStatus.ACTIVE);
            productPriceRepository.save(price);

            logger.info("✅ Created new price for variant ID: " + variant.getId() +
                ", SKU: " + variant.getSku() + ", currency: " + priceData.getCurrency());
            return false;
        }
    }

    private void createGeneralInstructions(Workbook workbook, Sheet sheet) {
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle infoStyle = createInfoStyle(workbook);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("FİYAT IMPORT ŞABLONU");
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

        Row[] instructionRows = {
                sheet.createRow(1),
                sheet.createRow(2),
                sheet.createRow(3),
                sheet.createRow(4),
                sheet.createRow(5),
                sheet.createRow(6),
                sheet.createRow(7)
        };

        String[] instructions = {
                "1. Ürün Kodu: Değiştirmeyin, sistemdeki mevcut ürün kodlarını kullanın",
                "2. Fiyat Sütunları: İstediğiniz para birimlerinde fiyat girin (TRY, USD, EUR)",
                "3. Tüm kurları doldurmak zorunda değilsiniz! Sadece TRY de yeterli",
                "4. Tarih Formatı: dd.MM.yyyy HH:mm (örnek: 01.01.2025 10:30)",
                "5. Aktif Alanı: EVET veya HAYIR yazın",
                "6. Boş satırlar otomatik olarak atlanır",
                "7. Hatalı veriler varsa hata raporunu kontrol edin"
        };

        for (int i = 0; i < instructions.length; i++) {
            Cell cell = instructionRows[i].createCell(0);
            cell.setCellValue(instructions[i]);
            cell.setCellStyle(infoStyle);
            sheet.addMergedRegion(new CellRangeAddress(i + 1, i + 1, 0, 8));
        }
    }

    private void createSampleData(Sheet sheet, int startRow) {
        // ✅ VARYANT BAZLI örnek veri satırları
        String[][] sampleData = {
                // TI-001 ürününün 3 farklı varyantı
                {"TI-001", "Titanyum İmplant", "4.0mm", "TI-001-40", "50", "150.00", "15.50", "14.20", "01.01.2025 00:00", "31.12.2025 23:59", "EVET"},
                {"TI-001", "Titanyum İmplant", "4.5mm", "TI-001-45", "30", "165.00", "17.00", "15.50", "", "", "EVET"},
                {"TI-001", "Titanyum İmplant", "5.0mm", "TI-001-50", "25", "180.00", "18.50", "17.00", "", "", "EVET"},

                // PL-002 ürününün 2 farklı varyantı - Sadece TRY fiyatı
                {"PL-002", "Titanyum Plak", "6 Delik", "PL-002-6", "25", "280.00", "", "", "", "", "EVET"},
                {"PL-002", "Titanyum Plak", "8 Delik", "PL-002-8", "15", "350.00", "", "", "", "", "EVET"},

                // SC-003 ürününün tek varyantı - Sadece USD ve EUR
                {"SC-003", "Titanyum Vida", "3.5mm", "SC-003-35", "100", "", "5.50", "5.20", "", "", "EVET"}
        };

        for (int i = 0; i < sampleData.length; i++) {
            Row row = sheet.createRow(startRow + i);
            for (int j = 0; j < sampleData[i].length; j++) {
                row.createCell(j).setCellValue(sampleData[i][j]);
            }
        }
    }

    // ==================== UTILITY METHODS ====================

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                } else {
                    // Sayısal değeri string'e çevir
                    double numValue = cell.getNumericCellValue();
                    if (numValue == Math.floor(numValue)) {
                        yield String.valueOf((long) numValue);
                    } else {
                        yield String.valueOf(numValue);
                    }
                }
            }
            case BOOLEAN -> cell.getBooleanCellValue() ? "EVET" : "HAYIR";
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue().trim();
                }
            }
            default -> "";
        };
    }

    private BigDecimal getCellValueAsBigDecimal(Cell cell) {
        if (cell == null) return null;

        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING -> {
                    String value = cell.getStringCellValue().trim();
                    if (value.isEmpty()) yield null;

                    // ✅ STRING validasyonu: Sadece rakam, nokta ve virgül olmalı
                    if (!value.matches("^[0-9.,]+$")) {
                        throw new NumberFormatException("Fiyat alanına geçersiz karakter girildi: '" + value + "'. Sadece sayı girebilirsiniz!");
                    }

                    // Türkçe format desteği (virgül -> nokta)
                    value = value.replace(",", ".");
                    yield new BigDecimal(value);
                }
                case FORMULA -> BigDecimal.valueOf(cell.getNumericCellValue());
                default -> null;
            };
        } catch (NumberFormatException e) {
            logger.severe("Invalid price format: " + e.getMessage());
            throw e; // Hatayı yukarı fırlat ki kullanıcı görsün
        } catch (Exception e) {
            logger.warning("Could not parse cell value as BigDecimal: " + e.getMessage());
            return null;
        }
    }

    private LocalDateTime getCellValueAsDateTime(Cell cell) {
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                String dateStr = cell.getStringCellValue().trim();
                if (dateStr.isEmpty()) return null;

                // Farklı formatları dene
                DateTimeFormatter[] formatters = {
                        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
                        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")
                };

                for (DateTimeFormatter formatter : formatters) {
                    try {
                        if (formatter.toString().contains("HH:mm")) {
                            return LocalDateTime.parse(dateStr, formatter);
                        } else {
                            return LocalDateTime.parse(dateStr + " 00:00",
                                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                        }
                    } catch (DateTimeParseException ignored) {
                        // Sonraki formatı dene
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Could not parse cell value as DateTime: " + e.getMessage());
        }

        return null;
    }

    private Boolean getCellValueAsBoolean(Cell cell) {
        if (cell == null) return null;

        String value = getCellValueAsString(cell).toUpperCase();
        return switch (value) {
            case "EVET", "TRUE", "1", "AKTIF", "YES" -> true;
            case "HAYIR", "FALSE", "0", "PASIF", "NO" -> false;
            default -> null;
        };
    }

    private String getRowDataAsString(Row row) {
        if (row == null) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= COL_IS_ACTIVE; i++) {
            Cell cell = row.getCell(i);
            if (i > 0) sb.append(" | ");
            sb.append(getCellValueAsString(cell));
        }
        return sb.toString();
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i <= COL_IS_ACTIVE; i++) {
            sheet.autoSizeColumn(i);
            // Maksimum genişlik sınırı
            if (sheet.getColumnWidth(i) > 8000) {
                sheet.setColumnWidth(i, 8000);
            }
        }
    }

    // ==================== STYLE METHODS ====================

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createInfoStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private CellStyle createColumnHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
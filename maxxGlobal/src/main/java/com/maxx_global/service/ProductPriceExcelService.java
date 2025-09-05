package com.maxx_global.service;

import com.maxx_global.dto.productPriceExcell.ExcelPriceData;
import com.maxx_global.dto.productPriceExcell.PriceImportError;
import com.maxx_global.dto.productPriceExcell.PriceImportResult;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Product;
import com.maxx_global.entity.ProductPrice;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.DealerRepository;
import com.maxx_global.repository.ProductPriceRepository;
import com.maxx_global.repository.ProductRepository;
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

    // Excel sütun indeksleri
    private static final int COL_PRODUCT_CODE = 0;
    private static final int COL_PRODUCT_NAME = 1;
    private static final int COL_CURRENT_STOCK = 2;
    private static final int COL_PRICE_TRY = 3;
    private static final int COL_PRICE_USD = 4;
    private static final int COL_PRICE_EUR = 5;
    private static final int COL_VALID_FROM = 6;
    private static final int COL_VALID_UNTIL = 7;
    private static final int COL_IS_ACTIVE = 8;

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final DealerRepository dealerRepository;

    public ProductPriceExcelService(ProductRepository productRepository,
                                    ProductPriceRepository productPriceRepository,
                                    DealerRepository dealerRepository) {
        this.productRepository = productRepository;
        this.productPriceRepository = productPriceRepository;
        this.dealerRepository = dealerRepository;
    }

    /**
     * ÖZELLİK 1: Mevcut fiyatları dolu olarak template oluştur
     * Bayi için fiyat şablonu oluştur - mevcut fiyatlar dolu gelir
     */
    public byte[] generatePriceTemplate(Long dealerId) throws IOException {
        logger.info("Generating price template with existing prices for dealer: " + dealerId);

        // Dealer kontrolü
        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new EntityNotFoundException("Dealer not found with id: " + dealerId));

        // Aktif ürünleri getir
        List<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Ürün Fiyatları");

            // Header ve talimatları oluştur
            createHeaderSection(workbook, sheet, dealer);

            // Sütun başlıklarını oluştur
            createColumnHeaders(workbook, sheet, 4); // 5. satırdan başla

            // ÖZELLİK 1: Ürün verilerini mevcut fiyatları ile birlikte ekle
            int rowIndex = 5; // 6. satırdan ürünler başlasın
            for (Product product : products) {
                // Bu ürün için dealer'ın mevcut fiyatlarını getir
                Map<CurrencyType, ProductPrice> priceMap = getProductPricesForDealer(
                        product.getId(), dealerId, true); // Sadece aktif fiyatlar

                createProductRow(sheet, rowIndex++, product,
                        priceMap.get(CurrencyType.TRY),
                        priceMap.get(CurrencyType.USD),
                        priceMap.get(CurrencyType.EUR));
            }

            // Sütun genişliklerini ayarla
            autoSizeColumns(sheet);

            // Excel dosyasını byte array'e çevir
            workbook.write(outputStream);

            logger.info("Price template generated successfully with " + products.size() +
                    " products (existing prices included)");
            return outputStream.toByteArray();
        }
    }

    /**
     * Bayi fiyatlarını Excel'e aktar (mevcut fiyatlar ile)
     */
    public byte[] exportDealerPrices(Long dealerId, boolean activeOnly) throws IOException {
        logger.info("Exporting dealer prices - dealerId: " + dealerId + ", activeOnly: " + activeOnly);

        // Dealer kontrolü
        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new EntityNotFoundException("Dealer not found with id: " + dealerId));

        // Aktif ürünleri getir
        List<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Ürün Fiyatları");

            // Header ve talimatları oluştur
            createHeaderSection(workbook, sheet, dealer);

            // Sütun başlıklarını oluştur
            createColumnHeaders(workbook, sheet, 4);

            // Ürün verilerini ve mevcut fiyatları ekle
            int rowIndex = 5;
            for (Product product : products) {
                // Bu ürün için dealer fiyatlarını getir
                Map<CurrencyType, ProductPrice> priceMap = getProductPricesForDealer(
                        product.getId(), dealerId, activeOnly);

                createProductRow(sheet, rowIndex++, product,
                        priceMap.get(CurrencyType.TRY),
                        priceMap.get(CurrencyType.USD),
                        priceMap.get(CurrencyType.EUR));
            }

            // Sütun genişliklerini ayarla
            autoSizeColumns(sheet);

            workbook.write(outputStream);

            logger.info("Dealer prices exported successfully");
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

                        // Fiyatı kaydet veya güncelle
                        boolean isUpdate = saveOrUpdatePrice(product, dealer, priceData, updateExisting);
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

        String[] headers = {
                "Ürün Kodu", "Ürün Adı", "Mevcut Stok",
                "Fiyat (TRY)", "Fiyat (USD)", "Fiyat (EUR)",
                "Geçerli Başlangıç", "Geçerli Bitiş", "Aktif"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void createProductRow(Sheet sheet, int rowIndex, Product product,
                                  ProductPrice tryPrice, ProductPrice usdPrice, ProductPrice eurPrice) {
        Row row = sheet.createRow(rowIndex);

        // Ürün bilgileri (değiştirilemez)
        row.createCell(COL_PRODUCT_CODE).setCellValue(product.getCode());
        row.createCell(COL_PRODUCT_NAME).setCellValue(product.getName());
        row.createCell(COL_CURRENT_STOCK).setCellValue(product.getStockQuantity() != null ?
                product.getStockQuantity() : 0);

        // ÖZELLİK 1: Mevcut fiyat bilgilerini doldur
        setCurrencyPriceCell(row, COL_PRICE_TRY, tryPrice);
        setCurrencyPriceCell(row, COL_PRICE_USD, usdPrice);
        setCurrencyPriceCell(row, COL_PRICE_EUR, eurPrice);

        // Tarih alanları (ilk aktif fiyattan al)
        ProductPrice activePrice = tryPrice != null ? tryPrice : (usdPrice != null ? usdPrice : eurPrice);
        if (activePrice != null) {
            if (activePrice.getValidFrom() != null) {
                row.createCell(COL_VALID_FROM).setCellValue(
                        activePrice.getValidFrom().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            }
            if (activePrice.getValidUntil() != null) {
                row.createCell(COL_VALID_UNTIL).setCellValue(
                        activePrice.getValidUntil().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            }
            row.createCell(COL_IS_ACTIVE).setCellValue(activePrice.getIsActive() ? "EVET" : "HAYIR");
        } else {
            row.createCell(COL_IS_ACTIVE).setCellValue("EVET"); // Default
        }
    }

    private void setCurrencyPriceCell(Row row, int columnIndex, ProductPrice price) {
        Cell cell = row.createCell(columnIndex);
        if (price != null && price.getAmount() != null) {
            cell.setCellValue(price.getAmount().doubleValue());
        }
        // ÖZELLİK 1: Fiyat yoksa boş bırak (null değil, boş hücre)
    }

    private Map<CurrencyType, ProductPrice> getProductPricesForDealer(Long productId, Long dealerId, boolean activeOnly) {
        List<ProductPrice> prices;

        if (activeOnly) {
            prices = productPriceRepository.findByProductIdAndDealerIdAndStatus(
                            productId, dealerId, EntityStatus.ACTIVE)
                    .stream()
                    .filter(ProductPrice::isValidNow)
                    .collect(Collectors.toList());
        } else {
            prices = productPriceRepository.findByProductIdAndDealerIdAndStatus(
                    productId, dealerId, EntityStatus.ACTIVE);
        }

        return prices.stream()
                .collect(Collectors.toMap(
                        ProductPrice::getCurrency,
                        price -> price,
                        (existing, replacement) -> existing // İlkini tut
                ));
    }

    private int findDataStartRow(Sheet sheet) {
        // "Ürün Kodu" header'ını ara
        for (int i = 0; i <= 10; i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell firstCell = row.getCell(0);
                if (firstCell != null && "Ürün Kodu".equals(getCellValueAsString(firstCell))) {
                    return i + 1; // Data bir sonraki satırdan başlar
                }
            }
        }
        return -1; // Bulunamadı
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) return true;

        for (int i = 0; i < 9; i++) {
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

        String productCode = getCellValueAsString(row.getCell(COL_PRODUCT_CODE));
        if (productCode.trim().isEmpty()) {
            return priceDataList; // Ürün kodu yoksa atla
        }

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
            priceData.setProductCode(productCode);
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
            priceData.setProductCode(productCode);
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
            priceData.setProductCode(productCode);
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

    private boolean saveOrUpdatePrice(Product product, Dealer dealer, ExcelPriceData priceData, boolean updateExisting) {
        // Mevcut fiyatı kontrol et
        Optional<ProductPrice> existingPrice = productPriceRepository.findByProductIdAndDealerIdAndCurrency(
                product.getId(), dealer.getId(), priceData.getCurrency());

        if (existingPrice.isPresent()) {
//            if (!updateExisting) {
//                throw new IllegalArgumentException("Fiyat zaten mevcut - Ürün: " + priceData.getProductCode() +
//                        ", Currency: " + priceData.getCurrency());
//            }

            // Güncelle
            ProductPrice price = existingPrice.get();
            price.setAmount(priceData.getAmount());
            price.setValidFrom(priceData.getValidFrom());
            price.setValidUntil(priceData.getValidUntil());
            price.setIsActive(priceData.getIsActive());
            productPriceRepository.save(price);

            logger.info("Updated price for product: " + product.getCode() + ", currency: " + priceData.getCurrency());
            return true;

        } else {
            // Yeni oluştur
            ProductPrice price = new ProductPrice();
            price.setProduct(product);
            price.setDealer(dealer);
            price.setCurrency(priceData.getCurrency());
            price.setAmount(priceData.getAmount());
            price.setValidFrom(priceData.getValidFrom());
            price.setValidUntil(priceData.getValidUntil());
            price.setIsActive(priceData.getIsActive());
            price.setStatus(EntityStatus.ACTIVE);
            productPriceRepository.save(price);

            logger.info("Created new price for product: " + product.getCode() + ", currency: " + priceData.getCurrency());
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
        // Örnek veri satırları - Esnek fiyat girişi örnekleri
        String[][] sampleData = {
                {"TI-001", "Titanyum İmplant 4.0mm", "50", "150.00", "15.50", "14.20", "01.01.2025 00:00", "31.12.2025 23:59", "EVET"},
                {"PL-002", "Titanyum Plak 6 Delik", "25", "280.00", "", "", "", "", "EVET"}, // Sadece TRY
                {"SC-003", "Titanyum Vida 3.5mm", "100", "", "5.50", "5.20", "", "", "EVET"} // Sadece USD ve EUR
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
                    // Türkçe format desteği (virgül -> nokta)
                    value = value.replace(",", ".");
                    yield new BigDecimal(value);
                }
                case FORMULA -> BigDecimal.valueOf(cell.getNumericCellValue());
                default -> null;
            };
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
        for (int i = 0; i < 9; i++) {
            Cell cell = row.getCell(i);
            if (i > 0) sb.append(" | ");
            sb.append(getCellValueAsString(cell));
        }
        return sb.toString();
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < 9; i++) {
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
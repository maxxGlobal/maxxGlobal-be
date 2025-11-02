package com.maxx_global.service;

import com.maxx_global.dto.productExcel.ExcelProductData;
import com.maxx_global.dto.productExcel.ProductImportError;
import com.maxx_global.dto.productExcel.ProductImportResult;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Category;
import com.maxx_global.entity.Product;
import com.maxx_global.entity.ProductVariant;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.CategoryRepository;
import com.maxx_global.repository.ProductRepository;
import com.maxx_global.repository.ProductVariantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductExcelService {

    private static final Logger logger = Logger.getLogger(ProductExcelService.class.getName());

    // Excel sütun indeksleri - Varyant bazlı yapı
    private static final int COL_PRODUCT_CODE = 0;
    private static final int COL_PRODUCT_NAME = 1;
    private static final int COL_DESCRIPTION = 2;
    private static final int COL_CATEGORY_NAME = 3;
    private static final int COL_MATERIAL = 4;
    private static final int COL_VARIANT_SIZE = 5;  // ✅ Varyant boyutu (eski: COL_SIZE)
    private static final int COL_SKU = 6;           // ✅ YENİ: Varyant SKU
    private static final int COL_DIAMETER = 7;
    private static final int COL_ANGLE = 8;
    private static final int COL_STERILE = 9;
    private static final int COL_SINGLE_USE = 10;
    private static final int COL_IMPLANTABLE = 11;
    private static final int COL_CE_MARKING = 12;
    private static final int COL_FDA_APPROVED = 13;
    private static final int COL_MEDICAL_DEVICE_CLASS = 14;
    private static final int COL_REGULATORY_NUMBER = 15;
    private static final int COL_WEIGHT_GRAMS = 16;
    private static final int COL_DIMENSIONS = 17;
    private static final int COL_COLOR = 18;
    private static final int COL_SURFACE_TREATMENT = 19;
    private static final int COL_SERIAL_NUMBER = 20;
    private static final int COL_MANUFACTURER_CODE = 21;
    private static final int COL_MANUFACTURING_DATE = 22;
    private static final int COL_EXPIRY_DATE = 23;
    private static final int COL_SHELF_LIFE_MONTHS = 24;
    private static final int COL_UNIT = 25;
    private static final int COL_BARCODE = 26;
    private static final int COL_LOT_NUMBER = 27;
    private static final int COL_VARIANT_STOCK = 28; // ✅ Varyant stoğu (eski: COL_STOCK_QUANTITY)
    private static final int COL_MIN_ORDER_QUANTITY = 29;
    private static final int COL_MAX_ORDER_QUANTITY = 30;

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
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final AppUserService appUserService;
    private final StockTrackerService stockTrackerService;

    public ProductExcelService(ProductRepository productRepository,
                               CategoryRepository categoryRepository,
                               ProductVariantRepository productVariantRepository,
                               AppUserService appUserService,
                               StockTrackerService stockTrackerService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productVariantRepository = productVariantRepository;
        this.appUserService = appUserService;
        this.stockTrackerService = stockTrackerService;
    }

    /**
     * Boş ürün import şablonu oluştur
     */
    public byte[] generateProductTemplate() throws IOException {
        logger.info("Generating product import template");

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Ürün Import Şablonu");

            // Header ve talimatları oluştur
            createTemplateHeader(workbook, sheet);

            // Sütun başlıklarını oluştur
            createProductColumnHeaders(workbook, sheet, 8);

            // Örnek veri satırları ekle
            createSampleProductData(sheet, 9);

            // Kategori bilgilerini ayrı bir sheet'te ekle
            createCategorySheet(workbook);

            // Sütun genişliklerini ayarla
            autoSizeColumns(sheet);

            workbook.write(outputStream);

            logger.info("Product template generated successfully");
            return outputStream.toByteArray();
        }
    }

    /**
     * Mevcut ürünleri Excel'e aktar
     */
    public byte[] exportProducts(Long categoryId, boolean activeOnly, boolean inStockOnly) throws IOException {
        logger.info("Exporting products - categoryId: " + categoryId +
                ", activeOnly: " + activeOnly + ", inStockOnly: " + inStockOnly);

        // Ürünleri getir
        List<Product> products = getProductsForExport(categoryId, activeOnly, inStockOnly);

        // Her ürün için aktif varyantları önceden çek
        Map<Long, List<ProductVariant>> variantsByProduct = new LinkedHashMap<>();
        int totalVariantCount = 0;

        for (Product product : products) {
            List<ProductVariant> variants = productVariantRepository
                    .findByProductIdAndStatusOrderBySizeAsc(product.getId(), EntityStatus.ACTIVE);

            variantsByProduct.put(product.getId(), variants);

            // Varyant yoksa bile en az bir satır oluşturacağız
            totalVariantCount += variants.isEmpty() ? 1 : variants.size();
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Ürün Listesi");

            // Export header'ını oluştur
            createExportHeader(workbook, sheet, products.size(), totalVariantCount);

            // Sütun başlıklarını oluştur
            createProductColumnHeaders(workbook, sheet, 4);

            // Ürün ve varyant verilerini ekle (her ürün farklı renkte)
            int rowIndex = 5;
            int productIndex = 0;
            for (Product product : products) {
                List<ProductVariant> variants = variantsByProduct.getOrDefault(product.getId(), Collections.emptyList());

                // Her ürün için paletten bir renk seç
                short colorIndex = PRODUCT_ROW_COLORS[productIndex % PRODUCT_ROW_COLORS.length];

                if (variants.isEmpty()) {
                    createProductVariantDataRow(sheet, rowIndex++, product, null, colorIndex);
                } else {
                    for (ProductVariant variant : variants) {
                        createProductVariantDataRow(sheet, rowIndex++, product, variant, colorIndex);
                    }
                }

                productIndex++;
            }

            autoSizeColumns(sheet);
            workbook.write(outputStream);

            logger.info("Products exported successfully: " + products.size() +
                    " products, " + totalVariantCount + " variants");
            return outputStream.toByteArray();
        }
    }

    /**
     * Excel'den ürün verilerini import et - VARYANT BAZLI
     */
    @Transactional
    public ProductImportResult importProductsFromExcel(MultipartFile file,
                                                       boolean updateExisting,
                                                       boolean skipErrors) throws IOException {
        logger.info("Importing products from Excel with VARIANT support - updateExisting: " + updateExisting +
                ", skipErrors: " + skipErrors);

        List<ProductImportError> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;
        int updatedProductCount = 0;
        int createdProductCount = 0;
        int variantCount = 0;

        String batchId = generateBatchId();
        String fileName = file.getOriginalFilename();
        AppUser currentUser = getCurrentUser();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            int startRow = findProductDataStartRow(sheet);
            if (startRow == -1) {
                throw new IllegalArgumentException("Excel dosyasında veri satırları bulunamadı");
            }

            // Cache'ler oluştur
            Map<String, Category> categoryMap = createCategoryNameMap();
            Map<String, Product> existingProductMap = createProductCodeMap();

            // ✅ 1. ADIM: Tüm satırları oku ve product code'a göre grupla
            Map<String, List<ExcelProductData>> groupedData = new LinkedHashMap<>();

            for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isEmptyProductRow(row)) {
                    continue;
                }

                totalRows++;

                try {
                    ExcelProductData productData = parseRowToProductData(row, rowIndex + 1);

                    if (!productData.isValid()) {
                        errors.add(new ProductImportError(
                                productData.getRowNumber(),
                                productData.getProductCode(),
                                productData.getValidationError(),
                                getRowDataAsString(row)
                        ));
                        if (!skipErrors) {
                            throw new IllegalArgumentException("Satır " + (rowIndex + 1) + ": " +
                                    productData.getValidationError());
                        }
                        continue;
                    }

                    // Kategori kontrolü
                    if (!categoryMap.containsKey(productData.getCategoryName().toUpperCase())) {
                        String error = "Kategori bulunamadı: " + productData.getCategoryName();
                        errors.add(new ProductImportError(
                                productData.getRowNumber(),
                                productData.getProductCode(),
                                error,
                                getRowDataAsString(row)
                        ));
                        if (!skipErrors) {
                            throw new IllegalArgumentException("Satır " + (rowIndex + 1) + ": " + error);
                        }
                        continue;
                    }

                    // Product code'a göre grupla
                    String productCode = productData.getProductCode().toUpperCase();
                    groupedData.computeIfAbsent(productCode, k -> new ArrayList<>()).add(productData);

                } catch (Exception e) {
                    String error = "Satır işleme hatası: " + e.getMessage();
                    errors.add(new ProductImportError(
                            rowIndex + 1,
                            getCellValueAsString(row.getCell(COL_PRODUCT_CODE)),
                            error,
                            getRowDataAsString(row)
                    ));

                    if (!skipErrors) {
                        throw new IllegalArgumentException("Satır " + (rowIndex + 1) + ": " + error);
                    }
                }
            }

            // ✅ 2. ADIM: Her grup için Product + Variants oluştur/güncelle
            for (Map.Entry<String, List<ExcelProductData>> entry : groupedData.entrySet()) {
                String productCode = entry.getKey();
                List<ExcelProductData> variants = entry.getValue();

                try {
                    Category category = categoryMap.get(variants.get(0).getCategoryName().toUpperCase());

                    boolean isUpdate = saveOrUpdateProductWithVariants(
                            productCode, variants, category, existingProductMap,
                            updateExisting, currentUser, batchId, fileName);

                    if (isUpdate) {
                        updatedProductCount++;
                    } else {
                        createdProductCount++;
                    }

                    variantCount += variants.size();
                    successCount += variants.size();

                } catch (Exception e) {
                    for (ExcelProductData variantData : variants) {
                        errors.add(new ProductImportError(
                                variantData.getRowNumber(),
                                productCode,
                                "Product/Variant kaydetme hatası: " + e.getMessage(),
                                ""
                        ));
                    }

                    if (!skipErrors) {
                        throw new IllegalArgumentException("Product " + productCode + " kaydedilemedi: " + e.getMessage());
                    }

                    logger.warning("Product " + productCode + " failed: " + e.getMessage());
                }
            }

            boolean success = errors.isEmpty() || (skipErrors && successCount > 0);
            String message = String.format(
                    "Import tamamlandı. Toplam Satır: %d, Başarılı: %d, Hatalı: %d, " +
                    "Ürün Güncellenen: %d, Ürün Yeni: %d, Toplam Variant: %d (Batch ID: %s)",
                    totalRows, successCount, errors.size(),
                    updatedProductCount, createdProductCount, variantCount, batchId);

            logger.info(message);

            return new ProductImportResult(
                    totalRows,
                    successCount,
                    errors.size(),
                    updatedProductCount,
                    createdProductCount,
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
     * ✅ YENİ METOD: Batch ID oluştur
     */
    private String generateBatchId() {
        return "EXCEL_" + System.currentTimeMillis();
    }

    /**
     * ✅ YENİ METOD: Mevcut kullanıcıyı al (SecurityContext'ten)
     */
    private AppUser getCurrentUser() {
        // Bu metod SecurityContextHolder'dan mevcut kullanıcıyı alacak
        // Şimdilik null dönelim, sonra implement ederiz
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                // AppUserService kullanarak kullanıcıyı al
                return appUserService.getCurrentUser(authentication);
            }
        } catch (Exception e) {
            logger.warning("Could not get current user for Excel import: " + e.getMessage());
        }
        return null; // System user olarak kaydedilecek
    }


    /**
     * Excel dosyasını gerçek import yapmadan doğrula
     */
    public ProductImportResult validateProductExcel(MultipartFile file) throws IOException {
        logger.info("Validating product Excel file");

        List<ProductImportError> errors = new ArrayList<>();
        int totalRows = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            int startRow = findProductDataStartRow(sheet);
            if (startRow == -1) {
                throw new IllegalArgumentException("Excel dosyasında veri satırları bulunamadı");
            }

            Map<String, Category> categoryMap = createCategoryNameMap();
            Set<String> productCodes = new HashSet<>();

            for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isEmptyProductRow(row)) {
                    continue;
                }

                totalRows++;

                try {
                    ExcelProductData productData = parseRowToProductData(row, rowIndex + 1);

                    // Basic validation
                    if (!productData.isValid()) {
                        errors.add(new ProductImportError(
                                productData.getRowNumber(),
                                productData.getProductCode(),
                                productData.getValidationError(),
                                getRowDataAsString(row)
                        ));
                        continue;
                    }

                    // Duplicate product code check
                    if (!productCodes.add(productData.getProductCode().toUpperCase())) {
                        errors.add(new ProductImportError(
                                productData.getRowNumber(),
                                productData.getProductCode(),
                                "Duplicate product code in Excel file",
                                getRowDataAsString(row)
                        ));
                        continue;
                    }

                    // Category validation
                    if (!categoryMap.containsKey(productData.getCategoryName().toUpperCase())) {
                        errors.add(new ProductImportError(
                                productData.getRowNumber(),
                                productData.getProductCode(),
                                "Kategori bulunamadı: " + productData.getCategoryName(),
                                getRowDataAsString(row)
                        ));
                    }

                } catch (Exception e) {
                    errors.add(new ProductImportError(
                            rowIndex + 1,
                            getCellValueAsString(row.getCell(COL_PRODUCT_CODE)),
                            "Validation error: " + e.getMessage(),
                            getRowDataAsString(row)
                    ));
                }
            }

            boolean success = errors.isEmpty();
            String message = success ? "Dosya geçerli" :
                    "Dosyada " + errors.size() + " hata bulundu";

            return new ProductImportResult(
                    totalRows,
                    success ? totalRows : 0,
                    errors.size(),
                    0,
                    0,
                    errors,
                    success,
                    message
            );

        } catch (Exception e) {
            logger.severe("Excel validation failed: " + e.getMessage());
            throw new RuntimeException("Excel doğrulama hatası: " + e.getMessage(), e);
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private void createTemplateHeader(Workbook workbook, Sheet sheet) {
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle infoStyle = createInfoStyle(workbook);

        // Başlık
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ÜRÜN İMPORT ŞABLONU");
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 29));

        // Tarih
        Row dateRow = sheet.createRow(1);
        Cell dateCell = dateRow.createCell(0);
        dateCell.setCellValue("Oluşturma Tarihi: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        dateCell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 29));

        // Talimatlar
        createInstructions(workbook, sheet, infoStyle, 2);
    }

    private void createExportHeader(Workbook workbook, Sheet sheet, int productCount, int variantCount) {
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle infoStyle = createInfoStyle(workbook);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ÜRÜN LİSTESİ");
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 29));

        Row infoRow = sheet.createRow(1);
        Cell infoCell = infoRow.createCell(0);
        infoCell.setCellValue("Toplam Ürün: " + productCount + " | Toplam Varyant: " + variantCount +
                " | Export Tarihi: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        infoCell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 29));
    }

    private void createInstructions(Workbook workbook, Sheet sheet, CellStyle infoStyle, int startRow) {
        String[] instructions = {
                "TÜM ALANLAR DOLDURULMAK ZORUNDA DEĞİLDİR - Sadece gerekli alanları doldurun",
                "ZORUNLU ALANLAR: Ürün Kodu, Ürün Adı, Kategori Adı, Lot Numarası",
                "Kategori Adı: Sistemde mevcut kategori adlarından birini kullanın (2. sheet'e bakın)",
                "Boolean alanlar: EVET/HAYIR veya TRUE/FALSE yazın",
                "Tarih formatı: dd.MM.yyyy (örnek: 15.01.2025)",
                "Sayısal alanlar: Ondalıklı sayılar için nokta kullanın (örnek: 15.50)",
                "Ürün kodları benzersiz olmalıdır"
        };

        for (int i = 0; i < instructions.length; i++) {
            Row row = sheet.createRow(startRow + i);
            Cell cell = row.createCell(0);
            cell.setCellValue(instructions[i]);
            cell.setCellStyle(infoStyle);
            sheet.addMergedRegion(new CellRangeAddress(startRow + i, startRow + i, 0, 29));
        }
    }

    private void createProductColumnHeaders(Workbook workbook, Sheet sheet, int rowIndex) {
        CellStyle headerStyle = createColumnHeaderStyle(workbook);

        Row headerRow = sheet.createRow(rowIndex);

        String[] headers = {
                "Ürün Kodu", "Ürün Adı", "Açıklama", "Kategori Adı", "Malzeme",
                "Varyant Boyutu", "SKU Kodu", "Çap", "Açı", "Steril", "Tek Kullanımlık",
                "İmplant", "CE İşareti", "FDA Onaylı", "Tıbbi Cihaz Sınıfı", "Düzenleyici No",
                "Ağırlık (gr)", "Boyutlar", "Renk", "Yüzey İşlemi", "Seri No",
                "Üretici Kodu", "Üretim Tarihi", "Son Kullanma", "Raf Ömrü (ay)", "Birim",
                "Barkod", "Lot Numarası", "Varyant Stoğu", "Min Sipariş", "Max Sipariş"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void createSampleProductData(Sheet sheet, int startRow) {
        Workbook workbook = sheet.getWorkbook();

        // ✅ Alternate renk stilleri oluştur
        CellStyle normalStyle = createDataCellStyle(workbook);
        CellStyle alternateStyle = createAlternateDataCellStyle(workbook);

        // ✅ Örnek veri satırları - AYNI ÜRÜN KODUNDA FARKLI VARYANTLAR
        String[][] sampleData = {
                // TI-001 Ürününün 3 farklı varyantı (4.0mm, 4.5mm, 5.0mm)
                {"TI-001", "Titanyum İmplant", "Dental implant çözümü", "Dental İmplantlar", "Titanyum",
                        "4.0mm", "TI-001-40", "", "EVET", "EVET",
                        "EVET", "EVET", "HAYIR", "Class II", "REG-2024-001",
                        "15.5", "10x15x20mm", "Gümüş", "Anodize", "SN-2024-001",
                        "MFG-001", "15.01.2024", "15.01.2027", "36", "adet",
                        "1234567890123", "LOT-2024-001", "100", "1", "1000"},

                {"TI-001", "Titanyum İmplant", "Dental implant çözümü", "Dental İmplantlar", "Titanyum",
                        "4.5mm", "TI-001-45", "", "EVET", "EVET",
                        "EVET", "EVET", "HAYIR", "Class II", "REG-2024-001",
                        "16.2", "10x15x20mm", "Gümüş", "Anodize", "SN-2024-002",
                        "MFG-001", "15.01.2024", "15.01.2027", "36", "adet",
                        "1234567890124", "LOT-2024-001", "50", "1", "1000"},

                {"TI-001", "Titanyum İmplant", "Dental implant çözümü", "Dental İmplantlar", "Titanyum",
                        "5.0mm", "TI-001-50", "", "EVET", "EVET",
                        "EVET", "EVET", "HAYIR", "Class II", "REG-2024-001",
                        "17.0", "10x15x20mm", "Gümüş", "Anodize", "SN-2024-003",
                        "MFG-001", "15.01.2024", "15.01.2027", "36", "adet",
                        "1234567890125", "LOT-2024-001", "75", "1", "1000"},

                // PL-002 Ürününün 2 farklı varyantı (6 delik, 8 delik)
                {"PL-002", "Titanyum Plak", "Ortopedik plak sistemi", "Plaklar", "Titanyum",
                        "6 Delik", "PL-002-6", "0°", "EVET", "HAYIR",
                        "EVET", "EVET", "HAYIR", "Class II", "REG-2024-002",
                        "45.2", "80x15x3mm", "Doğal", "Sandblasted", "SN-2024-004",
                        "MFG-002", "20.01.2024", "20.01.2027", "36", "adet",
                        "1234567890126", "LOT-2024-002", "30", "1", "100"},

                {"PL-002", "Titanyum Plak", "Ortopedik plak sistemi", "Plaklar", "Titanyum",
                        "8 Delik", "PL-002-8", "0°", "EVET", "HAYIR",
                        "EVET", "EVET", "HAYIR", "Class II", "REG-2024-002",
                        "52.8", "100x15x3mm", "Doğal", "Sandblasted", "SN-2024-005",
                        "MFG-002", "20.01.2024", "20.01.2027", "36", "adet",
                        "1234567890127", "LOT-2024-002", "20", "1", "100"}
        };

        // ✅ Product code bazlı alternate renklendirme
        String currentProductCode = null;
        boolean useAlternateColor = false;

        for (int i = 0; i < sampleData.length; i++) {
            String productCode = sampleData[i][0];

            // Yeni product code başladığında rengi değiştir
            if (!productCode.equals(currentProductCode)) {
                currentProductCode = productCode;
                useAlternateColor = !useAlternateColor;
            }

            // Satırı oluştur ve rengi uygula
            Row row = sheet.createRow(startRow + i);
            CellStyle rowStyle = useAlternateColor ? alternateStyle : normalStyle;

            for (int j = 0; j < sampleData[i].length; j++) {
                Cell cell = row.createCell(j);
                cell.setCellValue(sampleData[i][j]);
                cell.setCellStyle(rowStyle);
            }
        }
    }
    private CellStyle createDataCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createColoredDataCellStyle(Workbook workbook, short colorIndex) {
        CellStyle style = createDataCellStyle(workbook);
        style.setFillForegroundColor(colorIndex);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * ✅ YENİ METOD: Alternate renk veri hücresi stili (açık mavi arka plan)
     */
    private CellStyle createAlternateDataCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        // ✅ Açık mavi arka plan (aynı ürün grubu görselleştirmesi için)
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void createCategorySheet(Workbook workbook) {
        Sheet categorySheet = workbook.createSheet("Kategoriler");

        CellStyle headerStyle = createColumnHeaderStyle(workbook);

        // Başlık
        Row titleRow = categorySheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Mevcut Kategori Adları");
        titleCell.setCellStyle(headerStyle);
        categorySheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

        Row headerRow = categorySheet.createRow(1);
        headerRow.createCell(0).setCellValue("Kategori ID");
        headerRow.createCell(1).setCellValue("Kategori Adı");
        headerRow.getCell(0).setCellStyle(headerStyle);
        headerRow.getCell(1).setCellStyle(headerStyle);

        // Kategorileri getir ve listele
        List<Category> categories = categoryRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);

        for (int i = 0; i < categories.size(); i++) {
            Row row = categorySheet.createRow(i + 2);
            Category category = categories.get(i);
            row.createCell(0).setCellValue(category.getId());
            row.createCell(1).setCellValue(category.getName());
        }

        categorySheet.autoSizeColumn(0);
        categorySheet.autoSizeColumn(1);
    }

    private void createProductVariantDataRow(Sheet sheet, int rowIndex, Product product,
                                            ProductVariant variant, short colorIndex) {
        Row row = sheet.createRow(rowIndex);

        // Renkli veri hücresi stili oluştur (aynı ürünün varyantları aynı renkte)
        Workbook workbook = sheet.getWorkbook();
        CellStyle dataCellStyle = createColoredDataCellStyle(workbook, colorIndex);

        // Ortak ürün alanları
        setCellValueWithStyle(row, COL_PRODUCT_CODE, product.getCode(), dataCellStyle);
        setCellValueWithStyle(row, COL_PRODUCT_NAME, product.getName(), dataCellStyle);
        setCellValueWithStyle(row, COL_DESCRIPTION, product.getDescription(), dataCellStyle);
        setCellValueWithStyle(row, COL_CATEGORY_NAME,
                product.getCategory() != null ? product.getCategory().getName() : "", dataCellStyle);
        setCellValueWithStyle(row, COL_MATERIAL, product.getMaterial(), dataCellStyle);

        // Varyant alanları (mevcut değilse ürünün eski alanlarını kullan)
        String variantSize = variant != null ? variant.getSize() : product.getSize();
        String variantSku = variant != null ? variant.getSku() : "";
        Integer variantStock = variant != null ? variant.getStockQuantity() : product.getStockQuantity();
        Integer minOrder = variant != null ? variant.getMinimumOrderQuantity() : product.getMinimumOrderQuantity();
        Integer maxOrder = variant != null ? variant.getMaximumOrderQuantity() : product.getMaximumOrderQuantity();

        setCellValueWithStyle(row, COL_VARIANT_SIZE, variantSize, dataCellStyle);
        setCellValueWithStyle(row, COL_SKU, variantSku, dataCellStyle);

        // Ürün seviyesindeki diğer alanlar
        setCellValueWithStyle(row, COL_DIAMETER, product.getDiameter(), dataCellStyle);
        setCellValueWithStyle(row, COL_ANGLE, product.getAngle(), dataCellStyle);
        setCellValueWithStyle(row, COL_STERILE, booleanToString(product.getSterile()), dataCellStyle);
        setCellValueWithStyle(row, COL_SINGLE_USE, booleanToString(product.getSingleUse()), dataCellStyle);
        setCellValueWithStyle(row, COL_IMPLANTABLE, booleanToString(product.getImplantable()), dataCellStyle);
        setCellValueWithStyle(row, COL_CE_MARKING, booleanToString(product.getCeMarking()), dataCellStyle);
        setCellValueWithStyle(row, COL_FDA_APPROVED, booleanToString(product.getFdaApproved()), dataCellStyle);
        setCellValueWithStyle(row, COL_MEDICAL_DEVICE_CLASS, product.getMedicalDeviceClass(), dataCellStyle);
        setCellValueWithStyle(row, COL_REGULATORY_NUMBER, product.getRegulatoryNumber(), dataCellStyle);
        setCellValueWithStyle(row, COL_WEIGHT_GRAMS, product.getWeightGrams(), dataCellStyle);
        setCellValueWithStyle(row, COL_DIMENSIONS, product.getDimensions(), dataCellStyle);
        setCellValueWithStyle(row, COL_COLOR, product.getColor(), dataCellStyle);
        setCellValueWithStyle(row, COL_SURFACE_TREATMENT, product.getSurfaceTreatment(), dataCellStyle);
        setCellValueWithStyle(row, COL_SERIAL_NUMBER, product.getSerialNumber(), dataCellStyle);
        setCellValueWithStyle(row, COL_MANUFACTURER_CODE, product.getManufacturerCode(), dataCellStyle);
        setCellValueWithStyle(row, COL_MANUFACTURING_DATE, product.getManufacturingDate(), dataCellStyle);
        setCellValueWithStyle(row, COL_EXPIRY_DATE, product.getExpiryDate(), dataCellStyle);
        setCellValueWithStyle(row, COL_SHELF_LIFE_MONTHS, product.getShelfLifeMonths(), dataCellStyle);
        setCellValueWithStyle(row, COL_UNIT, product.getUnit(), dataCellStyle);
        setCellValueWithStyle(row, COL_BARCODE, product.getBarcode(), dataCellStyle);
        setCellValueWithStyle(row, COL_LOT_NUMBER, product.getLotNumber(), dataCellStyle);

        // Varyant stok ve sipariş bilgileri
        setCellValueWithStyle(row, COL_VARIANT_STOCK, variantStock, dataCellStyle);
        setCellValueWithStyle(row, COL_MIN_ORDER_QUANTITY, minOrder, dataCellStyle);
        setCellValueWithStyle(row, COL_MAX_ORDER_QUANTITY, maxOrder, dataCellStyle);
    }

    /**
     * ✅ YENİ METOD: Stil ile hücre değeri set etme
     */
    private void setCellValueWithStyle(Row row, int columnIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellStyle(style);

        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Integer) {
            cell.setCellValue(((Integer) value).doubleValue());
        } else if (value instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal) value).doubleValue());
        } else if (value instanceof LocalDate) {
            cell.setCellValue(((LocalDate) value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        } else if (value instanceof Boolean) {
            cell.setCellValue(((Boolean) value) ? "EVET" : "HAYIR");
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private String booleanToString(Boolean value) {
        if (value == null) return "";
        return value ? "EVET" : "HAYIR";
    }

    private List<Product> getProductsForExport(Long categoryId, boolean activeOnly, boolean inStockOnly) {
        if (categoryId != null) {
            // Kategori kontrolü
            categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new EntityNotFoundException("Category not found: " + categoryId));

            List<Product> products = productRepository.findByCategoryIdAndStatusOrderByNameAsc(
                    categoryId, EntityStatus.ACTIVE);

            return filterProducts(products, inStockOnly);
        } else {
            List<Product> products = activeOnly ?
                    productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE) :
                    productRepository.findAll();

            return filterProducts(products, inStockOnly);
        }
    }

    private List<Product> filterProducts(List<Product> products, boolean inStockOnly) {
        if (!inStockOnly) {
            return products;
        }

        return products.stream()
                .filter(product -> !productVariantRepository
                        .findInStockVariantsByProduct(product.getId(), EntityStatus.ACTIVE)
                        .isEmpty())
                .collect(Collectors.toList());
    }

    private int findProductDataStartRow(Sheet sheet) {
        // "Ürün Kodu" header'ını ara
        for (int i = 0; i <= 15; i++) {
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

    private boolean isEmptyProductRow(Row row) {
        if (row == null) return true;

        // En azından ürün kodu ve adı olmalı
        Cell codeCell = row.getCell(COL_PRODUCT_CODE);
        Cell nameCell = row.getCell(COL_PRODUCT_NAME);

        return (codeCell == null || getCellValueAsString(codeCell).trim().isEmpty()) &&
                (nameCell == null || getCellValueAsString(nameCell).trim().isEmpty());
    }

    private ExcelProductData parseRowToProductData(Row row, int rowNumber) {
        ExcelProductData productData = new ExcelProductData(rowNumber);

        productData.setProductCode(getCellValueAsString(row.getCell(COL_PRODUCT_CODE)));
        productData.setProductName(getCellValueAsString(row.getCell(COL_PRODUCT_NAME)));
        productData.setDescription(getCellValueAsString(row.getCell(COL_DESCRIPTION)));
        productData.setCategoryName(getCellValueAsString(row.getCell(COL_CATEGORY_NAME)));
        productData.setMaterial(getCellValueAsString(row.getCell(COL_MATERIAL)));
        productData.setSize(getCellValueAsString(row.getCell(COL_VARIANT_SIZE)));  // ✅ Varyant boyutu
        productData.setSku(getCellValueAsString(row.getCell(COL_SKU)));            // ✅ Varyant SKU
        productData.setDiameter(getCellValueAsString(row.getCell(COL_DIAMETER)));
        productData.setAngle(getCellValueAsString(row.getCell(COL_ANGLE)));
        productData.setSterile(getCellValueAsBoolean(row.getCell(COL_STERILE)));
        productData.setSingleUse(getCellValueAsBoolean(row.getCell(COL_SINGLE_USE)));
        productData.setImplantable(getCellValueAsBoolean(row.getCell(COL_IMPLANTABLE)));
        productData.setCeMarking(getCellValueAsBoolean(row.getCell(COL_CE_MARKING)));
        productData.setFdaApproved(getCellValueAsBoolean(row.getCell(COL_FDA_APPROVED)));
        productData.setMedicalDeviceClass(getCellValueAsString(row.getCell(COL_MEDICAL_DEVICE_CLASS)));
        productData.setRegulatoryNumber(getCellValueAsString(row.getCell(COL_REGULATORY_NUMBER)));
        productData.setWeightGrams(getCellValueAsBigDecimal(row.getCell(COL_WEIGHT_GRAMS)));
        productData.setDimensions(getCellValueAsString(row.getCell(COL_DIMENSIONS)));
        productData.setColor(getCellValueAsString(row.getCell(COL_COLOR)));
        productData.setSurfaceTreatment(getCellValueAsString(row.getCell(COL_SURFACE_TREATMENT)));
        productData.setSerialNumber(getCellValueAsString(row.getCell(COL_SERIAL_NUMBER)));
        productData.setManufacturerCode(getCellValueAsString(row.getCell(COL_MANUFACTURER_CODE)));
        productData.setManufacturingDate(getCellValueAsLocalDate(row.getCell(COL_MANUFACTURING_DATE)));
        productData.setExpiryDate(getCellValueAsLocalDate(row.getCell(COL_EXPIRY_DATE)));
        productData.setShelfLifeMonths(getCellValueAsInteger(row.getCell(COL_SHELF_LIFE_MONTHS)));
        productData.setUnit(getCellValueAsString(row.getCell(COL_UNIT)));
        productData.setBarcode(getCellValueAsString(row.getCell(COL_BARCODE)));
        productData.setLotNumber(getCellValueAsString(row.getCell(COL_LOT_NUMBER)));
        productData.setStockQuantity(getCellValueAsInteger(row.getCell(COL_VARIANT_STOCK)));  // ✅ Varyant stoğu
        productData.setMinimumOrderQuantity(getCellValueAsInteger(row.getCell(COL_MIN_ORDER_QUANTITY)));
        productData.setMaximumOrderQuantity(getCellValueAsInteger(row.getCell(COL_MAX_ORDER_QUANTITY)));

        return productData;
    }

    private Map<String, Category> createCategoryNameMap() {
        List<Category> categories = categoryRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);
        return categories.stream()
                .collect(Collectors.toMap(
                        category -> category.getName().toUpperCase(),
                        category -> category,
                        (existing, replacement) -> existing
                ));
    }

    private Map<String, Product> createProductCodeMap() {
        List<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);
        return products.stream()
                .collect(Collectors.toMap(
                        product -> product.getCode().toUpperCase(),
                        product -> product,
                        (existing, replacement) -> existing
                ));
    }


    /**
     * ✅ YENİ METOD: Product + Variants kaydetme/güncelleme
     */
    private boolean saveOrUpdateProductWithVariants(String productCode,
                                                    List<ExcelProductData> variantsData,
                                                    Category category,
                                                    Map<String, Product> existingProductMap,
                                                    boolean updateExisting,
                                                    AppUser performedBy,
                                                    String batchId,
                                                    String fileName) {
        // İlk satırdan ortak ürün bilgilerini al
        ExcelProductData firstRow = variantsData.get(0);

        // Mevcut ürün kontrolü
        Product existingProduct = existingProductMap.get(productCode);
        Product product;
        boolean isUpdate;

        if (existingProduct != null) {
            if (!updateExisting) {
                throw new IllegalArgumentException("Ürün zaten mevcut - Kod: " + productCode);
            }
            // Ürünü güncelle (varyantlar hariç)
            updateProductFromExcelData(existingProduct, firstRow, category);
            product = productRepository.save(existingProduct);
            isUpdate = true;
            logger.info("Updated product: " + productCode);
        } else {
            // Yeni ürün oluştur (varyantlar hariç)
            product = createProductFromExcelData(firstRow, category);
            product = productRepository.save(product);
            isUpdate = false;
            logger.info("Created new product: " + productCode);
        }

        // ✅ Her satır için ProductVariant oluştur/güncelle
        Map<String, ProductVariant> existingVariants = productVariantRepository
                .findByProductIdAndStatusOrderBySizeAsc(product.getId(), EntityStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(
                        v -> v.getSku().toUpperCase(),
                        v -> v,
                        (existing, replacement) -> existing
                ));

        int totalVariantStock = 0;
        String effectiveFileName = (fileName != null && !fileName.trim().isEmpty()) ? fileName : "Excel Import";

        for (int i = 0; i < variantsData.size(); i++) {
            ExcelProductData variantData = variantsData.get(i);

            // SKU oluştur (yoksa)
            String sku = variantData.getSku();
            if (sku == null || sku.trim().isEmpty()) {
                sku = ProductVariant.generateSku(productCode, variantData.getSize());
            }

            ProductVariant variant = existingVariants.get(sku.toUpperCase());
            boolean variantExists = variant != null;
            Integer oldStock = variantExists && variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
            Integer newStock = variantData.getStockQuantity() != null ? variantData.getStockQuantity() : 0;
            Integer minOrderQty = variantData.getMinimumOrderQuantity();
            Integer maxOrderQty = variantData.getMaximumOrderQuantity();

            totalVariantStock += newStock;

            if (variantExists) {
                // Mevcut varyant güncelle
                variant.setSize(variantData.getSize());
                variant.setStockQuantity(newStock);
                variant.setIsDefault(i == 0); // İlk varyant default

                if (minOrderQty != null) {
                    variant.setMinimumOrderQuantity(minOrderQty);
                }
                if (maxOrderQty != null) {
                    variant.setMaximumOrderQuantity(maxOrderQty);
                }

                variant = productVariantRepository.save(variant);

                logger.info("Updated variant: " + sku + " (Stock: " + oldStock + " -> " + newStock + ")");
            } else {
                // Yeni varyant oluştur
                variant = new ProductVariant(product, variantData.getSize(), sku);
                variant.setStockQuantity(newStock);
                variant.setIsDefault(i == 0); // İlk varyant default
                variant.setStatus(EntityStatus.ACTIVE);

                variant.setMinimumOrderQuantity(minOrderQty != null ? minOrderQty : product.getMinimumOrderQuantity());
                variant.setMaximumOrderQuantity(maxOrderQty != null ? maxOrderQty : product.getMaximumOrderQuantity());

                variant = productVariantRepository.save(variant);

                logger.info("Created new variant: " + sku + " (Initial stock: " + newStock + ")");
            }

            existingVariants.put(sku.toUpperCase(), variant);

            if (!Objects.equals(oldStock, newStock)) {
                String operation = variantExists ? "update" : "import";
                stockTrackerService.trackExcelStockUpdate(
                        variant,
                        oldStock,
                        newStock,
                        operation,
                        performedBy,
                        effectiveFileName
                );
            }

            // Varyant stok hareketini kaydettik
        }

        // Ürünün toplam stok bilgisini varyantlardan güncelle
        if (!Objects.equals(product.getStockQuantity(), totalVariantStock)) {
            product.setStockQuantity(totalVariantStock);
        }
        // Ürünün toplam stok bilgisini varyantlardan güncelle
        if (!Objects.equals(product.getStockQuantity(), totalVariantStock)) {
            product.setStockQuantity(totalVariantStock);
        }

        return isUpdate;
    }

    /**
     * ✅ ESKİ METOD: StockTracker ile entegre ürün kaydetme/güncelleme (DEPRECATED - variant kullan)
     */
    @Deprecated
    private boolean saveOrUpdateProductWithStockTracking(ExcelProductData productData, Category category,
                                                         Map<String, Product> existingProductMap,
                                                         boolean updateExisting, AppUser performedBy,
                                                         String batchId, String fileName) {
        // Mevcut ürün kontrolü
        Product existingProduct = existingProductMap.get(productData.getProductCode().toUpperCase());

        if (existingProduct != null) {
            if (!updateExisting) {
                throw new IllegalArgumentException("Ürün zaten mevcut - Kod: " + productData.getProductCode());
            }

            // ✅ GÜNCELLEME: Eski stok değerini kaydet
            Integer oldStock = existingProduct.getStockQuantity();
            Integer newStock = productData.getStockQuantity() != null ?
                    productData.getStockQuantity() : oldStock;

            // Ürünü güncelle
            updateProductFromExcelData(existingProduct, productData, category);
            existingProduct.setStockQuantity(newStock); // Stok değerini set et
            productRepository.save(existingProduct);

            // ✅ StockTracker ile stok değişikliğini takip et
            if (!Objects.equals(oldStock, newStock)) {
                stockTrackerService.trackExcelStockUpdate(
                        existingProduct, oldStock, newStock, "update", performedBy, fileName);
            }

            logger.info("Updated product with stock tracking: " + productData.getProductCode() +
                    " (Stock: " + oldStock + " -> " + newStock + ")");
            return true;

        } else {
            // ✅ YENİ OLUŞTURMA
            Integer initialStock = productData.getStockQuantity() != null ?
                    productData.getStockQuantity() : 0;

            // Yeni ürün oluştur
            Product newProduct = createProductFromExcelData(productData, category);
            newProduct.setStockQuantity(initialStock);
            Product savedProduct = productRepository.save(newProduct);

            // ✅ StockTracker ile başlangıç stokunu takip et
            if (initialStock > 0) {
                stockTrackerService.trackExcelStockUpdate(
                        savedProduct, 0, initialStock, "import", performedBy, fileName);
            }

            logger.info("Created new product with stock tracking: " + productData.getProductCode() +
                    " (Initial stock: " + initialStock + ")");
            return false;
        }
    }

    private boolean saveOrUpdateProduct(ExcelProductData productData, Category category,
                                        Map<String, Product> existingProductMap, boolean updateExisting) {
        // Mevcut ürün kontrolü
        Product existingProduct = existingProductMap.get(productData.getProductCode().toUpperCase());

        if (existingProduct != null) {
            if (!updateExisting) {
                throw new IllegalArgumentException("Ürün zaten mevcut - Kod: " + productData.getProductCode());
            }

            Integer oldStock = existingProduct.getStockQuantity();
            Integer newStock = productData.getStockQuantity() != null ?
                    productData.getStockQuantity() : oldStock;

            // Ürünü güncelle
            updateProductFromExcelData(existingProduct, productData, category);
            existingProduct.setStockQuantity(newStock); // Stok değerini set et
            // Güncelle
            updateProductFromExcelData(existingProduct, productData, category);
            productRepository.save(existingProduct);
            // ✅ StockTracker ile stok değişikliğini takip et
            if (!Objects.equals(oldStock, newStock)) {
                stockTrackerService.trackExcelStockUpdate(
                        existingProduct, oldStock, newStock, "update", getCurrentUser(), "fileName");
            }
            logger.info("Updated product: " + productData.getProductCode());
            return true;

        } else {
            Integer initialStock = productData.getStockQuantity() != null ?
                    productData.getStockQuantity() : 0;
            // Yeni oluştur
            Product newProduct = createProductFromExcelData(productData, category);
            Product savedProduct= productRepository.save(newProduct);
            // ✅ StockTracker ile başlangıç stokunu takip et
            if (initialStock > 0) {
                stockTrackerService.trackExcelStockUpdate(
                        savedProduct, 0, initialStock, "import", getCurrentUser(), "fileName");
            }
            logger.info("Created new product: " + productData.getProductCode());
            return false;
        }
    }

    private Product createProductFromExcelData(ExcelProductData data, Category category) {
        Product product = new Product();

        product.setCode(data.getProductCode());
        product.setName(data.getProductName());
        product.setDescription(data.getDescription());
        product.setCategory(category);
        product.setMaterial(data.getMaterial());
        product.setSize(data.getSize());
        product.setDiameter(data.getDiameter());
        product.setAngle(data.getAngle());
        product.setSterile(data.getSterile());
        product.setSingleUse(data.getSingleUse() != null ? data.getSingleUse() : true);
        product.setImplantable(data.getImplantable() != null ? data.getImplantable() : false);
        product.setCeMarking(data.getCeMarking() != null ? data.getCeMarking() : false);
        product.setFdaApproved(data.getFdaApproved() != null ? data.getFdaApproved() : false);
        product.setMedicalDeviceClass(data.getMedicalDeviceClass());
        product.setRegulatoryNumber(data.getRegulatoryNumber());
        product.setWeightGrams(data.getWeightGrams());
        product.setDimensions(data.getDimensions());
        product.setColor(data.getColor());
        product.setSurfaceTreatment(data.getSurfaceTreatment());
        product.setSerialNumber(data.getSerialNumber());
        product.setManufacturerCode(data.getManufacturerCode());
        product.setManufacturingDate(data.getManufacturingDate());
        product.setExpiryDate(data.getExpiryDate());
        product.setShelfLifeMonths(data.getShelfLifeMonths());
        product.setUnit(data.getUnit());
        product.setBarcode(data.getBarcode());
        product.setLotNumber(data.getLotNumber());
        product.setStockQuantity(data.getStockQuantity() != null ? data.getStockQuantity() : 0);
        product.setMinimumOrderQuantity(data.getMinimumOrderQuantity() != null ? data.getMinimumOrderQuantity() : 1);
        product.setMaximumOrderQuantity(data.getMaximumOrderQuantity() != null ? data.getMaximumOrderQuantity() : 1000);

        product.setStatus(EntityStatus.ACTIVE);

        return product;
    }

    private void updateProductFromExcelData(Product product, ExcelProductData data, Category category) {
        product.setName(data.getProductName());
        product.setDescription(data.getDescription());
        product.setCategory(category);
        product.setMaterial(data.getMaterial());
        product.setSize(data.getSize());
        product.setDiameter(data.getDiameter());
        product.setAngle(data.getAngle());

        if (data.getSterile() != null) product.setSterile(data.getSterile());
        if (data.getSingleUse() != null) product.setSingleUse(data.getSingleUse());
        if (data.getImplantable() != null) product.setImplantable(data.getImplantable());
        if (data.getCeMarking() != null) product.setCeMarking(data.getCeMarking());
        if (data.getFdaApproved() != null) product.setFdaApproved(data.getFdaApproved());

        product.setMedicalDeviceClass(data.getMedicalDeviceClass());
        product.setRegulatoryNumber(data.getRegulatoryNumber());
        product.setWeightGrams(data.getWeightGrams());
        product.setDimensions(data.getDimensions());
        product.setColor(data.getColor());
        product.setSurfaceTreatment(data.getSurfaceTreatment());
        product.setSerialNumber(data.getSerialNumber());
        product.setManufacturerCode(data.getManufacturerCode());
        product.setManufacturingDate(data.getManufacturingDate());
        product.setExpiryDate(data.getExpiryDate());
        product.setShelfLifeMonths(data.getShelfLifeMonths());
        product.setUnit(data.getUnit());
        product.setBarcode(data.getBarcode());
        product.setLotNumber(data.getLotNumber());

        if (data.getStockQuantity() != null) product.setStockQuantity(data.getStockQuantity());
        if (data.getMinimumOrderQuantity() != null) product.setMinimumOrderQuantity(data.getMinimumOrderQuantity());
        if (data.getMaximumOrderQuantity() != null) product.setMaximumOrderQuantity(data.getMaximumOrderQuantity());
    }

    // ==================== UTILITY METHODS ====================

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                } else {
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

    private Integer getCellValueAsInteger(Cell cell) {
        if (cell == null) return null;

        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (int) cell.getNumericCellValue();
                case STRING -> {
                    String value = cell.getStringCellValue().trim();
                    if (value.isEmpty()) yield null;
                    yield Integer.parseInt(value);
                }
                case FORMULA -> (int) cell.getNumericCellValue();
                default -> null;
            };
        } catch (Exception e) {
            logger.warning("Could not parse cell value as Integer: " + e.getMessage());
            return null;
        }
    }

    private LocalDate getCellValueAsLocalDate(Cell cell) {
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            } else if (cell.getCellType() == CellType.STRING) {
                String dateStr = cell.getStringCellValue().trim();
                if (dateStr.isEmpty()) return null;

                DateTimeFormatter[] formatters = {
                        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")
                };

                for (DateTimeFormatter formatter : formatters) {
                    try {
                        return LocalDate.parse(dateStr, formatter);
                    } catch (DateTimeParseException ignored) {
                        // Sonraki formatı dene
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Could not parse cell value as LocalDate: " + e.getMessage());
        }

        return null;
    }

    private Boolean getCellValueAsBoolean(Cell cell) {
        if (cell == null) return null;

        String value = getCellValueAsString(cell).toUpperCase();
        return switch (value) {
            case "EVET", "TRUE", "1", "AKTIF", "YES", "E", "T" -> true;
            case "HAYIR", "FALSE", "0", "PASIF", "NO", "H", "F" -> false;
            default -> null;
        };
    }

    private String getRowDataAsString(Row row) {
        if (row == null) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            Cell cell = row.getCell(i);
            if (i > 0) sb.append(" | ");
            sb.append(getCellValueAsString(cell));
        }
        return sb.toString();
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < 30; i++) {
            sheet.autoSizeColumn(i);
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
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
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

package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.productExcel.ProductImportResult;
import com.maxx_global.service.ProductExcelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.logging.Logger;

@RestController
@RequestMapping("/api/products/excel")
@Validated
@Tag(name = "Product Excel Operations", description = "Ürün bilgileri için Excel import/export işlemleri")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductExcelController {

    private static final Logger logger = Logger.getLogger(ProductExcelController.class.getName());
    private final ProductExcelService excelService;

    public ProductExcelController(ProductExcelService excelService) {
        this.excelService = excelService;
    }

    @GetMapping("/template")
    @Operation(
            summary = "Ürün import şablonu indir",
            description = "Ürün bilgilerini toplu olarak eklemek için boş Excel şablonunu indirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Excel şablonu başarıyla oluşturuldu"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null,'PRODUCT_CREATE') or  hasPermission(null,'PRODUCT_UPDATE')")
    public ResponseEntity<Resource> downloadProductTemplate() {
        try {
            logger.info("Generating product import template");

            byte[] excelData = excelService.generateProductTemplate();

            ByteArrayResource resource = new ByteArrayResource(excelData);

            String filename = "urun_import_sablonu.xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(excelData.length)
                    .body(resource);

        } catch (Exception e) {
            logger.severe("Error generating product template: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/export")
    @Operation(
            summary = "Ürünleri Excel'e aktar",
            description = "Mevcut ürün bilgilerini Excel formatında indirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ürünler başarıyla aktarıldı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<Resource> exportProducts(
            @Parameter(description = "Kategori ID'si (opsiyonel)", example = "1")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Sadece aktif ürünler", example = "true")
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @Parameter(description = "Sadece stokta olanlar", example = "false")
            @RequestParam(defaultValue = "false") boolean inStockOnly) {

        try {
            logger.info("Exporting products - categoryId: " + categoryId +
                    ", activeOnly: " + activeOnly + ", inStockOnly: " + inStockOnly);

            byte[] excelData = excelService.exportProducts(categoryId, activeOnly, inStockOnly);

            ByteArrayResource resource = new ByteArrayResource(excelData);

            String filename = "urunler_" + System.currentTimeMillis() + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(excelData.length)
                    .body(resource);

        } catch (Exception e) {
            logger.severe("Error exporting products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/export/category/{categoryId}")
    @Operation(
            summary = "Kategoriye göre ürünleri Excel'e aktar",
            description = "Belirtilen kategorideki ürünleri Excel formatında indirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kategori ürünleri başarıyla aktarıldı"),
            @ApiResponse(responseCode = "404", description = "Kategori bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<Resource> exportProductsByCategory(
            @Parameter(description = "Kategori ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long categoryId,
            @Parameter(description = "Sadece aktif ürünler", example = "true")
            @RequestParam(defaultValue = "true") boolean activeOnly) {

        try {
            logger.info("Exporting products by category: " + categoryId + ", activeOnly: " + activeOnly);

            byte[] excelData = excelService.exportProducts(categoryId, activeOnly, false);

            ByteArrayResource resource = new ByteArrayResource(excelData);

            String filename = "kategori_" + categoryId + "_urunler.xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(excelData.length)
                    .body(resource);

        } catch (EntityNotFoundException e) {
            logger.warning("Category not found: " + categoryId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.severe("Error exporting products by category: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/import")
    @Operation(
            summary = "Excel'den ürün al",
            description = "Excel dosyasından ürün bilgilerini sisteme aktarır"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ürünler başarıyla aktarıldı"),
            @ApiResponse(responseCode = "400", description = "Geçersiz Excel dosyası veya veri"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null,'PRODUCT_CREATE') or hasPermission(null,'PRODUCT_UPDATE')")
    public ResponseEntity<BaseResponse<ProductImportResult>> importProductsFromExcel(
            @Parameter(description = "Excel dosyası", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Mevcut ürünleri güncelle", example = "true")
            @RequestParam(defaultValue = "true") boolean updateExisting,
            @Parameter(description = "Hatalı satırları atla", example = "false")
            @RequestParam(defaultValue = "false") boolean skipErrors) {

        try {
            logger.info("Importing products from Excel - updateExisting: " + updateExisting +
                    ", skipErrors: " + skipErrors);

            // Dosya validasyonu
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(BaseResponse.error("Excel dosyası boş", HttpStatus.BAD_REQUEST.value()));
            }

            if (!isValidExcelFile(file)) {
                return ResponseEntity.badRequest()
                        .body(BaseResponse.error("Geçersiz dosya formatı. Sadece .xlsx dosyaları kabul edilir",
                                HttpStatus.BAD_REQUEST.value()));
            }

            ProductImportResult result = excelService.importProductsFromExcel(file, updateExisting, skipErrors);

            logger.info("Import completed - Total: " + result.totalRows() +
                    ", Success: " + result.successCount() +
                    ", Failed: " + result.failedCount());

            return ResponseEntity.ok(BaseResponse.success(result));

        } catch (IllegalArgumentException e) {
            logger.warning("Validation error during Excel import: " + e.getMessage());
            return ResponseEntity.badRequest()
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (RuntimeException e) {
            // RuntimeException içindeki gerçek mesajı çıkar
            String errorMessage = e.getMessage();

            // "Excel import hatası: " prefix'ini kaldır
            if (errorMessage != null && errorMessage.startsWith("Excel import hatası: ")) {
                errorMessage = errorMessage.substring("Excel import hatası: ".length());
            }

            // İç içe exception mesajlarını kontrol et
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warning("Validation error during Excel import: " + cause.getMessage());
                return ResponseEntity.badRequest()
                        .body(BaseResponse.error(cause.getMessage(), HttpStatus.BAD_REQUEST.value()));
            }

            logger.severe("Error importing products from Excel: " + errorMessage);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR.value()));

        } catch (Exception e) {
            logger.severe("Error importing products from Excel: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Excel import sırasında beklenmeyen hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/validate")
    @Operation(
            summary = "Excel dosyasını doğrula",
            description = "Excel dosyasını gerçek import yapmadan doğrular"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Doğrulama tamamlandı"),
            @ApiResponse(responseCode = "400", description = "Geçersiz Excel dosyası"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null,'PRODUCT_CREATE') or hasPermission(null,'PRODUCT_UPDATE')")
    public ResponseEntity<BaseResponse<ProductImportResult>> validateProductExcel(
            @Parameter(description = "Excel dosyası", required = true)
            @RequestParam("file") MultipartFile file) {

        try {
            logger.info("Validating product Excel file");

            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(BaseResponse.error("Excel dosyası boş", HttpStatus.BAD_REQUEST.value()));
            }

            if (!isValidExcelFile(file)) {
                return ResponseEntity.badRequest()
                        .body(BaseResponse.error("Geçersiz dosya formatı", HttpStatus.BAD_REQUEST.value()));
            }

            ProductImportResult result = excelService.validateProductExcel(file);
            return ResponseEntity.ok(BaseResponse.success(result));

        } catch (Exception e) {
            logger.severe("Error validating product Excel: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Excel doğrulama sırasında hata: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Excel dosya formatı validasyonu
     */
    private boolean isValidExcelFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return originalFilename != null &&
                (originalFilename.toLowerCase().endsWith(".xlsx") ||
                        originalFilename.toLowerCase().endsWith(".xls"));
    }
}
package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.productPriceExcell.PriceImportResult;
import com.maxx_global.service.ProductPriceExcelService;
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
@RequestMapping("/api/product-prices/excel")
@Validated
@Tag(name = "Product Price Excel Operations", description = "Ürün fiyatları için Excel import/export işlemleri")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductPriceExcelController {

    private static final Logger logger = Logger.getLogger(ProductPriceExcelController.class.getName());
    private final ProductPriceExcelService excelService;

    public ProductPriceExcelController(ProductPriceExcelService excelService) {
        this.excelService = excelService;
    }

    @GetMapping("/template/{dealerId}")
    @Operation(
            summary = "Bayi için fiyat şablonu indir",
            description = "Belirtilen bayi için tüm ürünleri içeren boş Excel şablonunu indirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Excel şablonu başarıyla oluşturuldu"),
            @ApiResponse(responseCode = "404", description = "Bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_CREATE') or hasAuthority('PRICE_UPDATE')")
    public ResponseEntity<Resource> downloadPriceTemplate(
            @Parameter(description = "Bayi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long dealerId) {

        try {
            logger.info("Generating price template for dealer: " + dealerId);

            byte[] excelData = excelService.generatePriceTemplate(dealerId);

            ByteArrayResource resource = new ByteArrayResource(excelData);

            String filename = "fiyat_sablonu_bayi_" + dealerId + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(excelData.length)
                    .body(resource);

        } catch (EntityNotFoundException e) {
            logger.warning("Dealer not found: " + dealerId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.severe("Error generating Excel template: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/export/{dealerId}")
    @Operation(
            summary = "Bayi fiyatlarını Excel'e aktar",
            description = "Belirtilen bayinin mevcut fiyatlarını Excel formatında indirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fiyatlar başarıyla aktarıldı"),
            @ApiResponse(responseCode = "404", description = "Bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_READ')")
    public ResponseEntity<Resource> exportDealerPrices(
            @Parameter(description = "Bayi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long dealerId,
            @Parameter(description = "Sadece aktif fiyatlar", example = "true")
            @RequestParam(defaultValue = "true") boolean activeOnly) {

        try {
            logger.info("Exporting prices for dealer: " + dealerId + ", activeOnly: " + activeOnly);

            byte[] excelData = excelService.exportDealerPrices(dealerId, activeOnly);

            ByteArrayResource resource = new ByteArrayResource(excelData);

            String filename = "bayi_fiyatlari_" + dealerId + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(excelData.length)
                    .body(resource);

        } catch (EntityNotFoundException e) {
            logger.warning("Dealer not found: " + dealerId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.severe("Error exporting dealer prices: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/import/{dealerId}")
    @Operation(
            summary = "Excel'den fiyat al",
            description = "Excel dosyasından bayi fiyatlarını sisteme aktarır"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fiyatlar başarıyla aktarıldı"),
            @ApiResponse(responseCode = "400", description = "Geçersiz Excel dosyası veya veri"),
            @ApiResponse(responseCode = "404", description = "Bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_CREATE') or hasAuthority('PRICE_UPDATE')")
    public ResponseEntity<BaseResponse<PriceImportResult>> importPricesFromExcel(
            @Parameter(description = "Bayi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long dealerId,
            @Parameter(description = "Excel dosyası", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Mevcut fiyatları güncelle", example = "true")
            @RequestParam(defaultValue = "true") boolean updateExisting,
            @Parameter(description = "Hatalı satırları atla", example = "false")
            @RequestParam(defaultValue = "false") boolean skipErrors) {

        try {
            logger.info("Importing prices from Excel for dealer: " + dealerId);

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

            PriceImportResult result = excelService.importPricesFromExcel(
                    dealerId, file, updateExisting, skipErrors);

            logger.info("Import completed - Total: " + result.totalRows() +
                    ", Success: " + result.successCount() +
                    ", Failed: " + result.failedCount());

            return ResponseEntity.ok(BaseResponse.success(result));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error importing prices from Excel: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Excel import sırasında hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/import-template")
    @Operation(
            summary = "Import şablonu indir",
            description = "Fiyat import işlemi için örnek Excel şablonunu indirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Şablon başarıyla oluşturuldu"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_READ')")
    public ResponseEntity<Resource> downloadImportTemplate() {
        try {
            logger.info("Generating import template");

            byte[] excelData = excelService.generateImportTemplate();

            ByteArrayResource resource = new ByteArrayResource(excelData);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fiyat_import_sablonu.xlsx\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(excelData.length)
                    .body(resource);

        } catch (Exception e) {
            logger.severe("Error generating import template: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.stock.*;
import com.maxx_global.service.StockTrackerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/stock")
@Validated
@Tag(name = "Stock Movement Reports", description = "Stok hareket raporları için API endpoint'leri. Otomatik oluşturulan stok hareketlerinin görüntülenmesi ve analizi.")
@SecurityRequirement(name = "Bearer Authentication")
public class StockMovementController {

    private static final Logger logger = Logger.getLogger(StockMovementController.class.getName());

    private final StockTrackerService stockMovementService;

    public StockMovementController(StockTrackerService stockMovementService) {
        this.stockMovementService = stockMovementService;
    }

    // ==================== STOCK MOVEMENT LISTING OPERATIONS ====================

    @GetMapping("/movements")
    @Operation(
            summary = "Tüm stok hareketlerini listele",
            description = "Sistem tarafından otomatik oluşturulan tüm stok hareketlerini sayfalama ve filtreleme ile listeler"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stok hareketleri başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'STOCK_READ')")
    public ResponseEntity<BaseResponse<Page<StockMovementResponse>>> getAllStockMovements(
            @Parameter(description = "Sayfa numarası (0'dan başlar)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sıralama alanı", example = "movementDate")
            @RequestParam(defaultValue = "movementDate") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDirection,
            @Parameter(description = "Hareket tipi filtresi", example = "STOCK_IN")
            @RequestParam(required = false) String movementType,
            @Parameter(description = "Ürün ID filtresi", example = "1")
            @RequestParam(required = false) Long productId,
            @Parameter(description = "Başlangıç tarihi (yyyy-MM-dd)", example = "2024-01-01")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "Bitiş tarihi (yyyy-MM-dd)", example = "2024-12-31")
            @RequestParam(required = false) String endDate) {

        try {
            logger.info("Fetching stock movements - page: " + page + ", size: " + size +
                    ", movementType: " + movementType + ", productId: " + productId);

            Page<StockMovementResponse> movements = stockMovementService.getAllStockMovements(
                    page, size, sortBy, sortDirection, movementType, productId, startDate, endDate);

            return ResponseEntity.ok(BaseResponse.success(movements));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error fetching stock movements: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Stok hareketleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/movements/{id}")
    @Operation(
            summary = "Stok hareketi detayını getir",
            description = "Belirtilen ID'ye sahip stok hareketinin detay bilgilerini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stok hareketi detayı başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Stok hareketi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'STOCK_READ')")
    public ResponseEntity<BaseResponse<StockMovementResponse>> getStockMovementById(
            @Parameter(description = "Stok hareketi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {

        try {
            logger.info("Fetching stock movement details for id: " + id);

            StockMovementResponse movement = stockMovementService.getStockMovementById(id);
            return ResponseEntity.ok(BaseResponse.success(movement));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching stock movement details: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Stok hareketi detayı getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/movements/product/{productId}")
    @Operation(
            summary = "Ürüne göre stok hareketlerini listele",
            description = "Belirtilen ürün için tüm stok hareketlerini kronolojik sıraya göre listeler"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ürün stok hareketleri başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'STOCK_READ')")
    public ResponseEntity<BaseResponse<Page<StockMovementResponse>>> getStockMovementsByProduct(
            @Parameter(description = "Ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long productId,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sıralama yönü", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDirection) {

        try {
            logger.info("Fetching stock movements for product: " + productId);

            Page<StockMovementResponse> movements = stockMovementService.getStockMovementsByProduct(
                    productId, page, size, sortDirection);

            return ResponseEntity.ok(BaseResponse.success(movements));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching product stock movements: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürün stok hareketleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/movements/recent")
    @Operation(
            summary = "Son stok hareketlerini getir",
            description = "En son gerçekleşen stok hareketlerini listeler (hızlı erişim için)"
    )
    @PreAuthorize("hasPermission(null, 'STOCK_READ')")
    public ResponseEntity<BaseResponse<List<StockMovementResponse>>> getRecentStockMovements(
            @Parameter(description = "Getirilecek hareket sayısı", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {

        try {
            logger.info("Fetching recent stock movements, limit: " + limit);

            List<StockMovementResponse> movements = stockMovementService.getRecentStockMovements(limit);
            return ResponseEntity.ok(BaseResponse.success(movements));

        } catch (Exception e) {
            logger.severe("Error fetching recent stock movements: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Son stok hareketleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ==================== STOCK SUMMARY AND REPORTS ====================

    @GetMapping("/summary/product/{productId}")
    @Operation(
            summary = "Ürün stok özetini getir",
            description = "Belirtilen ürün için toplam giriş, çıkış, mevcut stok ve değer bilgilerini getirir"
    )
    @PreAuthorize("hasPermission(null, 'STOCK_READ')")
    public ResponseEntity<BaseResponse<StockSummaryResponse>> getProductStockSummary(
            @Parameter(description = "Ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long productId) {

        try {
            logger.info("Fetching stock summary for product: " + productId);

            StockSummaryResponse summary = stockMovementService.getProductStockSummary(productId);
            return ResponseEntity.ok(BaseResponse.success(summary));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching product stock summary: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürün stok özeti getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/summary/all")
    @Operation(
            summary = "Tüm ürünler stok özeti",
            description = "Tüm ürünler için stok özet bilgilerini sayfalama ile getirir"
    )
    @PreAuthorize("hasPermission(null, 'STOCK_READ')")
    public ResponseEntity<BaseResponse<Page<StockSummaryResponse>>> getAllProductsStockSummary(
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sıralama alanı", example = "currentStock")
            @RequestParam(defaultValue = "currentStock") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDirection) {

        try {
            logger.info("Fetching stock summary for all products");

            Page<StockSummaryResponse> summaries = stockMovementService.getAllProductsStockSummary(
                    page, size, sortBy, sortDirection);

            return ResponseEntity.ok(BaseResponse.success(summaries));

        } catch (Exception e) {
            logger.severe("Error fetching all products stock summary: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Tüm ürünler stok özeti getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ==================== STOCK MOVEMENT ANALYTICS ====================

    @GetMapping("/analytics/movement-types")
    @Operation(
            summary = "Hareket tipi istatistikleri",
            description = "Belirtilen tarih aralığında hareket tiplerinin dağılımını getirir"
    )
    @PreAuthorize("hasPermission(null, 'STOCK_READ')")
    public ResponseEntity<BaseResponse<Object>> getMovementTypeStatistics(
            @Parameter(description = "Başlangıç tarihi (yyyy-MM-dd)", example = "2024-01-01")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "Bitiş tarihi (yyyy-MM-dd)", example = "2024-12-31")
            @RequestParam(required = false) String endDate) {

        try {
            logger.info("Fetching movement type statistics for period: " + startDate + " to " + endDate);

            Object statistics = stockMovementService.getMovementTypeStatistics(startDate, endDate);
            return ResponseEntity.ok(BaseResponse.success(statistics));

        } catch (Exception e) {
            logger.severe("Error fetching movement type statistics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Hareket tipi istatistikleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/analytics/daily-summary")
    @Operation(
            summary = "Günlük stok hareket özeti",
            description = "Belirtilen tarih için günlük stok hareket özetini getirir"
    )
    @PreAuthorize("hasPermission(null, 'STOCK_READ')")
    public ResponseEntity<BaseResponse<Object>> getDailyStockSummary(
            @Parameter(description = "Rapor tarihi (yyyy-MM-dd)", example = "2024-01-15")
            @RequestParam(required = false) String reportDate) {

        try {
            logger.info("Fetching daily stock summary for date: " + reportDate);

            Object dailySummary = stockMovementService.getDailyStockSummary(reportDate);
            return ResponseEntity.ok(BaseResponse.success(dailySummary));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error fetching daily stock summary: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Günlük stok özeti getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/analytics/top-movements")
    @Operation(
            summary = "En çok hareket gören ürünler",
            description = "Belirtilen tarih aralığında en çok stok hareketi olan ürünleri getirir"
    )
    @PreAuthorize("hasPermission(null, 'STOCK_READ')")
    public ResponseEntity<BaseResponse<List<Object>>> getTopMovementProducts(
            @Parameter(description = "Başlangıç tarihi (yyyy-MM-dd)", example = "2024-01-01")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "Bitiş tarihi (yyyy-MM-dd)", example = "2024-12-31")
            @RequestParam(required = false) String endDate,
            @Parameter(description = "Listeleme limiti", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {

        try {
            logger.info("Fetching top movement products for period: " + startDate + " to " + endDate + ", limit: " + limit);

            List<Map<String, Object>> topProducts = stockMovementService.getTopMovementProducts(startDate, endDate, limit);
            return ResponseEntity.ok(BaseResponse.success(Collections.singletonList(topProducts)));

        } catch (Exception e) {
            logger.severe("Error fetching top movement products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("En çok hareket gören ürünler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ==================== STOCK MOVEMENT SEARCH ====================

    @GetMapping("/movements/search")
    @Operation(
            summary = "Stok hareketlerinde arama",
            description = "Ürün adı, kodu, referans numarası veya notlarda arama yapar"
    )
    @PreAuthorize("hasPermission(null, 'STOCK_READ')")
    public ResponseEntity<BaseResponse<Page<StockMovementResponse>>> searchStockMovements(
            @Parameter(description = "Arama terimi", example = "TI-001", required = true)
            @RequestParam String searchTerm,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sıralama yönü", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDirection) {

        try {
            logger.info("Searching stock movements with term: " + searchTerm);

            Page<StockMovementResponse> movements = stockMovementService.searchStockMovements(
                    searchTerm, page, size, sortDirection);

            return ResponseEntity.ok(BaseResponse.success(movements));

        } catch (Exception e) {
            logger.severe("Error searching stock movements: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Stok hareketi arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ==================== REFERENCE BASED QUERIES ====================

    @GetMapping("/movements/reference/{referenceType}/{referenceId}")
    @Operation(
            summary = "Referansa göre stok hareketleri",
            description = "Belirtilen referans tipi ve ID'ye göre stok hareketlerini getirir (örn: sipariş, transfer)"
    )
    @PreAuthorize("hasPermission(null, 'STOCK_READ')")
    public ResponseEntity<BaseResponse<List<StockMovementResponse>>> getStockMovementsByReference(
            @Parameter(description = "Referans tipi", example = "ORDER", required = true)
            @PathVariable String referenceType,
            @Parameter(description = "Referans ID'si", example = "123", required = true)
            @PathVariable @Min(1) Long referenceId) {

        try {
            logger.info("Fetching stock movements by reference: " + referenceType + "/" + referenceId);

            List<StockMovementResponse> movements = stockMovementService.getStockMovementsByReference(
                    referenceType, referenceId);

            return ResponseEntity.ok(BaseResponse.success(movements));

        } catch (Exception e) {
            logger.severe("Error fetching stock movements by reference: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Referans bazlı stok hareketleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ==================== STOCK COUNT OPERATIONS ====================

    @PostMapping("/count")
    @Operation(
            summary = "Stok sayımı kaydet",
            description = "Manuel stok sayımı sonucunu kaydeder ve fark varsa düzeltme hareketi oluşturur"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stok sayımı başarıyla kaydedildi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz sayım verileri"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'STOCK_COUNT')")
    public ResponseEntity<BaseResponse<StockMovementResponse>> recordStockCount(
            @Parameter(description = "Stok sayım bilgileri", required = true)
            @Valid @RequestBody StockCountRequest request,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Recording stock count for product: " + request.productId() +
                    ", counted quantity: " + request.countedQuantity());

            StockMovementResponse movement = stockMovementService.recordStockCount(request);
            return ResponseEntity.ok(BaseResponse.success(movement));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error recording stock count: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Stok sayımı kaydedilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}
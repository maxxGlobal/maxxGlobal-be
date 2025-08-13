package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.productPrice.*;
import com.maxx_global.service.ProductPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/product-prices")
@Validated
@Tag(name = "Product Price Management", description = "Ürün fiyat yönetimi için API endpoint'leri. Bayi bazlı fiyatlandırma sistemini destekler.")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductPriceController {

    private static final Logger logger = Logger.getLogger(ProductPriceController.class.getName());
    private final ProductPriceService productPriceService;

    public ProductPriceController(ProductPriceService productPriceService) {
        this.productPriceService = productPriceService;
    }

    @GetMapping
    @Operation(
            summary = "Tüm fiyatları listele",
            description = "Sayfalama ve sıralama ile tüm ürün fiyatlarını getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fiyatlar başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_READ')")
    public ResponseEntity<BaseResponse<Page<ProductPriceResponse>>> getAllPrices(
            @Parameter(description = "Sayfa numarası (0'dan başlar)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "amount")
            @RequestParam(defaultValue = "amount") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            Page<ProductPriceResponse> prices = productPriceService.getAllPrices(page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(prices));

        } catch (Exception e) {
            logger.severe("Error fetching prices: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Fiyatlar getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "ID ile fiyat getir",
            description = "Belirtilen ID'ye sahip fiyatın detay bilgilerini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fiyat başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Fiyat bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_READ')")
    public ResponseEntity<BaseResponse<ProductPriceResponse>> getPriceById(
            @Parameter(description = "Fiyat ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {
        try {
            ProductPriceResponse price = productPriceService.getPriceById(id);
            return ResponseEntity.ok(BaseResponse.success(price));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching price by id: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Fiyat getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/dealer/{dealerId}")
    @Operation(
            summary = "Bayiye göre fiyatları listele",
            description = "Belirtilen bayinin tüm ürün fiyatlarını getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bayi fiyatları başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_READ')")
    public ResponseEntity<BaseResponse<Page<ProductPriceResponse>>> getPricesByDealer(
            @Parameter(description = "Bayi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long dealerId,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "amount")
            @RequestParam(defaultValue = "amount") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection,
            @Parameter(description = "Sadece aktif fiyatlar", example = "false")
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        try {
            Page<ProductPriceResponse> prices = productPriceService.getPricesByDealer(
                    dealerId, page, size, sortBy, sortDirection, activeOnly);
            return ResponseEntity.ok(BaseResponse.success(prices));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching prices by dealer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi fiyatları getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/product/{productId}/comparison")
    @Operation(
            summary = "Ürün fiyat karşılaştırması",
            description = "Belirtilen ürünün bayiler arası fiyat karşılaştırmasını getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fiyat karşılaştırması başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı veya fiyat yok"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_READ')")
    public ResponseEntity<BaseResponse<DealerPriceComparison>> getProductPriceComparison(
            @Parameter(description = "Ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long productId,
            @Parameter(description = "Para birimi", example = "TRY", required = true)
            @RequestParam String currency,
            @Parameter(description = "Fiyat tipi", example = "PRICE_1", required = true)
            @RequestParam String priceType) {

        try {
            DealerPriceComparison comparison = productPriceService.getProductPriceComparison(
                    productId, currency, priceType);
            return ResponseEntity.ok(BaseResponse.success(comparison));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error getting price comparison: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Fiyat karşılaştırması getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/dealer/{dealerId}/search")
    @Operation(
            summary = "Bayi fiyatlarında arama",
            description = "Bayinin fiyatlarında ürün adı ve koduna göre arama yapar"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Arama başarılı"),
            @ApiResponse(responseCode = "404", description = "Bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_READ')")
    public ResponseEntity<BaseResponse<Page<ProductPriceResponse>>> searchPrices(
            @Parameter(description = "Bayi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long dealerId,
            @Parameter(description = "Arama terimi (ürün adı veya kodu)", example = "implant", required = true)
            @RequestParam String q,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size) {

        try {
            Page<ProductPriceResponse> prices = productPriceService.searchPrices(dealerId, q, page, size);
            return ResponseEntity.ok(BaseResponse.success(prices));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error searching prices: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Fiyat arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping
    @Operation(
            summary = "Yeni fiyat oluştur",
            description = "Belirtilen ürün ve bayi için yeni bir fiyat oluşturur"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Fiyat başarıyla oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri veya fiyat zaten mevcut"),
            @ApiResponse(responseCode = "404", description = "Ürün veya bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_CREATE')")
    public ResponseEntity<BaseResponse<ProductPriceResponse>> createPrice(
            @Parameter(description = "Yeni fiyat bilgileri", required = true)
            @Valid @RequestBody ProductPriceRequest request) {
        try {
            ProductPriceResponse price = productPriceService.createPrice(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(price));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (BadCredentialsException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error creating price: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Fiyat oluşturulurken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Fiyat güncelle",
            description = "Mevcut bir fiyatın bilgilerini günceller"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fiyat başarıyla güncellendi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri"),
            @ApiResponse(responseCode = "404", description = "Fiyat bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_UPDATE')")
    public ResponseEntity<BaseResponse<ProductPriceResponse>> updatePrice(
            @Parameter(description = "Fiyat ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id,
            @Parameter(description = "Güncellenmiş fiyat bilgileri", required = true)
            @Valid @RequestBody ProductPriceRequest request) {

        try {
            ProductPriceResponse price = productPriceService.updatePrice(id, request);
            return ResponseEntity.ok(BaseResponse.success(price));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (BadCredentialsException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error updating price: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Fiyat güncellenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/bulk-update")
    @Operation(
            summary = "Toplu fiyat güncelleme",
            description = "Birden fazla fiyatı aynı anda günceller. Yüzde artış/azalış veya sabit fiyat belirlenebilir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fiyatlar başarıyla güncellendi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri"),
            @ApiResponse(responseCode = "404", description = "Bazı fiyatlar bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_UPDATE')")
    public ResponseEntity<BaseResponse<List<ProductPriceResponse>>> bulkUpdatePrices(
            @Parameter(description = "Toplu güncelleme bilgileri", required = true)
            @Valid @RequestBody BulkPriceUpdateRequest request) {

        try {
            List<ProductPriceResponse> prices = productPriceService.bulkUpdatePrices(request);
            return ResponseEntity.ok(BaseResponse.success(prices));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error bulk updating prices: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Toplu fiyat güncelleme sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Fiyat sil",
            description = "Belirtilen fiyatı siler (soft delete)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fiyat başarıyla silindi"),
            @ApiResponse(responseCode = "404", description = "Fiyat bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_DELETE')")
    public ResponseEntity<BaseResponse<Void>> deletePrice(
            @Parameter(description = "Silinecek fiyat ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {
        try {
            productPriceService.deletePrice(id);
            return ResponseEntity.ok(BaseResponse.success(null));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error deleting price: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Fiyat silinirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/expired")
    @Operation(
            summary = "Süresi dolan fiyatları listele",
            description = "Geçerlilik tarihi dolmuş fiyatları getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Süresi dolan fiyatlar başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRICE_READ')")
    public ResponseEntity<BaseResponse<List<ProductPriceResponse>>> getExpiredPrices() {
        try {
            List<ProductPriceResponse> expiredPrices = productPriceService.getExpiredPrices();
            return ResponseEntity.ok(BaseResponse.success(expiredPrices));

        } catch (Exception e) {
            logger.severe("Error fetching expired prices: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Süresi dolan fiyatlar getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}
package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.discount.DiscountCalculationRequest;
import com.maxx_global.dto.discount.DiscountCalculationResponse;
import com.maxx_global.dto.discount.DiscountRequest;
import com.maxx_global.dto.discount.DiscountResponse;
import com.maxx_global.service.DiscountService;
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
@RequestMapping("/api/discounts")
@Validated
@Tag(name = "Discount Management", description = "İndirim yönetimi için API endpoint'leri. Ürün ve bayi bazlı indirim sistemini destekler.")
@SecurityRequirement(name = "Bearer Authentication")
public class DiscountController {

    private static final Logger logger = Logger.getLogger(DiscountController.class.getName());
    private final DiscountService discountService;

    public DiscountController(DiscountService discountService) {
        this.discountService = discountService;
    }

    @GetMapping
    @Operation(
            summary = "Tüm indirimleri listele",
            description = "Sayfalama ve sıralama ile tüm indirimleri getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "İndirimler başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('DISCOUNT_READ')")
    public ResponseEntity<BaseResponse<Page<DiscountResponse>>> getAllDiscounts(
            @Parameter(description = "Sayfa numarası (0'dan başlar)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            Page<DiscountResponse> discounts = discountService.getAllDiscounts(page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(discounts));

        } catch (Exception e) {
            logger.severe("Error fetching discounts: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("İndirimler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/active")
    @Operation(
            summary = "Aktif indirimleri listele",
            description = "Sadece geçerli durumda olan indirimleri getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Aktif indirimler başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('DISCOUNT_READ')")
    public ResponseEntity<BaseResponse<List<DiscountResponse>>> getActiveDiscounts() {
        try {
            List<DiscountResponse> discounts = discountService.getActiveDiscounts();
            return ResponseEntity.ok(BaseResponse.success(discounts));

        } catch (Exception e) {
            logger.severe("Error fetching active discounts: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Aktif indirimler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "ID ile indirim getir",
            description = "Belirtilen ID'ye sahip indirimin detay bilgilerini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "İndirim başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "İndirim bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('DISCOUNT_READ')")
    public ResponseEntity<BaseResponse<DiscountResponse>> getDiscountById(
            @Parameter(description = "İndirim ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {
        try {
            DiscountResponse discount = discountService.getDiscountById(id);
            return ResponseEntity.ok(BaseResponse.success(discount));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching discount by id: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("İndirim getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/product/{productId}")
    @Operation(
            summary = "Ürüne uygulanabilir indirimleri listele",
            description = "Belirtilen ürün için geçerli indirimleri getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ürün indirimleri başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('DISCOUNT_READ')")
    public ResponseEntity<BaseResponse<List<DiscountResponse>>> getDiscountsForProduct(
            @Parameter(description = "Ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long productId,
            @Parameter(description = "Bayi ID'si (opsiyonel)", example = "1")
            @RequestParam(required = false) Long dealerId) {

        try {
            List<DiscountResponse> discounts = discountService.getDiscountsForProduct(productId, dealerId);
            return ResponseEntity.ok(BaseResponse.success(discounts));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching discounts for product: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürün indirimleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/dealer/{dealerId}")
    @Operation(
            summary = "Bayiye uygulanabilir indirimleri listele",
            description = "Belirtilen bayi için geçerli indirimleri getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bayi indirimleri başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('DISCOUNT_READ')")
    public ResponseEntity<BaseResponse<List<DiscountResponse>>> getDiscountsForDealer(
            @Parameter(description = "Bayi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long dealerId) {

        try {
            List<DiscountResponse> discounts = discountService.getDiscountsForDealer(dealerId);
            return ResponseEntity.ok(BaseResponse.success(discounts));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching discounts for dealer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi indirimleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/search")
    @Operation(
            summary = "İndirim arama",
            description = "İndirim adında arama yapar. Kısmi eşleşmeleri destekler."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Arama başarılı"),
            @ApiResponse(responseCode = "400", description = "Geçersiz arama parametresi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('DISCOUNT_READ')")
    public ResponseEntity<BaseResponse<Page<DiscountResponse>>> searchDiscounts(
            @Parameter(description = "Arama terimi (minimum 2 karakter)", example = "kış", required = true)
            @RequestParam String q,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            Page<DiscountResponse> discounts = discountService.searchDiscounts(q, page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(discounts));

        } catch (Exception e) {
            logger.severe("Error searching discounts: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("İndirim arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping
    @Operation(
            summary = "Yeni indirim oluştur",
            description = "Yeni bir indirim kaydı oluşturur. Ürün ve/veya bayi bazlı indirim yapılabilir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "İndirim başarıyla oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri"),
            @ApiResponse(responseCode = "404", description = "Ürün veya bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('DISCOUNT_CREATE')")
    public ResponseEntity<BaseResponse<DiscountResponse>> createDiscount(
            @Parameter(description = "Yeni indirim bilgileri", required = true)
            @Valid @RequestBody DiscountRequest request) {

        try {
            logger.info("Creating new discount: " + request.name());
            DiscountResponse discount = discountService.createDiscount(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(discount));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (BadCredentialsException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error creating discount: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("İndirim oluşturulurken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "İndirim güncelle",
            description = "Mevcut bir indirimin bilgilerini günceller"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "İndirim başarıyla güncellendi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri"),
            @ApiResponse(responseCode = "404", description = "İndirim bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('DISCOUNT_UPDATE')")
    public ResponseEntity<BaseResponse<DiscountResponse>> updateDiscount(
            @Parameter(description = "İndirim ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id,
            @Parameter(description = "Güncellenmiş indirim bilgileri", required = true)
            @Valid @RequestBody DiscountRequest request) {

        try {
            DiscountResponse discount = discountService.updateDiscount(id, request);
            return ResponseEntity.ok(BaseResponse.success(discount));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (BadCredentialsException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error updating discount: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("İndirim güncellenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "İndirim sil",
            description = "Belirtilen indirimi siler (soft delete)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "İndirim başarıyla silindi"),
            @ApiResponse(responseCode = "404", description = "İndirim bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('DISCOUNT_DELETE')")
    public ResponseEntity<BaseResponse<Void>> deleteDiscount(
            @Parameter(description = "Silinecek indirim ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {
        try {
            logger.info("DELETE /api/discounts/" + id);
            discountService.deleteDiscount(id);
            return ResponseEntity.ok(BaseResponse.success(null));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error deleting discount: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("İndirim silinirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/{id}/restore")
    @Operation(
            summary = "İndirim geri yükle",
            description = "Silinmiş olan indirimi geri yükler"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "İndirim başarıyla geri yüklendi"),
            @ApiResponse(responseCode = "404", description = "İndirim bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('DISCOUNT_RESTORE')")
    public ResponseEntity<BaseResponse<DiscountResponse>> restoreDiscount(
            @Parameter(description = "Geri yüklenecek indirim ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {
        try {
            logger.info("POST /api/discounts/" + id + "/restore");
            DiscountResponse discount = discountService.restoreDiscount(id);
            return ResponseEntity.ok(BaseResponse.success(discount));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error restoring discount: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("İndirim geri yüklenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/expired")
    @Operation(
            summary = "Süresi dolan indirimleri listele",
            description = "Bitiş tarihi geçmiş indirimleri getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Süresi dolan indirimler başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('DISCOUNT_READ')")
    public ResponseEntity<BaseResponse<List<DiscountResponse>>> getExpiredDiscounts() {
        try {
            List<DiscountResponse> expiredDiscounts = discountService.getExpiredDiscounts();
            return ResponseEntity.ok(BaseResponse.success(expiredDiscounts));

        } catch (Exception e) {
            logger.severe("Error fetching expired discounts: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Süresi dolan indirimler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/upcoming")
    @Operation(
            summary = "Yakında başlayacak indirimleri listele",
            description = "Henüz başlamamış olan indirimleri getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Yakında başlayacak indirimler başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('DISCOUNT_READ')")
    public ResponseEntity<BaseResponse<List<DiscountResponse>>> getUpcomingDiscounts() {
        try {
            List<DiscountResponse> upcomingDiscounts = discountService.getUpcomingDiscounts();
            return ResponseEntity.ok(BaseResponse.success(upcomingDiscounts));

        } catch (Exception e) {
            logger.severe("Error fetching upcoming discounts: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Yakında başlayacak indirimler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/calculate")
    @Operation(
            summary = "İndirim hesaplama",
            description = "Belirtilen ürün ve bayi için uygulanabilir en iyi indirimi hesaplar"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "İndirim başarıyla hesaplandı"),
            @ApiResponse(responseCode = "404", description = "Ürün veya bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('DISCOUNT_READ')")
    public ResponseEntity<BaseResponse<DiscountCalculationResponse>> calculateDiscount(
            @Parameter(description = "İndirim hesaplama bilgileri", required = true)
            @Valid @RequestBody DiscountCalculationRequest request) {

        try {
            DiscountCalculationResponse calculation = discountService.calculateDiscount(request);
            return ResponseEntity.ok(BaseResponse.success(calculation));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error calculating discount: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("İndirim hesaplama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}
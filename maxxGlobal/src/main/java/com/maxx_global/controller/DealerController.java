package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.dealer.DealerRequest;
import com.maxx_global.dto.dealer.DealerResponse;
import com.maxx_global.dto.dealer.DealerSimple;
import com.maxx_global.dto.dealer.DealerSummary;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.service.DealerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/dealers")
@Tag(name = "Dealer Management", description = "Bayi yönetimi için API endpoint'leri")
@SecurityRequirement(name = "Bearer Authentication")
public class DealerController {

    private static final Logger logger = Logger.getLogger(DealerController.class.getName());
    private final DealerService dealerService;

    public DealerController(DealerService dealerService) {
        this.dealerService = dealerService;
    }

    @GetMapping
    @Operation(
            summary = "Tüm bayileri listele",
            description = "Sayfalama ve sıralama ile tüm bayileri getirir. Sadece yetkili kullanıcılar erişebilir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bayiler başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'DEALER_READ')")
    public ResponseEntity<BaseResponse<Page<DealerResponse>>> getAllDealers(
            @Parameter(description = "Sayfa numarası (0'dan başlar)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            logger.info("GET /api/dealers - page: " + page + ", size: " + size +
                    ", sortBy: " + sortBy + ", direction: " + sortDirection);

            Page<DealerResponse> dealers = dealerService.getAllDealers(page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(dealers));

        } catch (Exception e) {
            logger.severe("Error fetching dealers: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayiler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/active")
    @Operation(
            summary = "Aktif bayileri listele",
            description = "Sadece aktif durumda olan bayileri getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Aktif bayiler başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'DEALER_READ')")
    public ResponseEntity<BaseResponse<List<DealerResponse>>> getActiveDealers() {
        try {
            logger.info("GET /api/dealers/active");
            List<DealerResponse> dealers = dealerService.getActiveDealers();
            return ResponseEntity.ok(BaseResponse.success(dealers));

        } catch (Exception e) {
            logger.severe("Error fetching active dealers: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Aktif bayiler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/summaries")
    @Operation(
            summary = "Bayi özetlerini getir",
            description = "Dropdown ve select listeleri için bayi ID ve isim özetlerini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bayi özetleri başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'DEALER_READ')")
    public ResponseEntity<BaseResponse<List<DealerSummary>>> getDealerSummaries() {
        try {
            logger.info("GET /api/dealers/summaries");
            List<DealerSummary> summaries = dealerService.getDealerSummaries();
            return ResponseEntity.ok(BaseResponse.success(summaries));

        } catch (Exception e) {
            logger.severe("Error fetching dealer summaries: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi özetleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "ID ile bayi getir",
            description = "Belirtilen ID'ye sahip bayinin detay bilgilerini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bayi başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'DEALER_READ')")
    public ResponseEntity<BaseResponse<DealerResponse>> getDealerById(
            @Parameter(description = "Bayi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {
        try {
            logger.info("GET /api/dealers/" + id);
            DealerResponse dealer = dealerService.getDealerById(id);
            return ResponseEntity.ok(BaseResponse.success(dealer));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching dealer by id: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/search-by-name")
    @Operation(
            summary = "İsme göre bayi arama",
            description = "Bayi adında belirtilen kelimeyi içeren bayileri arar (backward compatibility için)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Arama başarılı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'DEALER_READ')")
    public ResponseEntity<BaseResponse<Page<DealerResponse>>> searchDealersByName(
            @Parameter(description = "Aranacak bayi adı", example = "ABC", required = true)
            @RequestParam String name,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size) {

        try {
            logger.info("GET /api/dealers/search-by-name - name: " + name);
            Page<DealerResponse> dealers = dealerService.searchDealersByName(name, page, size);
            return ResponseEntity.ok(BaseResponse.success(dealers));

        } catch (Exception e) {
            logger.severe("Error searching dealers by name: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/search")
    @Operation(
            summary = "Genel bayi arama",
            description = "Name, email, phone, mobile ve address alanlarında arama yapar. Kısmi eşleşmeleri destekler."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Arama başarılı"),
            @ApiResponse(responseCode = "400", description = "Geçersiz arama parametresi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'DEALER_READ')")
    public ResponseEntity<BaseResponse<Page<DealerResponse>>> searchDealers(
            @Parameter(description = "Arama terimi (minimum 3 karakter)", example = "ost", required = true)
            @RequestParam String q,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection,
            @Parameter(description = "Sadece aktif bayilerde ara", example = "false")
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        try {
            logger.info("GET /api/dealers/search - q: " + q + ", activeOnly: " + activeOnly);

            Page<DealerResponse> dealers;
            if (activeOnly) {
                dealers = dealerService.searchActiveDealers(q, page, size, sortBy, sortDirection);
            } else {
                dealers = dealerService.searchDealers(q, page, size, sortBy, sortDirection);
            }

            return ResponseEntity.ok(BaseResponse.success(dealers));

        } catch (Exception e) {
            logger.severe("Error searching dealers: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/by-email")
    @Operation(
            summary = "Email ile bayi getir",
            description = "Belirtilen email adresine sahip bayiyi getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bayi başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Email ile bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'DEALER_READ')")
    public ResponseEntity<BaseResponse<DealerResponse>> getDealerByEmail(
            @Parameter(description = "Email adresi", example = "info@abcmedikal.com", required = true)
            @RequestParam String email) {
        try {
            logger.info("GET /api/dealers/by-email - email: " + email);
            DealerResponse dealer = dealerService.getDealerByEmail(email);
            return ResponseEntity.ok(BaseResponse.success(dealer));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching dealer by email: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Email ile bayi arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping
    @Operation(
            summary = "Yeni bayi oluştur",
            description = "Yeni bir bayi kaydı oluşturur. Email, bayi adı benzersiz olmalıdır. Tercih edilen para birimi belirtilebilir (varsayılan: TRY)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Bayi başarıyla oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri"),
            @ApiResponse(responseCode = "409", description = "Email veya bayi adı zaten kullanımda"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'DEALER_MANAGE')")
    public ResponseEntity<BaseResponse<DealerResponse>> createDealer(
            @Parameter(description = "Yeni bayi bilgileri", required = true)
            @Valid @RequestBody DealerRequest request) {
        try {
            logger.info("POST /api/dealers - Creating dealer: " + request.name());

            DealerResponse dealer = dealerService.createDealer(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(dealer));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.CONFLICT.value()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error creating dealer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi oluşturulurken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Bayi güncelle",
            description = "Mevcut bir bayinin bilgilerini günceller. Tercih edilen para birimi de güncellenebilir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bayi başarıyla güncellendi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri"),
            @ApiResponse(responseCode = "404", description = "Bayi bulunamadı"),
            @ApiResponse(responseCode = "409", description = "Email veya bayi adı zaten kullanımda"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'DEALER_MANAGE')")
    public ResponseEntity<BaseResponse<DealerResponse>> updateDealer(
            @Parameter(description = "Bayi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id,
            @Parameter(description = "Güncellenmiş bayi bilgileri", required = true)
            @Valid @RequestBody DealerRequest request) {

        try {
            logger.info("PUT /api/dealers/" + id + " - Updating dealer");

            DealerResponse dealer = dealerService.updateDealer(id, request);
            return ResponseEntity.ok(BaseResponse.success(dealer));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.CONFLICT.value()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error updating dealer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi güncellenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // YENİ ENDPOINT - Bayi'nin preferred currency'sini getir
    @GetMapping("/{id}/preferred-currency")
    @Operation(
            summary = "Bayinin tercih ettiği para birimini getir",
            description = "Belirtilen bayinin tercih ettiği para birimini döner"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Para birimi başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'DEALER_READ')")
    public ResponseEntity<BaseResponse<CurrencyType>> getDealerPreferredCurrency(
            @Parameter(description = "Bayi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {

        try {
            logger.info("GET /api/dealers/" + id + "/preferred-currency");
            CurrencyType currency = dealerService.getDealerPreferredCurrency(id);
            return ResponseEntity.ok(BaseResponse.success(currency));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching dealer preferred currency: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi para birimi getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Bayi sil",
            description = "Belirtilen bayiyi siler (soft delete)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bayi başarıyla silindi"),
            @ApiResponse(responseCode = "404", description = "Bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'DEALER_MANAGE')")
    public ResponseEntity<BaseResponse<Void>> deleteDealer(
            @Parameter(description = "Bayi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {
        try {
            logger.info("DELETE /api/dealers/" + id);
            dealerService.deleteDealer(id);
            return ResponseEntity.ok(BaseResponse.success(null));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error deleting dealer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi silinirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/restore/{id}")
    @Operation(
            summary = "Bayi geri yükle",
            description = "Silinmiş olan bayiyi geri yükler"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bayi başarıyla geri yüklendi"),
            @ApiResponse(responseCode = "404", description = "Bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'DEALER_MANAGE')")
    public ResponseEntity<BaseResponse<DealerResponse>> restoreDealer(
            @Parameter(description = "Bayi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {
        try {
            logger.info("POST /api/dealers/" + id + "/restore");
            DealerResponse dealer = dealerService.restoreDealer(id);
            return ResponseEntity.ok(BaseResponse.success(dealer));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error restoring dealer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi geri yüklenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }


    @GetMapping("/simple")
    @Operation(
            summary = "Basit bayi listesi",
            description = "Dropdown ve select listeleri için sadece ID ve isim içeren basit bayi listesi döner"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Basit bayi listesi başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'DEALER_READ')")
    public ResponseEntity<BaseResponse<List<DealerSimple>>> getSimpleDealers() {
        try {
            logger.info("GET /api/dealers/simple - Fetching simple dealer list");
            List<DealerSimple> dealers = dealerService.getSimpleDealers();
            return ResponseEntity.ok(BaseResponse.success(dealers));

        } catch (Exception e) {
            logger.severe("Error fetching simple dealers: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Basit bayi listesi getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}
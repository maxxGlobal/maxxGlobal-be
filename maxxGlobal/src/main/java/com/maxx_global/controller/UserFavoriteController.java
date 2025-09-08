package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.userFavorite.UserFavoriteRequest;
import com.maxx_global.dto.userFavorite.UserFavoriteResponse;
import com.maxx_global.dto.userFavorite.UserFavoriteStatusResponse;
import com.maxx_global.entity.AppUser;
import com.maxx_global.service.AppUserService;
import com.maxx_global.service.UserFavoriteService;
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
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/favorites")
@Validated
@Tag(name = "User Favorites", description = "Kullanıcı favori ürün yönetimi API'si")
@SecurityRequirement(name = "Bearer Authentication")
public class UserFavoriteController {

    private static final Logger logger = Logger.getLogger(UserFavoriteController.class.getName());

    private final UserFavoriteService userFavoriteService;
    private final AppUserService appUserService;

    public UserFavoriteController(UserFavoriteService userFavoriteService,
                                  AppUserService appUserService) {
        this.userFavoriteService = userFavoriteService;
        this.appUserService = appUserService;
    }

    @GetMapping
    @Operation(
            summary = "Kullanıcının favori ürünlerini listele",
            description = "Giriş yapmış kullanıcının favori ürünlerini sayfalama ile getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Favoriler başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Page<UserFavoriteResponse>>> getUserFavorites(
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);
            Page<UserFavoriteResponse> favorites = userFavoriteService.getUserFavorites(
                    currentUser.getId(), page, size);

            return ResponseEntity.ok(BaseResponse.success(favorites));

        } catch (Exception e) {
            logger.severe("Error fetching user favorites: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Favoriler getirilirken hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping
    @Operation(
            summary = "Favorilere ürün ekle",
            description = "Belirtilen ürünü kullanıcının favorilerine ekler"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Ürün favorilere eklendi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz istek veya ürün zaten favorilerde"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<UserFavoriteResponse>> addToFavorites(
            @Parameter(description = "Favori ekleme isteği", required = true)
            @Valid @RequestBody UserFavoriteRequest request,
            Authentication authentication) {

        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);
            UserFavoriteResponse favorite = userFavoriteService.addToFavorites(
                    currentUser.getId(), request, currentUser);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(BaseResponse.success(favorite));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error adding to favorites: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Favorilere ekleme sırasında hata: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @DeleteMapping("/{productId}")
    @Operation(
            summary = "Favorilerden ürün çıkar",
            description = "Belirtilen ürünü kullanıcının favorilerinden çıkarır"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ürün favorilerden çıkarıldı"),
            @ApiResponse(responseCode = "404", description = "Ürün favorilerde bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Void>> removeFromFavorites(
            @Parameter(description = "Ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long productId,
            Authentication authentication) {

        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);
            userFavoriteService.removeFromFavorites(currentUser.getId(), productId, currentUser);

            return ResponseEntity.ok(BaseResponse.success(null));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error removing from favorites: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Favorilerden çıkarma sırasında hata: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/status/{productId}")
    @Operation(
            summary = "Ürün favori durumunu kontrol et",
            description = "Belirtilen ürünün kullanıcının favorilerinde olup olmadığını kontrol eder"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<UserFavoriteStatusResponse>> checkFavoriteStatus(
            @Parameter(description = "Ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long productId,
            Authentication authentication) {

        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);
            UserFavoriteStatusResponse status = userFavoriteService.checkFavoriteStatus(
                    currentUser.getId(), productId);

            return ResponseEntity.ok(BaseResponse.success(status));

        } catch (Exception e) {
            logger.severe("Error checking favorite status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Favori durumu kontrolünde hata: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/status/bulk")
    @Operation(
            summary = "Birden fazla ürün için favori durumunu kontrol et",
            description = "Belirtilen ürünlerin favori durumlarını toplu olarak kontrol eder"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Map<Long, Boolean>>> checkBulkFavoriteStatus(
            @Parameter(description = "Ürün ID'leri listesi", required = true)
            @RequestBody Set<Long> productIds,
            Authentication authentication) {

        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);
            Map<Long, Boolean> statusMap = userFavoriteService.checkMultipleFavoriteStatus(
                    currentUser.getId(), productIds);

            return ResponseEntity.ok(BaseResponse.success(statusMap));

        } catch (Exception e) {
            logger.severe("Error checking bulk favorite status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Toplu favori durumu kontrolünde hata: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/count")
    @Operation(
            summary = "Kullanıcının favori sayısını getir",
            description = "Giriş yapmış kullanıcının toplam favori ürün sayısını getirir"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Long>> getFavoriteCount(Authentication authentication) {
        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);
            Long count = userFavoriteService.getUserFavoriteCount(currentUser.getId());

            return ResponseEntity.ok(BaseResponse.success(count));

        } catch (Exception e) {
            logger.severe("Error fetching favorite count: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Favori sayısı getirilirken hata: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}
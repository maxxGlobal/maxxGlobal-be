package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.appUser.AppUserRequest;
import com.maxx_global.dto.appUser.AppUserResponse;
import com.maxx_global.entity.AppUser;
import com.maxx_global.service.AppUserService;
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

import java.util.logging.Logger;


@RestController
@RequestMapping("/api/users")
@Validated
@Tag(name = "User Management", description = "Kullanıcı yönetimi için API endpoint'leri")
@SecurityRequirement(name = "Bearer Authentication")
public class AppUserController {

    private static final Logger logger = Logger.getLogger(AppUserController.class.getName());

    private final AppUserService appUserService;

    public AppUserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    /**
     * Kullanıcı bilgilerini günceller
     */
    @PutMapping("/{userId}")
    @Operation(
            summary = "Kullanıcı güncelle",
            description = "Belirtilen kullanıcının bilgilerini günceller. Sadece kendi bilgilerini veya yetki sahibi kullanıcılar güncelleyebilir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kullanıcı başarıyla güncellendi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri"),
            @ApiResponse(responseCode = "403", description = "Bu işlem için yetki yok"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<AppUserResponse>> updateUser(
            @Parameter(description = "Güncellenecek kullanıcının ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long userId,
            @Parameter(description = "Güncelleme bilgileri", required = true)
            @RequestBody @Valid AppUserRequest updateRequest,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            // Mevcut kullanıcıyı al (Security context'ten)
            AppUser currentUser = appUserService.getCurrentUser(authentication);

            // Kullanıcıyı güncelle
            AppUserResponse updatedUser = appUserService.updateUser(userId, updateRequest, currentUser);

            return ResponseEntity.ok(
                    BaseResponse.success(updatedUser)
            );

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Kullanıcıyı ID ile getirir
     */
    @GetMapping("/{userId}")
    @Operation(
            summary = "ID ile kullanıcı getir",
            description = "Belirtilen ID'ye sahip kullanıcının bilgilerini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kullanıcı başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Bu kullanıcıyı görme yetki yok"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<AppUserResponse>> getUserById(
            @Parameter(description = "Kullanıcı ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long userId) {

        try {
            AppUserResponse user = appUserService.getUserById(userId);
            return ResponseEntity.ok(BaseResponse.success(user));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Tüm kullanıcıları listeler
     */
    /**
     * Tüm kullanıcıları listeler (sayfalama ile)
     */
    @GetMapping
    @Operation(
            summary = "Tüm kullanıcıları listele",
            description = "Sistemdeki tüm kullanıcıları sayfalama ve sıralama ile listeler. USER_MANAGE yetkisi gerektirir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kullanıcılar başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Bu işlem için USER_MANAGE yetkisi gerekli"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public ResponseEntity<BaseResponse<Page<AppUserResponse>>> getAllUsers(
            @Parameter(description = "Sayfa numarası (0'dan başlar)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "firstName")
            @RequestParam(defaultValue = "firstName") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            Page<AppUserResponse> users = appUserService.getAllUsers(page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(users));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Mevcut kullanıcının kendi profil bilgilerini getirir
     */
    @GetMapping("/profile")
    @Operation(
            summary = "Kendi profil bilgilerini getir",
            description = "Giriş yapmış kullanıcının kendi profil bilgilerini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profil bilgileri başarıyla getirildi"),
            @ApiResponse(responseCode = "401", description = "Giriş yapılmamış"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<AppUserResponse>> getCurrentUserProfile(
            @Parameter(hidden = true) Authentication authentication) {

        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);
            AppUserResponse userProfile = appUserService.getUserById(currentUser.getId());

            return ResponseEntity.ok(BaseResponse.success(userProfile));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Profil bilgileri alınamadı: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
    @GetMapping("/search")
    @Operation(
            summary = "Kullanıcı arama",
            description = "FirstName, lastName, email, phoneNumber ve address alanlarında arama yapar. Kısmi eşleşmeleri destekler."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Arama başarılı"),
            @ApiResponse(responseCode = "400", description = "Geçersiz arama parametresi"),
            @ApiResponse(responseCode = "403", description = "Yetki yok")
    })
    @PreAuthorize("hasPermission(null, 'USER_READ')")
    public ResponseEntity<BaseResponse<Page<AppUserResponse>>> searchUsers(
            @Parameter(description = "Arama terimi (minimum 3 karakter)", example = "ahmet", required = true)
            @RequestParam String q,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "firstName")
            @RequestParam(defaultValue = "firstName") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection,
            @Parameter(description = "Sadece aktif kullanıcılarda ara", example = "false")
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        try {
            Page<AppUserResponse> users;
            if (activeOnly) {
                users = appUserService.searchActiveUsers(q, page, size, sortBy, sortDirection);
            } else {
                users = appUserService.searchUsers(q, page, size, sortBy, sortDirection);
            }

            return ResponseEntity.ok(BaseResponse.success(users));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Kullanıcı arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Aktif kullanıcıları listeler (sayfalama ile)
     */
    @GetMapping("/active")
    @Operation(
            summary = "Aktif kullanıcıları listele",
            description = "Sadece aktif durumda olan kullanıcıları sayfalama ve sıralama ile listeler. USER_READ yetkisi gerektirir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Aktif kullanıcılar başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Bu işlem için USER_READ yetkisi gerekli"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'USER_READ')")
    public ResponseEntity<BaseResponse<Page<AppUserResponse>>> getActiveUsers(
            @Parameter(description = "Sayfa numarası (0'dan başlar)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "firstName")
            @RequestParam(defaultValue = "firstName") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            Page<AppUserResponse> users = appUserService.getActiveUsers(page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(users));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Aktif kullanıcılar getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/byDealer/{dealerId}")
    @Operation(
            summary = "Bayiye göre kullanıcıları listele",
            description = "Belirtilen bayiye bağlı tüm kullanıcıları sayfalama ile getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bayi kullanıcıları başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Bayi bulunamadı"),
            @ApiResponse(responseCode = "403", description = "Bu işlem için USER_READ yetkisi gerekli"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<BaseResponse<Page<AppUserResponse>>> getUsersByDealer(
            @Parameter(description = "Bayi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long dealerId,
            @Parameter(description = "Sayfa numarası (0'dan başlar)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "firstName")
            @RequestParam(defaultValue = "firstName") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection,
            @Parameter(description = "Sadece aktif kullanıcılar", example = "false")
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        try {
            Page<AppUserResponse> users = appUserService.getUsersByDealer(
                    dealerId, page, size, sortBy, sortDirection, activeOnly);
            return ResponseEntity.ok(BaseResponse.success(users));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching users by dealer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi kullanıcıları getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}
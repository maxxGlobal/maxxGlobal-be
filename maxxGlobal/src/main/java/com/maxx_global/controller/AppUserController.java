package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.appUser.AppUserRequest;
import com.maxx_global.dto.appUser.AppUserResponse;
import com.maxx_global.entity.AppUser;
import com.maxx_global.service.AppUserService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Validated
public class AppUserController {

    private final AppUserService appUserService;

    public AppUserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    /**
     * Kullanıcı bilgilerini günceller
     * @param userId Güncellenecek kullanıcının ID'si
     * @param updateRequest Güncelleme bilgileri
     * @param authentication Mevcut kullanıcı bilgisi
     * @return BaseResponse ile sarılmış güncellenmiş kullanıcı bilgileri
     */
    @PutMapping("/{userId}")
    public ResponseEntity<BaseResponse<AppUserResponse>> updateUser(
            @PathVariable @Min(1) Long userId,
            @RequestBody @Valid AppUserRequest updateRequest,
            Authentication authentication) {

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
    public ResponseEntity<BaseResponse<AppUserResponse>> getUserById(
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
    @GetMapping
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public ResponseEntity<BaseResponse<List<AppUserResponse>>> getAllUsers() {

        try {
            List<AppUserResponse> users = appUserService.getAllUsers();
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
    public ResponseEntity<BaseResponse<AppUserResponse>> getCurrentUserProfile(
            Authentication authentication) {

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

    /**
     * Authentication'dan mevcut kullanıcıyı çıkarır
     */

}
// src/main/java/com/maxx_global/controller/NotificationController.java
package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.entity.AppUser;
import com.maxx_global.repository.AppUserRepository;
import com.maxx_global.service.AppUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notification Settings", description = "Bildirim ayarları yönetimi")
@SecurityRequirement(name = "Bearer Authentication")
public class NotificationController {

    private static final Logger logger = Logger.getLogger(NotificationController.class.getName());

    private final AppUserService appUserService;
    private final AppUserRepository appUserRepository; // ✅ Repository'yi direkt kullanalım

    public NotificationController(AppUserService appUserService, AppUserRepository appUserRepository) {
        this.appUserService = appUserService;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping("/settings")
    @Operation(
            summary = "Bildirim ayarlarını getir",
            description = "Kullanıcının mevcut bildirim ayarlarını getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bildirim ayarları başarıyla getirildi"),
            @ApiResponse(responseCode = "401", description = "Giriş yapılmamış"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<Map<String, Boolean>>> getNotificationSettings(
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Fetching notification settings for current user");

            AppUser currentUser = appUserService.getCurrentUser(authentication);

            Map<String, Boolean> settings = Map.of(
                    "emailNotifications", currentUser.isEmailNotificationsEnabled()
            );

            return ResponseEntity.ok(BaseResponse.success(settings));

        } catch (Exception e) {
            logger.severe("Error fetching notification settings: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(BaseResponse.error("Bildirim ayarları getirilirken bir hata oluştu: " + e.getMessage(), 500));
        }
    }

    @PutMapping("/settings/email")
    @Operation(
            summary = "E-posta bildirimlerini aç/kapat",
            description = "Kullanıcının e-posta bildirim ayarını günceller"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "E-posta bildirim ayarı başarıyla güncellendi"),
            @ApiResponse(responseCode = "401", description = "Giriş yapılmamış"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<Map<String, Boolean>>> updateEmailNotificationSettings(
            @Parameter(description = "E-posta bildirimlerini etkinleştir/devre dışı bırak", example = "true", required = true)
            @RequestParam Boolean enabled,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Updating email notification settings to: " + enabled);

            AppUser currentUser = appUserService.getCurrentUser(authentication);

            // ✅ Direkt repository kullanarak güncelle
            currentUser.setEmailNotifications(enabled);
            AppUser updatedUser = appUserRepository.save(currentUser);

            Map<String, Boolean> settings = Map.of(
                    "emailNotifications", updatedUser.isEmailNotificationsEnabled()
            );

            logger.info("Email notification settings updated successfully for user: " + currentUser.getId());
            return ResponseEntity.ok(BaseResponse.success(settings));

        } catch (Exception e) {
            logger.severe("Error updating email notification settings: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(BaseResponse.error("E-posta bildirim ayarları güncellenirken bir hata oluştu: " + e.getMessage(), 500));
        }
    }

    @PutMapping("/settings")
    @Operation(
            summary = "Tüm bildirim ayarlarını güncelle",
            description = "Kullanıcının tüm bildirim ayarlarını toplu olarak günceller"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bildirim ayarları başarıyla güncellendi"),
            @ApiResponse(responseCode = "401", description = "Giriş yapılmamış"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<Map<String, Boolean>>> updateAllNotificationSettings(
            @Parameter(description = "E-posta bildirimleri", example = "true")
            @RequestParam(required = false) Boolean emailNotifications,
            @Parameter(description = "SMS bildirimleri", example = "false")
            @RequestParam(required = false) Boolean smsNotifications,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Updating all notification settings - email: " + emailNotifications + ", sms: " + smsNotifications);

            AppUser currentUser = appUserService.getCurrentUser(authentication);

            // ✅ Sadece null olmayan değerleri güncelle
            if (emailNotifications != null) {
                currentUser.setEmailNotifications(emailNotifications);
            }

            // ✅ Direkt repository kullanarak kaydet
            AppUser updatedUser = appUserRepository.save(currentUser);

            Map<String, Boolean> settings = Map.of(
                    "emailNotifications", updatedUser.isEmailNotificationsEnabled()
            );

            logger.info("All notification settings updated successfully for user: " + currentUser.getId());
            return ResponseEntity.ok(BaseResponse.success(settings));

        } catch (Exception e) {
            logger.severe("Error updating notification settings: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(BaseResponse.error("Bildirim ayarları güncellenirken bir hata oluştu: " + e.getMessage(), 500));
        }
    }
}
package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.notification.*;
import com.maxx_global.entity.AppUser;
import com.maxx_global.enums.NotificationType;
import com.maxx_global.repository.AppUserRepository;
import com.maxx_global.service.AppUserService;
import com.maxx_global.service.NotificationService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notification Management", description = "Bildirim yönetimi")
@SecurityRequirement(name = "Bearer Authentication")
public class NotificationController {

    private static final Logger logger = Logger.getLogger(NotificationController.class.getName());

    private final AppUserService appUserService;
    private final AppUserRepository appUserRepository;
    private final NotificationService notificationService;

    public NotificationController(AppUserService appUserService, AppUserRepository appUserRepository, NotificationService notificationService) {
        this.appUserService = appUserService;
        this.appUserRepository = appUserRepository;
        this.notificationService = notificationService;
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

    @GetMapping
    @Operation(
            summary = "Kullanıcının bildirimlerini listele",
            description = "Giriş yapmış kullanıcının bildirimlerini sayfalama ve filtreleme ile getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bildirimler başarıyla getirildi"),
            @ApiResponse(responseCode = "401", description = "Giriş yapılmamış"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Page<NotificationResponse>>> getUserNotifications(
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sadece okunmamışlar", example = "false")
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @Parameter(description = "Bildirim tipi filtresi", example = "ORDER_CREATED")
            @RequestParam(required = false) String type,
            @Parameter(description = "Öncelik filtresi", example = "HIGH")
            @RequestParam(required = false) String priority,
            Authentication authentication) {

        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);

            // Filter oluştur
            NotificationFilterRequest filter = new NotificationFilterRequest(
                    null, // notificationStatus - unreadOnly ile handle edilecek
                    type != null ? com.maxx_global.enums.NotificationType.valueOf(type) : null,
                    priority,
                    null, // startDate
                    null, // endDate
                    null, // category
                    unreadOnly
            );

            Page<NotificationResponse> notifications = notificationService.getUserNotifications(
                    currentUser.getId(), page, size, filter);

            return ResponseEntity.ok(BaseResponse.success(notifications));

        } catch (Exception e) {
            logger.severe("Error fetching user notifications: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bildirimler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "Okunmamış bildirim sayısı",
            description = "Kullanıcının okunmamış bildirim sayısını getirir (header badge için)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sayı başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Long>> getUnreadCount(Authentication authentication) {
        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);
            long unreadCount = notificationService.getUnreadCount(currentUser.getId());

            return ResponseEntity.ok(BaseResponse.success(unreadCount));

        } catch (Exception e) {
            logger.severe("Error fetching unread count: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Okunmamış bildirim sayısı alınamadı: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/recent")
    @Operation(
            summary = "Son bildirimleri getir",
            description = "Header dropdown için son bildirimleri getirir (genellikle son 10)"
    )
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<NotificationResponse>>> getRecentNotifications(
            @Parameter(description = "Limit", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) int limit,
            Authentication authentication) {

        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);
            List<NotificationResponse> notifications = notificationService.getRecentNotifications(
                    currentUser.getId(), limit);

            return ResponseEntity.ok(BaseResponse.success(notifications));

        } catch (Exception e) {
            logger.severe("Error fetching recent notifications: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Son bildirimler alınamadı: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Bildirim özeti",
            description = "Kullanıcının bildirim istatistiklerini getirir"
    )
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<BaseResponse<NotificationSummary>> getNotificationSummary(
            Authentication authentication) {

        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);
            NotificationSummary summary = notificationService.getUserNotificationSummary(currentUser.getId());

            return ResponseEntity.ok(BaseResponse.success(summary));

        } catch (Exception e) {
            logger.severe("Error fetching notification summary: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bildirim özeti alınamadı: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/{id}/read")
    @Operation(
            summary = "Bildirimi okunmuş işaretle",
            description = "Belirtilen bildirimi okunmuş olarak işaretler"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bildirim okunmuş işaretlendi"),
            @ApiResponse(responseCode = "403", description = "Bu bildirimi işaretleme yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Bildirim bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<BaseResponse<NotificationResponse>> markAsRead(
            @Parameter(description = "Bildirim ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id,
            Authentication authentication) {

        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);
            NotificationResponse notification = notificationService.markAsRead(id, currentUser.getId());

            return ResponseEntity.ok(BaseResponse.success(notification));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (Exception e) {
            logger.severe("Error marking notification as read: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bildirim okundu işaretlenemedi: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/mark-all-read")
    @Operation(
            summary = "Tüm bildirimleri okunmuş işaretle",
            description = "Kullanıcının tüm okunmamış bildirimlerini okunmuş olarak işaretler"
    )
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Void>> markAllAsRead(Authentication authentication) {
        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);
            notificationService.markAllAsRead(currentUser.getId());

            return ResponseEntity.ok(BaseResponse.success(null));

        } catch (Exception e) {
            logger.severe("Error marking all notifications as read: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Tüm bildirimler okundu işaretlenemedi: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/bulk-update")
    @Operation(
            summary = "Toplu bildirim güncelleme",
            description = "Seçilen bildirimlerin durumunu toplu olarak günceller"
    )
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Void>> bulkUpdateNotifications(
            @Parameter(description = "Toplu güncelleme isteği", required = true)
            @Valid @RequestBody NotificationStatusUpdateRequest request,
            Authentication authentication) {

        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);

            if (request.notificationIds() == null || request.notificationIds().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(BaseResponse.error("En az bir bildirim seçilmelidir", 400));
            }

            notificationService.bulkUpdateStatus(request.notificationIds(), currentUser.getId(), request);

            return ResponseEntity.ok(BaseResponse.success(null));

        } catch (Exception e) {
            logger.severe("Error in bulk notification update: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Toplu güncelleme sırasında hata: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Bildirimi sil",
            description = "Belirtilen bildirimi kalıcı olarak siler"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bildirim başarıyla silindi"),
            @ApiResponse(responseCode = "403", description = "Bu bildirimi silme yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Bildirim bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Void>> deleteNotification(
            @Parameter(description = "Bildirim ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id,
            Authentication authentication) {

        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);
            notificationService.deleteNotification(id, currentUser.getId());

            return ResponseEntity.ok(BaseResponse.success(null));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (Exception e) {
            logger.severe("Error deleting notification: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bildirim silinirken hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/by-type/{type}")
    @Operation(
            summary = "Türe göre bildirimleri getir",
            description = "Belirli türdeki bildirimleri getirir (ör: ORDER_CREATED, ORDER_APPROVED)"
    )
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Page<NotificationResponse>>> getNotificationsByType(
            @Parameter(description = "Bildirim türü", example = "ORDER_CREATED", required = true)
            @PathVariable String type,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        try {
            AppUser currentUser = appUserService.getCurrentUser(authentication);

            NotificationFilterRequest filter = new NotificationFilterRequest(
                    null,
                    com.maxx_global.enums.NotificationType.valueOf(type.toUpperCase()),
                    null, null, null, null, false
            );

            Page<NotificationResponse> notifications = notificationService.getUserNotifications(
                    currentUser.getId(), page, size, filter);

            return ResponseEntity.ok(BaseResponse.success(notifications));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(BaseResponse.error("Geçersiz bildirim türü: " + type, 400));

        } catch (Exception e) {
            logger.severe("Error fetching notifications by type: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Türe göre bildirimler alınamadı: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ==================== ADMIN ENDPOINTS (Optional) ====================

    @PostMapping("/admin/create")
    @Operation(
            summary = "Admin - Dealer bazında bildirim oluştur",
            description = "Belirtilen dealer'a bağlı tüm kullanıcılara bildirim gönderir"
    )
    @PreAuthorize("hasPermission(null, 'SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<List<NotificationResponse>>> createNotificationForDealer(
            @Parameter(description = "Dealer bildirim oluşturma isteği", required = true)
            @Valid @RequestBody NotificationRequest request) {

        try {
            List<NotificationResponse> notifications = notificationService.createNotification(request);

            String message = String.format("Dealer %d için %d kullanıcıya bildirim gönderildi",
                    request.dealerId(), notifications.size());

            return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(notifications));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error creating dealer notification: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Dealer bildiimi oluşturulurken hata: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/admin/broadcast")
    @Operation(
            summary = "Admin - Toplu bildirim gönder",
            description = "Tüm kullanıcılara, belirli role sahip kullanıcılara, belirli dealer(lar) kullanıcılarına veya seçilen kullanıcılara toplu bildirim gönderir"
    )
    @PreAuthorize("hasPermission(null, 'SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<Object>> broadcastNotification(
            @Valid @RequestBody NotificationBroadcastRequest request) {

        try {
            Object result;

            if (request.sendToAll()) {
                // Tüm kullanıcılara gönder
                result = notificationService.createNotificationForAllUsers(request);

            } else if (request.targetRole() != null) {
                // Belirli role sahip kullanıcılara gönder
                result = notificationService.createNotificationForUsersByRole(request.targetRole(), request);

            } else if (request.targetDealerIds() != null && !request.targetDealerIds().isEmpty()) {
                // ✅ YENİ: Birden fazla dealer için bildirim gönder
                result = notificationService.createNotificationForMultipleDealers(request.targetDealerIds(), request);

            } else if (request.targetDealerId() != null) {
                // ✅ Geriye uyumluluk: Tek dealer için (deprecated)
                logger.warning("Using deprecated targetDealerId field. Use targetDealerIds instead.");
                result = notificationService.createNotificationForMultipleDealers(
                        List.of(request.targetDealerId()), request);

            } else if (request.specificUserIds() != null && !request.specificUserIds().isEmpty()) {
                // Seçilen kullanıcılara gönder
                result = notificationService.createNotificationForSpecificUsers(request.specificUserIds(), request);

            } else {
                return ResponseEntity.badRequest()
                        .body(BaseResponse.error("Bildirim hedefi belirtilmelidir (sendToAll, targetRole, targetDealerIds veya specificUserIds)", 400));
            }

            return ResponseEntity.ok(BaseResponse.success(result));

        } catch (Exception e) {
            logger.severe("Error broadcasting notification: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Toplu bildirim gönderilemedi: " + e.getMessage(), 500));
        }
    }

    @GetMapping("/admin/broadcast-stats")
    @Operation(
            summary = "Admin - Toplu bildirim istatistikleri",
            description = "Belirli dönem için bildirim gönderim istatistiklerini getirir"
    )
    @PreAuthorize("hasPermission(null, 'SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<NotificationBroadcastStatsResponse>> getBroadcastStats(
            @Parameter(description = "Zaman aralığı", example = "week")
            @RequestParam(defaultValue = "week") String timeRange) {

        try {
            NotificationBroadcastStatsResponse stats = notificationService.getBroadcastStats(timeRange);
            return ResponseEntity.ok(BaseResponse.success(stats));

        } catch (Exception e) {
            logger.severe("Error fetching broadcast stats: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("İstatistikler alınamadı: " + e.getMessage(), 500));
        }
    }

    @GetMapping("/types")
    @Operation(
            summary = "Bildirim türlerini listele",
            description = "Sistemde kullanılabilir tüm bildirim türlerini getirir (dropdown ve filter için)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bildirim türleri başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<NotificationTypeInfo>>> getNotificationTypes() {
        try {
            logger.info("Fetching notification types");

            List<NotificationTypeInfo> notificationTypes = Arrays.stream(NotificationType.values())
                    .map(type -> new NotificationTypeInfo(
                            type.name(),
                            type.getDisplayName(),
                            type.getCategory(),
                            getIconForNotificationType(type), // ✅ Güncellenmiş ikon metodu
                            getTypeDescription(type)
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(BaseResponse.success(notificationTypes));

        } catch (Exception e) {
            logger.severe("Error fetching notification types: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bildirim türleri getirilirken hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Bildirim tipine göre uygun ikonu döndür
     */
    private String getIconForNotificationType(NotificationType type) {
        return switch (type) {
            // Sipariş bildirimleri
            case ORDER_CREATED -> "shopping-cart";
            case ORDER_APPROVED -> "check-circle";
            case ORDER_REJECTED -> "x-circle";
            case ORDER_SHIPPED -> "truck";
            case ORDER_DELIVERED -> "package-check";
            case ORDER_CANCELLED -> "ban";
            case ORDER_EDITED -> "edit";

            // Sistem bildirimleri
            case SYSTEM_MAINTENANCE -> "settings";
            case SYSTEM_UPDATE -> "download";

            // Ürün bildirimleri
            case PRODUCT_LOW_STOCK -> "alert-triangle";
            case PRODUCT_OUT_OF_STOCK -> "alert-circle";
            case PRODUCT_PRICE_CHANGED -> "trending-up";

            // Kullanıcı bildirimleri
            case PROFILE_UPDATED -> "user";
            case PASSWORD_CHANGED -> "shield";

            // Genel bilgilendirme
            case ANNOUNCEMENT -> "megaphone";
            case PROMOTION -> "percent";

            default -> "bell"; // Varsayılan ikon
        };
    }

    /**
     * Admin tarafından oluşturulan bildirimleri listeler
     */
    @GetMapping("/admin/sent-notifications")
    @Operation(
            summary = "Admin - Gönderilen bildirimleri listele",
            description = "Admin tarafından oluşturulan bildirimleri tarihe göre listeler"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bildirimler başarıyla getirildi"),
            @ApiResponse(responseCode = "401", description = "Giriş yapılmamış"),
            @ApiResponse(responseCode = "403", description = "Yetkiniz yok"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'SYSTEM_ADMIN') or hasPermission(null, 'ADMIN')")
    public ResponseEntity<BaseResponse<Page<AdminNotificationResponse>>> getAdminSentNotifications(
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Bildirim türü filtresi", example = "ORDER_CREATED")
            @RequestParam(required = false) String type,
            @Parameter(description = "Başlangıç tarihi (yyyy-MM-dd)", example = "2024-01-01")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "Bitiş tarihi (yyyy-MM-dd)", example = "2024-12-31")
            @RequestParam(required = false) String endDate,
            @Parameter(description = "Arama terimi (başlık veya mesajda)", example = "kampanya")
            @RequestParam(required = false) String searchTerm,
            @Parameter(description = "Öncelik seviyesi", example = "HIGH")
            @RequestParam(required = false) String priority,
            Authentication authentication) {

        try {
            logger.info("Fetching admin sent notifications - page: " + page + ", size: " + size);

            // Filter parametrelerini oluştur
            AdminNotificationFilter filter = new AdminNotificationFilter(
                    type,
                    startDate,
                    endDate,
                    searchTerm,
                    priority
            );

            Page<AdminNotificationResponse> notifications = notificationService.getAdminSentNotifications(
                    page, size, filter);

            return ResponseEntity.ok(BaseResponse.success(notifications));

        } catch (Exception e) {
            logger.severe("Error fetching admin sent notifications: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Admin bildirimleri getirilirken hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Belirli bir bildirimin detaylarını ve istatistiklerini getir
     */
    @GetMapping("/admin/notifications/{id}/details")
    @Operation(
            summary = "Admin - Bildirim detayları ve istatistikleri",
            description = "Belirli bir bildirimin detaylarını, kaç kişiye gönderildiğini ve okunma oranlarını getirir"
    )
    @PreAuthorize("hasPermission(null, 'SYSTEM_ADMIN') or hasPermission(null, 'ADMIN')")
    public ResponseEntity<BaseResponse<AdminNotificationDetailResponse>> getNotificationDetails(
            @Parameter(description = "Bildirim ID'si", example = "1", required = true)
            @PathVariable Long id,
            Authentication authentication) {

        try {
            logger.info("Fetching notification details: " + id);

            AdminNotificationDetailResponse details = notificationService.getNotificationDetails(id);

            return ResponseEntity.ok(BaseResponse.success(details));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching notification details: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bildirim detayları alınamadı: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    private String getDefaultIcon(NotificationType type) {
        // Artık getIconForNotificationType metodunu kullan
        return getIconForNotificationType(type);
    }

    @GetMapping("/types/categories")
    @Operation(
            summary = "Bildirim kategorilerini listele",
            description = "Bildirim türlerinin kategorilerini benzersiz olarak getirir"
    )
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<NotificationCategoryInfo>>> getNotificationCategories() {
        try {
            logger.info("Fetching notification categories");

            // Kategorileri benzersiz olarak topla
            Map<String, List<NotificationType>> categorizedTypes = Arrays.stream(NotificationType.values())
                    .collect(Collectors.groupingBy(NotificationType::getCategory));

            List<NotificationCategoryInfo> categories = categorizedTypes.entrySet().stream()
                    .map(entry -> new NotificationCategoryInfo(
                            entry.getKey(),
                            getCategoryDisplayName(entry.getKey()),
                            getCategoryIcon(entry.getKey()),
                            entry.getValue().size(),
                            entry.getValue().stream()
                                    .map(NotificationType::name)
                                    .collect(Collectors.toList())
                    ))
                    .sorted((c1, c2) -> c1.name().compareToIgnoreCase(c2.name()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(BaseResponse.success(categories));

        } catch (Exception e) {
            logger.severe("Error fetching notification categories: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bildirim kategorileri getirilirken hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/priorities")
    @Operation(
            summary = "Bildirim öncelik seviyelerini listele",
            description = "Sistemde kullanılabilir öncelik seviyelerini getirir"
    )
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<NotificationPriorityInfo>>> getNotificationPriorities() {
        try {
            logger.info("Fetching notification priorities");

            List<NotificationPriorityInfo> priorities = List.of(
                    new NotificationPriorityInfo("LOW", "Düşük", "#28a745", "info"),
                    new NotificationPriorityInfo("MEDIUM", "Orta", "#ffc107", "bell"),
                    new NotificationPriorityInfo("HIGH", "Yüksek", "#fd7e14", "alert-triangle"),
                    new NotificationPriorityInfo("URGENT", "Acil", "#dc3545", "alert-circle")
            );

            return ResponseEntity.ok(BaseResponse.success(priorities));

        } catch (Exception e) {
            logger.severe("Error fetching notification priorities: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Öncelik seviyeleri getirilirken hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // Helper metodlar
    private String getTypeDescription(NotificationType type) {
        return switch (type) {
            case ORDER_CREATED -> "Yeni sipariş oluşturulduğunda gönderilir";
            case ORDER_APPROVED -> "Sipariş onaylandığında gönderilir";
            case ORDER_REJECTED -> "Sipariş reddedildiğinde gönderilir";
            case ORDER_SHIPPED -> "Sipariş kargoya verildiğinde gönderilir";
            case ORDER_DELIVERED -> "Sipariş teslim edildiğinde gönderilir";
            case ORDER_CANCELLED -> "Sipariş iptal edildiğinde gönderilir";
            case ORDER_EDITED -> "Sipariş düzenlendiğinde gönderilir";
            case SYSTEM_MAINTENANCE -> "Sistem bakımı bildirimleri için kullanılır";
            case SYSTEM_UPDATE -> "Sistem güncellemeleri için kullanılır";
            case PRODUCT_LOW_STOCK -> "Ürün stoku azaldığında gönderilir";
            case PRODUCT_OUT_OF_STOCK -> "Ürün stoku bittiğinde gönderilir";
            case PRODUCT_PRICE_CHANGED -> "Ürün fiyatı değiştiğinde gönderilir";
            case PROFILE_UPDATED -> "Profil güncellendiğinde gönderilir";
            case PASSWORD_CHANGED -> "Şifre değiştirildiğinde gönderilir";
            case ANNOUNCEMENT -> "Genel duyurular için kullanılır";
            case PROMOTION -> "Kampanya ve promosyonlar için kullanılır";
            default -> "Bildirim türü açıklaması mevcut değil";
        };
    }

    private String getCategoryDisplayName(String category) {
        return switch (category) {
            case "order" -> "Sipariş Bildirimleri";
            case "system" -> "Sistem Bildirimleri";
            case "product" -> "Ürün Bildirimleri";
            case "user" -> "Kullanıcı Bildirimleri";
            case "promotion" -> "Kampanya Bildirimleri";
            case "info" -> "Bilgilendirme";
            default -> "Genel";
        };
    }

    private String getCategoryIcon(String category) {
        return switch (category) {
            case "order" -> "shopping-cart";
            case "system" -> "settings";
            case "product" -> "package";
            case "user" -> "user";
            case "promotion" -> "percent";
            case "info" -> "info";
            default -> "bell";
        };
    }
}
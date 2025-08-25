package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.order.*;
import com.maxx_global.entity.AppUser;
import com.maxx_global.service.AppUserService;
import com.maxx_global.service.OrderService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.logging.Logger;

@RestController
@RequestMapping("/api/orders")
@Validated
@Tag(name = "Order Management", description = "Sipariş yönetimi için API endpoint'leri")
@SecurityRequirement(name = "Bearer Authentication")
public class OrderController {

    private static final Logger logger = Logger.getLogger(OrderController.class.getName());

    private final OrderService orderService;
    private final AppUserService appUserService;

    public OrderController(OrderService orderService, AppUserService appUserService) {
        this.orderService = orderService;
        this.appUserService = appUserService;
    }

    // ==================== END USER ENDPOINTS ====================

    @PostMapping
    @Operation(
            summary = "Yeni sipariş oluştur",
            description = "Kullanıcı yeni bir sipariş oluşturur. Fiyatlar backend'de hesaplanır ve stok kontrolü yapılır."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Sipariş başarıyla oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri veya stok yetersiz"),
            @ApiResponse(responseCode = "404", description = "Ürün veya bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    public ResponseEntity<BaseResponse<OrderResponse>> createOrder(
            @Parameter(description = "Sipariş bilgileri", required = true)
            @Valid @RequestBody OrderRequest request,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Creating new order for dealer: " + request.dealerId());

            // Mevcut kullanıcıyı al
            AppUser currentUser = appUserService.getCurrentUser(authentication);

            // Sipariş oluştur
            OrderResponse order = orderService.createOrder(request, currentUser);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(BaseResponse.success(order));

        } catch (EntityNotFoundException e) {
            logger.warning("Entity not found: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (IllegalArgumentException e) {
            logger.warning("Invalid argument: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error creating order: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sipariş oluşturulurken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/my-orders")
    @Operation(
            summary = "Kendi siparişlerimi listele",
            description = "Giriş yapmış kullanıcının tüm siparişlerini sayfalama ile listeler"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Siparişler başarıyla getirildi"),
            @ApiResponse(responseCode = "401", description = "Giriş yapılmamış"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<BaseResponse<Page<OrderResponse>>> getMyOrders(
            @Parameter(description = "Sayfa numarası (0'dan başlar)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "orderDate")
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDirection,
            @Parameter(description = "Sipariş durumu filtresi (opsiyonel)", example = "PENDING")
            @RequestParam(required = false) String status,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Fetching orders for current user - page: " + page + ", size: " + size);

            // Mevcut kullanıcıyı al
            AppUser currentUser = appUserService.getCurrentUser(authentication);

            // Kullanıcının siparişlerini getir
            Page<OrderResponse> orders = orderService.getOrdersByUser(
                    currentUser.getId(), page, size, sortBy, sortDirection, status);

            return ResponseEntity.ok(BaseResponse.success(orders));

        } catch (Exception e) {
            logger.severe("Error fetching user orders: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Siparişler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/{orderId}")
    @Operation(
            summary = "Sipariş detayını getir",
            description = "Belirtilen sipariş ID'sine ait detay bilgilerini getirir. Sadece kendi siparişlerini görebilir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sipariş detayı başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Bu siparişi görme yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Sipariş bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<BaseResponse<OrderResponse>> getOrderById(
            @Parameter(description = "Sipariş ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long orderId,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Fetching order details for id: " + orderId);

            // Mevcut kullanıcıyı al
            AppUser currentUser = appUserService.getCurrentUser(authentication);

            // Sipariş detayını getir (yetki kontrolü ile)
            OrderResponse order = orderService.getOrderByIdForUser(orderId, currentUser);

            return ResponseEntity.ok(BaseResponse.success(order));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (Exception e) {
            logger.severe("Error fetching order details: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sipariş detayı getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/{orderId}/cancel")
    @Operation(
            summary = "Siparişi iptal et",
            description = "Sadece PENDING durumundaki siparişler iptal edilebilir. Kullanıcı sadece kendi siparişlerini iptal edebilir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sipariş başarıyla iptal edildi"),
            @ApiResponse(responseCode = "400", description = "Sipariş iptal edilebilir durumda değil"),
            @ApiResponse(responseCode = "403", description = "Bu siparişi iptal etme yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Sipariş bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    public ResponseEntity<BaseResponse<OrderResponse>> cancelOrder(
            @Parameter(description = "İptal edilecek sipariş ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long orderId,
            @Parameter(description = "İptal nedeni (opsiyonel)", example = "Müşteri talebi")
            @RequestParam(required = false) String cancelReason,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Cancelling order: " + orderId);

            // Mevcut kullanıcıyı al
            AppUser currentUser = appUserService.getCurrentUser(authentication);

            // Siparişi iptal et
            OrderResponse cancelledOrder = orderService.cancelOrderByUser(orderId, currentUser, cancelReason);

            return ResponseEntity.ok(BaseResponse.success(cancelledOrder));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error cancelling order: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sipariş iptal edilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/my-orders/summary")
    @Operation(
            summary = "Sipariş özeti getir",
            description = "Kullanıcının sipariş istatistiklerini getirir (toplam sipariş, bekleyen, tamamlanan vb.)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sipariş özeti başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<BaseResponse<Object>> getMyOrdersSummary(
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Fetching order summary for current user");

            // Mevcut kullanıcıyı al
            AppUser currentUser = appUserService.getCurrentUser(authentication);

            // Sipariş özetini getir
            Object summary = orderService.getOrderSummaryByUser(currentUser.getId());

            return ResponseEntity.ok(BaseResponse.success(summary));

        } catch (Exception e) {
            logger.severe("Error fetching order summary: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sipariş özeti getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
    @GetMapping("/admin/all")
    @Operation(
            summary = "Tüm siparişleri listele (Admin)",
            description = "Sistem adminleri tüm siparişleri sayfalama ve filtreleme ile görüntüleyebilir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Siparişler başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Admin yetkisi gerekli"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_MANAGE') or hasAuthority('SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<Page<OrderResponse>>> getAllOrders(
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sıralama alanı", example = "orderDate")
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDirection,
            @Parameter(description = "Sipariş durumu filtresi", example = "PENDING")
            @RequestParam(required = false) String status,
            @Parameter(description = "Bayi ID filtresi", example = "1")
            @RequestParam(required = false) Long dealerId,
            @Parameter(description = "Kullanıcı ID filtresi", example = "5")
            @RequestParam(required = false) Long userId) {

        try {
            logger.info("Admin fetching all orders with filters - status: " + status +
                    ", dealerId: " + dealerId + ", userId: " + userId);

            Page<OrderResponse> orders = orderService.getAllOrdersForAdmin(
                    page, size, sortBy, sortDirection, status, dealerId, userId);

            return ResponseEntity.ok(BaseResponse.success(orders));

        } catch (Exception e) {
            logger.severe("Error fetching all orders for admin: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Siparişler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/admin/{orderId}/approve")
    @Operation(
            summary = "Siparişi onayla (Admin)",
            description = "Admin siparişi onaylar ve APPROVED durumuna geçirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sipariş başarıyla onaylandı"),
            @ApiResponse(responseCode = "400", description = "Sipariş onaylanabilir durumda değil"),
            @ApiResponse(responseCode = "403", description = "Admin yetkisi gerekli"),
            @ApiResponse(responseCode = "404", description = "Sipariş bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_MANAGE') or hasAuthority('SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<OrderResponse>> approveOrder(
            @Parameter(description = "Sipariş ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long orderId,
            @Parameter(description = "Admin notu", example = "Sipariş onaylandı, kargo süreci başlatılacak")
            @RequestParam(required = false) String adminNote,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Admin approving order: " + orderId);

            AppUser currentAdmin = appUserService.getCurrentUser(authentication);
            OrderResponse approvedOrder = orderService.approveOrder(orderId, currentAdmin, adminNote);

            return ResponseEntity.ok(BaseResponse.success(approvedOrder));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error approving order: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sipariş onaylanırken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/admin/{orderId}/reject")
    @Operation(
            summary = "Siparişi reddet (Admin)",
            description = "Admin siparişi reddeder ve REJECTED durumuna geçirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sipariş başarıyla reddedildi"),
            @ApiResponse(responseCode = "400", description = "Sipariş reddedilebilir durumda değil"),
            @ApiResponse(responseCode = "403", description = "Admin yetkisi gerekli"),
            @ApiResponse(responseCode = "404", description = "Sipariş bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_MANAGE') or hasAuthority('SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<OrderResponse>> rejectOrder(
            @Parameter(description = "Sipariş ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long orderId,
            @Parameter(description = "Red nedeni", example = "Stok yetersizliği", required = true)
            @RequestParam String rejectionReason,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Admin rejecting order: " + orderId + ", reason: " + rejectionReason);

            AppUser currentAdmin = appUserService.getCurrentUser(authentication);
            OrderResponse rejectedOrder = orderService.rejectOrder(orderId, currentAdmin, rejectionReason);

            return ResponseEntity.ok(BaseResponse.success(rejectedOrder));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error rejecting order: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sipariş reddedilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/admin/{orderId}/edit")
    @Operation(
            summary = "Siparişi düzenle (Admin)",
            description = "Admin sipariş kalemlerini düzenleyebilir (miktar değişikliği, ürün çıkarma vb.)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sipariş başarıyla düzenlendi"),
            @ApiResponse(responseCode = "400", description = "Sipariş düzenlenebilir durumda değil"),
            @ApiResponse(responseCode = "403", description = "Admin yetkisi gerekli"),
            @ApiResponse(responseCode = "404", description = "Sipariş bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_MANAGE') or hasAuthority('SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<OrderResponse>> editOrder(
            @Parameter(description = "Sipariş ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long orderId,
            @Parameter(description = "Güncellenmiş sipariş bilgileri", required = true)
            @Valid @RequestBody OrderRequest updatedOrderRequest,
            @Parameter(description = "Düzenleme nedeni", example = "Stok durumuna göre miktar güncellendi")
            @RequestParam(required = false) String editReason,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Admin editing order: " + orderId);

            AppUser currentAdmin = appUserService.getCurrentUser(authentication);
            OrderResponse editedOrder = orderService.editOrderByAdmin(
                    orderId, updatedOrderRequest, currentAdmin, editReason);

            return ResponseEntity.ok(BaseResponse.success(editedOrder));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error editing order: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sipariş düzenlenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/admin/{orderId}/status")
    @Operation(
            summary = "Sipariş durumunu değiştir (Admin)",
            description = "Admin sipariş durumunu değiştirebilir (SHIPPED, COMPLETED vb.)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sipariş durumu başarıyla güncellendi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz durum geçişi"),
            @ApiResponse(responseCode = "403", description = "Admin yetkisi gerekli"),
            @ApiResponse(responseCode = "404", description = "Sipariş bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_MANAGE') or hasAuthority('SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<OrderResponse>> updateOrderStatus(
            @Parameter(description = "Sipariş ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long orderId,
            @Parameter(description = "Yeni durum", example = "SHIPPED", required = true)
            @RequestParam String newStatus,
            @Parameter(description = "Durum değişiklik notu", example = "Kargo firmasına teslim edildi")
            @RequestParam(required = false) String statusNote,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Admin updating order status: " + orderId + " to " + newStatus);

            AppUser currentAdmin = appUserService.getCurrentUser(authentication);
            OrderResponse updatedOrder = orderService.updateOrderStatus(
                    orderId, newStatus, currentAdmin, statusNote);

            return ResponseEntity.ok(BaseResponse.success(updatedOrder));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error updating order status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sipariş durumu güncellenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/admin/statistics")
    @Operation(
            summary = "Sipariş istatistikleri (Admin)",
            description = "Admin paneli için sipariş istatistiklerini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "İstatistikler başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Admin yetkisi gerekli"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_MANAGE') or hasAuthority('SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<Object>> getOrderStatistics(
            @Parameter(description = "İstatistik tarihi (başlangıç)", example = "2024-01-01")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "İstatistik tarihi (bitiş)", example = "2024-12-31")
            @RequestParam(required = false) String endDate) {

        try {
            logger.info("Admin fetching order statistics");

            Object statistics = orderService.getOrderStatisticsForAdmin(startDate, endDate);

            return ResponseEntity.ok(BaseResponse.success(statistics));

        } catch (Exception e) {
            logger.severe("Error fetching order statistics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sipariş istatistikleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/admin/search")
    @Operation(
            summary = "Sipariş arama (Admin)",
            description = "Admin sipariş numarası, kullanıcı adı, bayi adı ile arama yapabilir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Arama sonuçları başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Admin yetkisi gerekli"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_MANAGE') or hasAuthority('SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<Page<OrderResponse>>> searchOrders(
            @Parameter(description = "Arama terimi", example = "ORD-2024-001", required = true)
            @RequestParam String searchTerm,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        try {
            logger.info("Admin searching orders with term: " + searchTerm);

            Page<OrderResponse> searchResults = orderService.searchOrdersForAdmin(
                    searchTerm, page, size);

            return ResponseEntity.ok(BaseResponse.success(searchResults));

        } catch (Exception e) {
            logger.severe("Error searching orders: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sipariş arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
    // ==================== SON KULLANICI İÇİN EK ENDPOINT'LER ====================

    @GetMapping("/recent")
    @Operation(
            summary = "Son siparişleri getir",
            description = "Kullanıcının son 10 siparişini getirir (hızlı erişim için)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Son siparişler başarıyla getirildi"),
            @ApiResponse(responseCode = "401", description = "Giriş yapılmamış"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<BaseResponse<Page<OrderResponse>>> getRecentOrders(
            @Parameter(description = "Getirilecek sipariş sayısı", example = "10")
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sıralama alanı", example = "orderDate")
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDirection,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Fetching recent orders for current user, limit: " + limit);

            AppUser currentUser = appUserService.getCurrentUser(authentication);
            Page<OrderResponse> recentOrders = orderService.getRecentOrdersByUser(
                    currentUser.getId(),page,size,sortBy,sortDirection);

            return ResponseEntity.ok(BaseResponse.success(recentOrders));

        } catch (Exception e) {
            logger.severe("Error fetching recent orders: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Son siparişler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ==================== GENEL KULLANIŞLI ENDPOINT'LER ====================

    @GetMapping("/dealer/{dealerId}")
    @Operation(
            summary = "Bayiye göre siparişleri listele",
            description = "Belirtilen bayi ID'sine ait siparişleri listeler"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bayi siparişleri başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<BaseResponse<Page<OrderResponse>>> getOrdersByDealer(
            @Parameter(description = "Bayi ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long dealerId,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "orderDate")
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDirection,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Fetching orders for dealer: " + dealerId);

            AppUser currentUser = appUserService.getCurrentUser(authentication);
            Page<OrderResponse> orders = orderService.getOrdersByDealer(
                    dealerId, currentUser, page, size, sortBy, sortDirection);

            return ResponseEntity.ok(BaseResponse.success(orders));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching orders by dealer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi siparişleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/by-date-range")
    @Operation(
            summary = "Tarih aralığına göre siparişleri listele",
            description = "Belirtilen tarih aralığındaki siparişleri getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tarih aralığındaki siparişler başarıyla getirildi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz tarih formatı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<BaseResponse<Page<OrderResponse>>> getOrdersByDateRange(
            @Parameter(description = "Başlangıç tarihi (yyyy-MM-dd)", example = "2024-01-01", required = true)
            @RequestParam String startDate,
            @Parameter(description = "Bitiş tarihi (yyyy-MM-dd)", example = "2024-12-31", required = true)
            @RequestParam String endDate,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "orderDate")
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDirection,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Fetching orders by date range: " + startDate + " to " + endDate);

            AppUser currentUser = appUserService.getCurrentUser(authentication);
            Page<OrderResponse> orders = orderService.getOrdersByDateRange(
                    startDate, endDate, currentUser, page, size, sortBy, sortDirection);

            return ResponseEntity.ok(BaseResponse.success(orders));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error fetching orders by date range: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Tarih aralığına göre siparişler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/{orderId}/history")
    @Operation(
            summary = "Sipariş geçmişini getir",
            description = "Belirtilen siparişin durum değişiklik geçmişini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sipariş geçmişi başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Bu siparişin geçmişini görme yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Sipariş bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<BaseResponse<Object>> getOrderHistory(
            @Parameter(description = "Sipariş ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long orderId,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Fetching order history for id: " + orderId);

            AppUser currentUser = appUserService.getCurrentUser(authentication);
            Object orderHistory = orderService.getOrderHistory(orderId, currentUser);

            return ResponseEntity.ok(BaseResponse.success(orderHistory));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (Exception e) {
            logger.severe("Error fetching order history: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sipariş geçmişi getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/by-status")
    @Operation(
            summary = "Duruma göre siparişleri listele",
            description = "Belirtilen duruma göre siparişleri getirir (örn: PENDING, APPROVED, COMPLETED)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Durum siparişleri başarıyla getirildi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz sipariş durumu"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<BaseResponse<Page<OrderResponse>>> getOrdersByStatus(
            @Parameter(description = "Sipariş durumu", example = "PENDING", required = true)
            @RequestParam String status,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "orderDate")
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDirection,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Fetching orders by status: " + status);

            AppUser currentUser = appUserService.getCurrentUser(authentication);
            Page<OrderResponse> orders = orderService.getOrdersByStatus(
                    status, currentUser, page, size, sortBy, sortDirection);

            return ResponseEntity.ok(BaseResponse.success(orders));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error fetching orders by status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Durum siparişleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/calculate")
    @Operation(
            summary = "Sipariş tutarı hesapla",
            description = "Sepet için sipariş tutarını hesaplar (gerçek sipariş oluşturmadan)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sipariş tutarı başarıyla hesaplandı"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri"),
            @ApiResponse(responseCode = "404", description = "Ürün veya bayi bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<BaseResponse<Object>> calculateOrderTotal(
            @Parameter(description = "Sipariş hesaplama bilgileri", required = true)
            @Valid @RequestBody OrderRequest request,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Calculating order total for dealer: " + request.dealerId());

            AppUser currentUser = appUserService.getCurrentUser(authentication);
            Object calculation = orderService.calculateOrderTotal(request, currentUser);

            return ResponseEntity.ok(BaseResponse.success(calculation));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error calculating order total: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sipariş tutarı hesaplanırken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ==================== ADMİN İÇİN EK ENDPOINT'LER ====================

    @GetMapping("/admin/pending-approval")
    @Operation(
            summary = "Onay bekleyen siparişleri listele (Admin)",
            description = "PENDING durumundaki siparişleri özel listeler (admin hızlı erişimi için)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Onay bekleyen siparişler başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Admin yetkisi gerekli"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_MANAGE') or hasAuthority('SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<Page<OrderResponse>>> getPendingApprovalOrders(
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sıralama alanı", example = "orderDate")
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            logger.info("Admin fetching pending approval orders");

            Page<OrderResponse> pendingOrders = orderService.getPendingApprovalOrders(
                    page, size, sortBy, sortDirection);

            return ResponseEntity.ok(BaseResponse.success(pendingOrders));

        } catch (Exception e) {
            logger.severe("Error fetching pending approval orders: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Onay bekleyen siparişler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @DeleteMapping("/admin/{orderId}/remove-item/{itemId}")
    @Operation(
            summary = "Siparişten ürün çıkar (Admin)",
            description = "Admin belirtilen sipariş kalemini siparişten çıkarır"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ürün başarıyla siparişten çıkarıldı"),
            @ApiResponse(responseCode = "400", description = "Sipariş düzenlenebilir durumda değil"),
            @ApiResponse(responseCode = "403", description = "Admin yetkisi gerekli"),
            @ApiResponse(responseCode = "404", description = "Sipariş veya ürün bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_MANAGE') or hasAuthority('SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<OrderResponse>> removeItemFromOrder(
            @Parameter(description = "Sipariş ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long orderId,
            @Parameter(description = "Çıkarılacak ürün kalemi ID'si", example = "5", required = true)
            @PathVariable @Min(1) Long itemId,
            @Parameter(description = "Çıkarma nedeni", example = "Stok yetersizliği")
            @RequestParam(required = false) String removeReason,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Admin removing item " + itemId + " from order " + orderId);

            AppUser currentAdmin = appUserService.getCurrentUser(authentication);
            OrderResponse updatedOrder = orderService.removeItemFromOrder(
                    orderId, itemId, currentAdmin, removeReason);

            return ResponseEntity.ok(BaseResponse.success(updatedOrder));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error removing item from order: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürün siparişten çıkarılırken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ==================== RAPORLAMA ENDPOINT'LERİ ====================

    @GetMapping("/reports/daily")
    @Operation(
            summary = "Günlük sipariş raporu",
            description = "Belirtilen tarih için günlük sipariş raporunu getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Günlük rapor başarıyla getirildi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz tarih formatı"),
            @ApiResponse(responseCode = "403", description = "Raporlama yetkisi gerekli"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_READ') or hasAuthority('ORDER_MANAGE')")
    public ResponseEntity<BaseResponse<Object>> getDailyReport(
            @Parameter(description = "Rapor tarihi (yyyy-MM-dd)", example = "2024-01-15")
            @RequestParam(required = false) String reportDate,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Generating daily report for date: " + reportDate);

            AppUser currentUser = appUserService.getCurrentUser(authentication);
            Object dailyReport = orderService.getDailyReport(reportDate, currentUser);

            return ResponseEntity.ok(BaseResponse.success(dailyReport));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error generating daily report: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Günlük rapor oluşturulurken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/reports/monthly")
    @Operation(
            summary = "Aylık sipariş raporu",
            description = "Belirtilen ay için aylık sipariş raporunu getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Aylık rapor başarıyla getirildi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz ay formatı"),
            @ApiResponse(responseCode = "403", description = "Raporlama yetkisi gerekli"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_READ') or hasAuthority('ORDER_MANAGE')")
    public ResponseEntity<BaseResponse<Object>> getMonthlyReport(
            @Parameter(description = "Rapor yılı", example = "2024")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "Rapor ayı (1-12)", example = "6")
            @RequestParam(required = false) Integer month,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Generating monthly report for year: " + year + ", month: " + month);

            AppUser currentUser = appUserService.getCurrentUser(authentication);
            Object monthlyReport = orderService.getMonthlyReport(year, month, currentUser);

            return ResponseEntity.ok(BaseResponse.success(monthlyReport));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error generating monthly report: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Aylık rapor oluşturulurken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/reports/dealer-performance")
    @Operation(
            summary = "Bayi performans raporu",
            description = "Bayilerin sipariş performansını analiz eden raporu getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bayi performans raporu başarıyla getirildi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz tarih aralığı"),
            @ApiResponse(responseCode = "403", description = "Raporlama yetkisi gerekli"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_MANAGE') or hasAuthority('SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<Object>> getDealerPerformanceReport(
            @Parameter(description = "Başlangıç tarihi (yyyy-MM-dd)", example = "2024-01-01")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "Bitiş tarihi (yyyy-MM-dd)", example = "2024-12-31")
            @RequestParam(required = false) String endDate,
            @Parameter(description = "Belirli bayi ID (opsiyonel)", example = "5")
            @RequestParam(required = false) Long dealerId) {

        try {
            logger.info("Generating dealer performance report for period: " + startDate + " to " + endDate);

            Object performanceReport = orderService.getDealerPerformanceReport(
                    startDate, endDate, dealerId);

            return ResponseEntity.ok(BaseResponse.success(performanceReport));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error generating dealer performance report: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi performans raporu oluşturulurken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
    // OrderController.java'ya eklenecek metodlar:

    @GetMapping("/edited/{orderId}")
    @Operation(
            summary = "Düzenlenmiş sipariş detayını getir",
            description = "Admin tarafından düzenlenmiş siparişin detaylarını ve değişiklikleri getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Düzenlenmiş sipariş detayı başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Bu siparişi görme yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Sipariş bulunamadı veya düzenlenmemiş"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<BaseResponse<EditedOrderResponse>> getEditedOrderDetails(
            @Parameter(description = "Sipariş ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long orderId,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Fetching edited order details for id: " + orderId);

            // Mevcut kullanıcıyı al
            AppUser currentUser = appUserService.getCurrentUser(authentication);

            // Düzenlenmiş sipariş detayını getir
            EditedOrderResponse editedOrder = orderService.getEditedOrderDetails(orderId, currentUser);

            return ResponseEntity.ok(BaseResponse.success(editedOrder));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (Exception e) {
            logger.severe("Error fetching edited order details: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Düzenlenmiş sipariş detayı getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/edited/{orderId}/approve")
    @Operation(
            summary = "Düzenlenmiş siparişi onayla/reddet",
            description = "Müşteri admin tarafından düzenlenmiş siparişi onaylar veya reddeder"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sipariş onay/red işlemi başarıyla tamamlandı"),
            @ApiResponse(responseCode = "400", description = "Geçersiz istek veya sipariş durumu"),
            @ApiResponse(responseCode = "403", description = "Bu siparişi onaylama yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Sipariş bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    public ResponseEntity<BaseResponse<OrderResponse>> approveEditedOrder(
            @Parameter(description = "Sipariş ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long orderId,
            @Parameter(description = "Onay bilgileri", required = true)
            @Valid @RequestBody OrderEditApprovalRequest request,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Customer approving/rejecting edited order: " + orderId +
                    ", approved: " + request.approved());

            // Mevcut kullanıcıyı al
            AppUser currentUser = appUserService.getCurrentUser(authentication);

            // Düzenlenmiş siparişi onayla/reddet
            OrderResponse result = orderService.approveOrRejectEditedOrder(
                    orderId, currentUser, request.approved(), request.customerNote());

            return ResponseEntity.ok(BaseResponse.success(result));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error approving/rejecting edited order: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sipariş onay işlemi sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}
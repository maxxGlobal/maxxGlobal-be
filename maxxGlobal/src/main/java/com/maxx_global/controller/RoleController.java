package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.permission.PermissionIdsRequest;
import com.maxx_global.dto.role.RoleRequest;
import com.maxx_global.dto.role.RoleResponse;
import com.maxx_global.dto.role.RoleSimple;
import com.maxx_global.dto.role.RoleSummary;
import com.maxx_global.entity.AppUser;
import com.maxx_global.service.AppUserService;
import com.maxx_global.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/roles")
@Validated
@Tag(name = "Role Management", description = "Rol yönetimi için API endpoint'leri. Tüm işlemler SYSTEM_ADMIN yetkisi gerektirir.")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasPermission(null,'SYSTEM_ADMIN')")
public class RoleController {

    private final RoleService roleService;
    private static final Logger logger = Logger.getLogger(RoleController.class.getName());

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * Yeni rol oluşturur
     */
    @PostMapping
    @Operation(
            summary = "Yeni rol oluştur",
            description = "Sistemde yeni bir rol oluşturur. SYSTEM_ADMIN yetkisi gerektirir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Rol başarıyla oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri veya rol adı zaten kullanımda"),
            @ApiResponse(responseCode = "403", description = "SYSTEM_ADMIN yetkisi gerekli"),
            @ApiResponse(responseCode = "404", description = "Belirtilen permission bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<RoleResponse>> createRole(
            @Parameter(description = "Yeni rol bilgileri", required = true)
            @RequestBody @Valid RoleRequest roleRequest) {

        try {
            RoleResponse createdRole = roleService.createRole(roleRequest);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(BaseResponse.success(createdRole));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Rol günceller
     */
    @PutMapping("/{roleId}")
    @Operation(
            summary = "Rol güncelle",
            description = "Mevcut bir rolün bilgilerini günceller. Rol adı ve permission'ları güncellenebilir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rol başarıyla güncellendi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri"),
            @ApiResponse(responseCode = "403", description = "SYSTEM_ADMIN yetkisi gerekli"),
            @ApiResponse(responseCode = "404", description = "Rol bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<RoleResponse>> updateRole(
            @Parameter(description = "Güncellenecek rolün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long roleId,
            @Parameter(description = "Güncelleme bilgileri", required = true)
            @RequestBody @Valid RoleRequest updateRequest) {

        try {
            RoleResponse updatedRole = roleService.updateRole(roleId, updateRequest);

            return ResponseEntity.ok(BaseResponse.success(updatedRole));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Rol siler
     */
    @DeleteMapping("/{roleId}")
    @Operation(
            summary = "Rol sil",
            description = "Belirtilen rolü siler (soft delete). Kullanıcılara atanmış roller silinemez."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rol başarıyla silindi"),
            @ApiResponse(responseCode = "400", description = "Rol kullanıcılara atanmış olduğu için silinemez"),
            @ApiResponse(responseCode = "403", description = "SYSTEM_ADMIN yetkisi gerekli"),
            @ApiResponse(responseCode = "404", description = "Rol bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<Void>> deleteRole(
            @Parameter(description = "Silinecek rolün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long roleId) {

        try {
            roleService.deleteRole(roleId);

            return ResponseEntity.ok(BaseResponse.success(null));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Rol ID ile getirir
     */
    @GetMapping("/{roleId}")
    @Operation(
            summary = "ID ile rol getir",
            description = "Belirtilen ID'ye sahip rolün detay bilgilerini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rol başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "SYSTEM_ADMIN yetkisi gerekli"),
            @ApiResponse(responseCode = "404", description = "Rol bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<RoleResponse>> getRoleById(
            @Parameter(description = "Rol ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long roleId) {

        try {
            RoleResponse role = roleService.getRoleById(roleId);
            return ResponseEntity.ok(BaseResponse.success(role));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Tüm rolleri listeler
     */
    @GetMapping
    @Operation(
            summary = "Tüm rolleri listele",
            description = "Sistemdeki tüm rolleri listeler. Permission'ları dahil etme seçeneği vardır."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Roller başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "SYSTEM_ADMIN yetkisi gerekli"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<List<RoleResponse>>> getAllRoles(
            @Parameter(description = "Permission'ları dahil et", example = "true")
            @RequestParam(defaultValue = "true") boolean includePermissions) {

        try {
            List<RoleResponse> roles = roleService.getAllRoles(includePermissions);
            return ResponseEntity.ok(BaseResponse.success(roles));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Rol özetlerini listeler (performans için)
     */
    @GetMapping("/summaries")
    @Operation(
            summary = "Rol özetlerini getir",
            description = "Dropdown ve select listeleri için rol ID ve isim özetlerini getirir. Permission'lar dahil edilmez."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rol özetleri başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "SYSTEM_ADMIN yetkisi gerekli"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<List<RoleSummary>>> getAllRoleSummaries() {

        try {
            List<RoleSummary> roleSummaries = roleService.getAllRoleSummaries();
            return ResponseEntity.ok(BaseResponse.success(roleSummaries));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Rolü geri yükler
     */
    @PostMapping("/restore/{roleId}")
    @Operation(
            summary = "Rol geri yükle",
            description = "Silinmiş olan rolü geri yükler ve aktif duruma getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rol başarıyla geri yüklendi"),
            @ApiResponse(responseCode = "403", description = "SYSTEM_ADMIN yetkisi gerekli"),
            @ApiResponse(responseCode = "404", description = "Rol bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<RoleResponse>> restoreRole(
            @Parameter(description = "Geri yüklenecek rolün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long roleId) {

        try {
            RoleResponse updatedRole = roleService.restoreRole(roleId);

            return ResponseEntity.ok(BaseResponse.success(updatedRole));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.FORBIDDEN.value()));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/active-simple")
    @Operation(
            summary = "Aktif rolleri basit formatta listele",
            description = "Sadece ID ve isim bilgisi ile aktif rolleri getirir. Dropdown ve select listeleri için optimize edilmiştir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Aktif roller başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "SYSTEM_ADMIN yetkisi gerekli"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null,'SYSTEM_ADMIN')")
    public ResponseEntity<BaseResponse<List<RoleSimple>>> getActiveRolesSimple() {
        try {
            logger.info("GET /api/roles/active-simple");
            List<RoleSimple> roles = roleService.getActiveRolesSimple();
            return ResponseEntity.ok(BaseResponse.success(roles));

        } catch (Exception e) {
            logger.severe("Error fetching active roles simple: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Aktif roller getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}
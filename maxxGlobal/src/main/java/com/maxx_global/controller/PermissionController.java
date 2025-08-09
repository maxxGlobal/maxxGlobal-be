package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.permission.PermissionResponse;
import com.maxx_global.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@Tag(name = "Permission Management", description = "Yetki yönetimi için API endpoint'leri. Permission'lar statik veridir, herkes okuyabilir.")
@SecurityRequirement(name = "Bearer Authentication")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * Tüm permission'ları listeler (herkes erişebilir - statik data)
     */
    @GetMapping
    @Operation(
            summary = "Tüm permission'ları listele",
            description = "Sistemdeki tüm yetkileri listeler. Permission'lar statik veri olduğu için herkes erişebilir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Permission'lar başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<List<PermissionResponse>>> getAllPermissions() {

        try {
            List<PermissionResponse> permissions = permissionService.getAllPermissions();
            return ResponseEntity.ok(BaseResponse.success(permissions));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Permission ID ile getirir
     */
    @GetMapping("/{permissionId}")
    @Operation(
            summary = "ID ile permission getir",
            description = "Belirtilen ID'ye sahip permission'ın detay bilgilerini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Permission başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Permission bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<PermissionResponse>> getPermissionById(
            @Parameter(description = "Permission ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long permissionId) {

        try {
            PermissionResponse permission = permissionService.getPermissionById(permissionId);
            return ResponseEntity.ok(BaseResponse.success(permission));

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
     * Permission name ile getirir
     */
    @GetMapping("/by-name/{permissionName}")
    @Operation(
            summary = "İsim ile permission getir",
            description = "Belirtilen isme sahip permission'ı getirir. Permission isimleri büyük/küçük harf duyarlıdır."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Permission başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Permission bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<BaseResponse<PermissionResponse>> getPermissionByName(
            @Parameter(description = "Permission adı", example = "USER_READ", required = true)
            @PathVariable String permissionName) {

        try {
            PermissionResponse permission = permissionService.getPermissionByName(permissionName);
            return ResponseEntity.ok(BaseResponse.success(permission));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}
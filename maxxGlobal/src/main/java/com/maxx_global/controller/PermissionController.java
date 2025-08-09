package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.permission.PermissionResponse;
import com.maxx_global.service.PermissionService;
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
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * Tüm permission'ları listeler (herkes erişebilir - statik data)
     */
    @GetMapping
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
    public ResponseEntity<BaseResponse<PermissionResponse>> getPermissionById(
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
    public ResponseEntity<BaseResponse<PermissionResponse>> getPermissionByName(
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
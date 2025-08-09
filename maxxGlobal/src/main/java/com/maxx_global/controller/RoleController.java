package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.permission.PermissionIdsRequest;
import com.maxx_global.dto.role.RoleRequest;
import com.maxx_global.dto.role.RoleResponse;
import com.maxx_global.dto.role.RoleSummary;
import com.maxx_global.entity.AppUser;
import com.maxx_global.service.AppUserService;
import com.maxx_global.service.RoleService;
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

@RestController
@RequestMapping("/api/roles")
@Validated
@PreAuthorize("hasPermission(null,'SYSTEM_ADMIN')")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * Yeni rol oluşturur
     */
    @PostMapping
    public ResponseEntity<BaseResponse<RoleResponse>> createRole(
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
    public ResponseEntity<BaseResponse<RoleResponse>> updateRole(
            @PathVariable @Min(1) Long roleId,
            @RequestBody @Valid RoleRequest updateRequest ) {

        try {
            RoleResponse updatedRole = roleService.updateRole(roleId, updateRequest );

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
    public ResponseEntity<BaseResponse<Void>> deleteRole(
            @PathVariable @Min(1) Long roleId ) {

        try {
            roleService.deleteRole(roleId );

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
    public ResponseEntity<BaseResponse<RoleResponse>> getRoleById(
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
    public ResponseEntity<BaseResponse<List<RoleResponse>>> getAllRoles(
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
     * Role permission ekleme
     */
    @PostMapping("/{roleId}/restore")
    public ResponseEntity<BaseResponse<RoleResponse>> addPermissionsToRole(
            @PathVariable @Min(1) Long roleId ) {

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
}
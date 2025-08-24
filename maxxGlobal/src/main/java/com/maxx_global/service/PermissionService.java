package com.maxx_global.service;

import com.maxx_global.dto.permission.PermissionResponse;
import com.maxx_global.entity.Permission;
import com.maxx_global.repository.PermissionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional()
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    /**
     * Tüm permission'ları listeler
     * @return Permission listesi
     */
    public List<PermissionResponse> getAllPermissions() {
        List<Permission> permissions = permissionRepository.findAllByOrderByIdAsc();
        return permissions.stream()
                .map(permission -> new PermissionResponse(
                        permission.getId(),
                        permission.getName(),
                        permission.getDescription(),
                        permission.getCreatedAt(),
                        permission.getStatus().getDisplayName()
                ))
                .sorted((p1, p2) -> p1.name().compareTo(p2.name())) // Alfabetik sıralama
                .toList();
    }

    /**
     * Permission ID ile getirir
     * @param permissionId Permission ID'si
     * @return Permission bilgisi
     */
    public PermissionResponse getPermissionById(Long permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException("Permission bulunamadı: " + permissionId));

        return new PermissionResponse(
                permission.getId(),
                permission.getName(),
                permission.getDescription(),
                permission.getCreatedAt(),
                permission.getStatus().getDisplayName()
        );
    }

    /**
     * Permission name ile getirir
     * @param permissionName Permission adı
     * @return Permission bilgisi
     */
    public PermissionResponse getPermissionByName(String permissionName) {
        Permission permission = permissionRepository.findByName(permissionName)
                .orElseThrow(() -> new EntityNotFoundException("Permission bulunamadı: " + permissionName));

        return new PermissionResponse(
                permission.getId(),
                permission.getName(),
                permission.getDescription(),
                permission.getCreatedAt(),
                permission.getStatus().getDisplayName()
        );
    }
}
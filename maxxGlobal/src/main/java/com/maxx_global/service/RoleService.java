package com.maxx_global.service;

import com.maxx_global.dto.permission.PermissionResponse;
import com.maxx_global.dto.role.*;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Permission;
import com.maxx_global.entity.Role;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.PermissionRepository;
import com.maxx_global.repository.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleService {

    private static final Logger logger = Logger.getLogger(RoleService.class.getName());

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper roleMapper;

    public RoleService(RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.roleMapper = roleMapper;
    }

    /**
     * Yeni rol oluşturur
     * @param roleRequest Rol bilgileri
     * @return Oluşturulan rol
     */
    public RoleResponse createRole(RoleRequest roleRequest) {
        // Rol adı benzersizlik kontrolü
        if (roleRepository.existsByName(roleRequest.name())) {
            throw new BadCredentialsException("Bu rol adı zaten mevcut: " + roleRequest.name());
        }

        // Entity'yi oluştur
        Role newRole = roleMapper.toEntity(roleRequest);

        // Yeni rol için varsayılan status
        newRole.setStatus(EntityStatus.valueOf(EntityStatus.ACTIVE.name()));

        // Permission'ları set et
        if (roleRequest.permissionIds() != null && !roleRequest.permissionIds().isEmpty()) {
            Set<Permission> permissions = new HashSet<>(
                    permissionRepository.findAllById(roleRequest.permissionIds())
            );

            if (permissions.size() != roleRequest.permissionIds().size()) {
                throw new EntityNotFoundException("Bir veya daha fazla permission bulunamadı");
            }

            newRole.setPermissions(permissions);
        }

        // Kaydet
        Role savedRole = roleRepository.save(newRole);
        return roleMapper.toDto(savedRole);
    }

    /**
     * Rol günceller
     * @param roleId Güncellenecek rol ID'si
     * @param updateRequest Güncelleme bilgileri
     * @return Güncellenmiş rol
     */
    public RoleResponse updateRole(Long roleId, RoleRequest updateRequest) {
        // Mevcut rolü bul
        Role existingRole = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Rol bulunamadı: " + roleId));

        // Rol adı benzersizlik kontrolü
        if (updateRequest.name() != null && !updateRequest.name().equals(existingRole.getName())) {
            if (roleRepository.existsByNameAndIdNot(updateRequest.name(), roleId)) {
                throw new BadCredentialsException("Bu rol adı zaten mevcut: " + updateRequest.name());
            }
        }

        // Temel bilgileri güncelle
        roleMapper.updateEntityFromRequest(updateRequest, existingRole);

        // Permission'ları güncelle
        if (updateRequest.permissionIds() != null) {
            if (updateRequest.permissionIds().isEmpty()) {
                existingRole.setPermissions(new HashSet<>());
            } else {
                Set<Permission> newPermissions = new HashSet<>(
                        permissionRepository.findAllById(updateRequest.permissionIds())
                );

                if (newPermissions.size() != updateRequest.permissionIds().size()) {
                    throw new EntityNotFoundException("Bir veya daha fazla permission bulunamadı");
                }

                existingRole.setPermissions(newPermissions);
            }
        }

        // Kaydet
        Role savedRole = roleRepository.save(existingRole);
        return roleMapper.toDto(savedRole);
    }

    /**
     * Rol siler (Soft Delete - status pasif yapar ve permission'ları koparır)
     * @param roleId Silinecek rol ID'si
     */
    public void deleteRole(Long roleId) {
        // Rolü bul
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Rol bulunamadı: " + roleId));

        // Zaten silinmiş mi kontrol et
        if (EntityStatus.DELETED.equals(role.getStatus())) {
            throw new BadCredentialsException("Bu rol zaten silinmiş veya pasif durumda");
        }

        // Rol kullanımda mı kontrol et (opsiyonel warning)
        if (roleRepository.isRoleInUse(roleId)) {
            // Log'lama yapılabilir ama engellenmez
            System.out.println("Warning: Role " + role.getName() + " is currently in use but will be deactivated");
        }

        // Permission'ları kopar
        role.setPermissions(new HashSet<>());

        role.setStatus(EntityStatus.valueOf(EntityStatus.DELETED.name()));

        // Hard delete yerine update
        roleRepository.save(role);

        System.out.println("Role " + role.getName() + " soft deleted and permissions cleared");
    }

    /**
     * Rol ID ile getirir (aktif olanlar)
     * @param roleId Rol ID'si
     * @return Rol bilgileri
     */
    public RoleResponse getRoleById(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Rol bulunamadı: " + roleId));

        // Silinmiş rol kontrolü
        if ("DELETED".equals(role.getStatus())) {
            throw new EntityNotFoundException("Bu rol silinmiş durumda: " + roleId);
        }

        return roleMapper.toDto(role);
    }
 
    /**
     * Tüm rolleri listeler (aktif olanlar)
     * @param includePermissions Permission detayları dahil edilsin mi
     * @return Rol listesi
     */
    public List<RoleResponse> getAllRoles(boolean includePermissions) {
        // Sadece aktif rolleri getir
        List<Role> roles = roleRepository.findAll();

        if (includePermissions) {
            return roleMapper.toDtoList(roles);
        } else {
            // Hafif versiyon için summary kullan
            return roles.stream()
                    .map(role -> new RoleResponse(
                            role.getId(),
                            role.getName(),
                            List.of(), // Permissions boş
                            role.getCreatedAt(),
                            role.getUpdatedAt()
                    ))
                    .toList();
        }
    }

    /**
     * Rol özetlerini listeler (performans için) - sadece aktif roller
     * @return Rol özet listesi
     */
    public List<RoleSummary> getAllRoleSummaries() {
        List<Role> roles = roleRepository.findAll();
        return roleMapper.toSummaryList(roles);
    }

    public RoleResponse restoreRole(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Rol bulunamadı: " + roleId));

        if (!EntityStatus.ACTIVE.name().equals(role.getStatus())) {
            throw new BadCredentialsException("Bu rol zaten aktif durumda");
        }

        // Status'u aktif yap
        role.setStatus(EntityStatus.valueOf(EntityStatus.ACTIVE.name()));
        Role restoredRole = roleRepository.save(role);

        return roleMapper.toDto(restoredRole);
    }

    /**
     * Silinmiş rolleri listeler
     * @param currentUser İşlemi yapan kullanıcı
     * @return Silinmiş rol listesi
     */
    public List<RoleResponse> getDeletedRoles(AppUser currentUser) {
        List<Role> deletedRoles = roleRepository.findDeletedRoles();
        return roleMapper.toDtoList(deletedRoles);
    }

    public Set<Role> findAllById(List<Long> ids) {
        Set<Role> newRoles = new HashSet<>(roleRepository.findAllById(ids));
        return newRoles;
    }

    public List<RoleSimple> getActiveRolesSimple() {
        logger.info("Fetching active roles simple format");
        List<Role> roles = roleRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);
        return roles.stream()
                .map(role -> new RoleSimple(role.getId(), role.getName()))
                .collect(Collectors.toList());
    }
}
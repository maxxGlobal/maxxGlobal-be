package com.maxx_global.dto.role;

import com.maxx_global.dto.BaseMapper;
import com.maxx_global.dto.permission.PermissionResponse;
import com.maxx_global.entity.Permission;
import com.maxx_global.entity.Role;
import com.maxx_global.enums.EntityStatus;
import org.mapstruct.*;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface RoleMapper extends BaseMapper<Role, RoleRequest, RoleResponse> {

    // --- Entity -> Response ---
    @Override
    @Mapping(target = "permissions", source = "permissions", qualifiedByName = "mapPermissionsToResponse")
    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusToDisplayName") // DEĞIŞEN SATIR
    RoleResponse toDto(Role role);

    // --- Request -> Entity ---
    @Override
    @Mapping(target = "permissions", ignore = true) // permissions servis katmanında set edilecek
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Role toEntity(RoleRequest request);

    // --- Update Request -> Entity (Partial Update) ---
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "permissions", ignore = true) // permissions servis katmanında set edilecek
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(RoleRequest updateRequest, @MappingTarget Role existingRole);

    // --- Entity -> Summary ---
    @Mapping(target = "permissionCount", expression = "java(role.getPermissions() != null ? role.getPermissions().size() : 0)")
    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusToDisplayName") // DEĞIŞEN SATIR
    RoleSummary toSummary(Role role);

    // --- Permission Entity -> Response ---
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusToDisplayName") // DEĞIŞEN SATIR
    PermissionResponse permissionToResponse(Permission permission);

    // --- List Mappings ---
    List<RoleResponse> toDtoList(List<Role> roles);
    List<RoleSummary> toSummaryList(List<Role> roles);
    List<PermissionResponse> permissionListToResponseList(List<Permission> permissions);

    // --- Custom Mapping Methods ---
    @Named("mapPermissionsToResponse")
    default List<PermissionResponse> mapPermissionsToResponse(Set<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }

        return permissions.stream()
                .map(this::permissionToResponse)
                .sorted((p1, p2) -> p1.name().compareTo(p2.name())) // Alfabetik sıralama
                .toList();
    }
    // YENİ EKLENEN - Türkçe status mapping
    @Named("mapStatusToDisplayName")
    default String mapStatusToDisplayName(EntityStatus status) {
        return status != null ? status.getDisplayName() : null;
    }
}
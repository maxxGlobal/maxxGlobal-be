package com.maxx_global.dto.role;

import com.maxx_global.dto.permission.PermissionResponse;

import java.time.LocalDateTime;
import java.util.List;

public record RoleResponse(
        Long id,
        String name,
        List<PermissionResponse> permissions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String status
) {}

package com.maxx_global.dto.role;

import com.maxx_global.dto.permission.PermissionResponse;

import java.util.List;

public record RoleResponse(
        String name,
        List<PermissionResponse> permissions
) {}

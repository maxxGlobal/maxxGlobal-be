package com.maxx_global.dto.role;

public record RoleSummary(
        Long id,
        String name,
        int permissionCount,
        String status
) {}
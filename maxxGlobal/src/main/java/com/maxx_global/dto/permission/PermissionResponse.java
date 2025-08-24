package com.maxx_global.dto.permission;

import java.time.LocalDateTime;

public record PermissionResponse(
        Long id,
        String name,
        String description,
        LocalDateTime createdAt,
        String status
) {}

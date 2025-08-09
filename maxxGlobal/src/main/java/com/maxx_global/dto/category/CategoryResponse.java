package com.maxx_global.dto.category;

import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String parentCategoryName, // Parent varsa adı
        LocalDateTime createdAt,
        String status
) {}

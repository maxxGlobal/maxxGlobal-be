package com.maxx_global.dto.category;

import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String nameEn,
        String description,
        String descriptionEn,
        String parentCategoryName, // Parent varsa adı
        String parentCategoryNameEn,
        LocalDateTime createdAt,
        String status
) {}


package com.maxx_global.dto.category;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
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


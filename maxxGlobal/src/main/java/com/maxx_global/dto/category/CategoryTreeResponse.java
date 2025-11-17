package com.maxx_global.dto.category;

import java.util.List;

public record CategoryTreeResponse(
        Long id,
        String name,
        String nameEn,
        String description,
        String descriptionEn,
        Long parentCategoryId,  // YENİ - Parent ID
        String parentCategoryName,  // YENİ - Parent adı (opsiyonel)
        String parentCategoryNameEn,
        Boolean hasChildren,
        List<CategoryTreeResponse> children
) {}

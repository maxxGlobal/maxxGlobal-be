package com.maxx_global.dto.category;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
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

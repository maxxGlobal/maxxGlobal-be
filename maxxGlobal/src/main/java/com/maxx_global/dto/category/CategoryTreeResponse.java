package com.maxx_global.dto.category;

import java.util.List;

public record CategoryTreeResponse(
        Long id,
        String name,
        Long parentCategoryId,  // YENİ - Parent ID
        String parentCategoryName,  // YENİ - Parent adı (opsiyonel)
        Boolean hasChildren,
        List<CategoryTreeResponse> children
) {}
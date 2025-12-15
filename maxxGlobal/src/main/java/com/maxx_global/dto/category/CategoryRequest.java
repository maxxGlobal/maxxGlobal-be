package com.maxx_global.dto.category;

public record CategoryRequest(
        String name,
        String nameEn,
        String description,
        String descriptionEn,
        Long parentCategoryId // Alt kategori oluşturuluyorsa üst kategori ID’si
) {}

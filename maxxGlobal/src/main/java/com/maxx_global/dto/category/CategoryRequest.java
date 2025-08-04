package com.maxx_global.dto.category;

public record CategoryRequest(
        String name,
        Long parentCategoryId // Alt kategori oluşturuluyorsa üst kategori ID’si
) {}

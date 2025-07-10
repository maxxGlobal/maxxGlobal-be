package com.maxx_global.dto.product;

import java.util.List;

public record ProductCreateRequest(
        String name,
        String code,
        String lotNumber,
        Boolean sterile,
        String material,
        String unit,
        Integer stockQuantity,
        Long categoryId,
        List<String> imageUrls
) {}

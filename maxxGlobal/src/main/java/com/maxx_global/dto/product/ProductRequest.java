package com.maxx_global.dto.product;

import com.maxx_global.dto.productImage.ProductImageRequest;

import java.util.List;

public record ProductRequest(
        String name,
        String code,
        String lotNumber,
        Boolean sterile,
        String material,
        String unit,
        Integer stockQuantity,
        Long categoryId,
        List<ProductImageRequest> images
) {}

package com.maxx_global.dto.product;

import com.maxx_global.dto.productImage.ProductImageResponse;
import com.maxx_global.dto.productPrice.ProductPriceResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record ProductResponse(
        Long id,
        String name,
        String code,
        String lotNumber,
        Boolean sterile,
        String material,
        String unit,
        Integer stockQuantity,
        String categoryName,
        Set<ProductImageResponse> images,
        Set<ProductPriceResponse> prices
) {}


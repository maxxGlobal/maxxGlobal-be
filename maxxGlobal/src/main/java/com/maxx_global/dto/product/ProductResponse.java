package com.maxx_global.dto.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String code,
        String lotNumber,
        Boolean sterile,
        String material,
        String unit,
        Integer stockQuantity,
        List<String> imageUrls,
        String categoryName,
        BigDecimal priceUSD,
        BigDecimal priceTRY,
        BigDecimal priceEUR,
        String createdBy,
        LocalDateTime createdDate
) {}

package com.maxx_global.dto.product;

public record ProductStatistics(
        Long totalProducts,
        Long inStockProducts,
        Long outOfStockProducts,
        Long expiredProducts
) {}
package com.maxx_global.dto.productPrice;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Ürün varyantı için fiyat detayları")
public record VariantPriceDetail(
        @Schema(description = "Varyant ID'si", example = "101")
        Long variantId,

        @Schema(description = "Varyant SKU", example = "TI-001-4.5MM")
        String variantSku,

        @Schema(description = "Varyant boyutu", example = "4.5MM")
        String variantSize,

        @Schema(description = "Para birimine göre fiyat listesi")
        List<PriceInfo> prices
) {}

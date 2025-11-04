package com.maxx_global.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Basit ürün varyant bilgisi")
public record ProductSimpleVariant(
        @Schema(description = "Varyant ID'si", example = "10")
        Long id,

        @Schema(description = "Varyant boyutu", example = "M")
        String size,

        @Schema(description = "Varyant SKU", example = "TI-001-M")
        String sku,

        @Schema(description = "Varyantın stok miktarı", example = "25")
        Integer stockQuantity,

        @Schema(description = "Varsayılan varyant mı?", example = "true")
        Boolean isDefault
) {}

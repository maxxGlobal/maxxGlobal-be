package com.maxx_global.dto.productVariant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ürün varyantı özet bilgisi - indirim seçimleri için")
public record ProductVariantSummary(
        @Schema(description = "Varyant ID'si", example = "101")
        Long id,
        @Schema(description = "Ürünün ID'si", example = "10")
        Long productId,
        @Schema(description = "Ürün adı", example = "Titanyum İmplant")
        String productName,
        @Schema(description = "Varyant boyutu", example = "M")
        String size,
        @Schema(description = "Varyant SKU bilgisi", example = "TI-001-M")
        String sku
) {
        public String displayName() {
                StringBuilder builder = new StringBuilder();
                if (productName != null) {
                        builder.append(productName);
                }
                if (size != null && !size.isBlank()) {
                        if (builder.length() > 0) {
                                builder.append(" - ");
                        }
                        builder.append(size);
                }
                if (sku != null && !sku.isBlank()) {
                        builder.append(" (").append(sku).append(")");
                }
                return builder.length() == 0 ? "Varyant" : builder.toString();
        }
}

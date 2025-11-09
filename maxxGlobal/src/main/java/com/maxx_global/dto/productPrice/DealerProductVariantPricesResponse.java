package com.maxx_global.dto.productPrice;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Belirli bir bayi için ürün varyant fiyatları yanıt modeli")
public record DealerProductVariantPricesResponse(
        @Schema(description = "Ürün ID'si", example = "1")
        Long productId,

        @Schema(description = "Ürün adı", example = "Titanyum İmplant")
        String productName,

        @Schema(description = "Ürün kodu", example = "TI-001")
        String productCode,

        @Schema(description = "Bayi ID'si", example = "10")
        Long dealerId,

        @Schema(description = "Bayi adı", example = "ABC Medikal Ltd.")
        String dealerName,

        @Schema(description = "Ürün varyant fiyat listesi")
        List<VariantPriceDetail> variants
) {}

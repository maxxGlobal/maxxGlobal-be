package com.maxx_global.dto.discount;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "İndirim hesaplama sonucu")
public record DiscountCalculationResponse(

        @Schema(description = "Varyant ID'si", example = "1")
        Long variantId,

        @Schema(description = "Varyant adı", example = "Titanyum İmplant - 4.0mm")
        String variantName,

        @Schema(description = "Ürün ID'si", example = "10")
        Long productId,

        @Schema(description = "Ürün adı", example = "Titanyum İmplant")
        String productName,

        @Schema(description = "Bayi ID'si", example = "1")
        Long dealerId,

        @Schema(description = "Bayi adı", example = "ABC Medikal")
        String dealerName,

        @Schema(description = "Orijinal birim fiyat", example = "100.00")
        BigDecimal originalUnitPrice,

        @Schema(description = "Miktar", example = "5")
        Integer quantity,

        @Schema(description = "Orijinal toplam tutar", example = "500.00")
        BigDecimal originalTotalAmount,

        @Schema(description = "Uygulanabilir indirimler")
        List<ApplicableDiscountInfo> applicableDiscounts,

        @Schema(description = "En iyi indirim")
        ApplicableDiscountInfo bestDiscount,

        @Schema(description = "Toplam indirim tutarı", example = "75.00")
        BigDecimal totalDiscountAmount,

        @Schema(description = "İndirimli toplam tutar", example = "425.00")
        BigDecimal finalTotalAmount,

        @Schema(description = "İndirim yüzdesi", example = "15.00")
        BigDecimal discountPercentage,

        @Schema(description = "Tasarruf tutarı", example = "75.00")
        BigDecimal savingsAmount

) {}
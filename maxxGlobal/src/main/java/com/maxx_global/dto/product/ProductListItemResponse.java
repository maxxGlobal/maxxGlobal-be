package com.maxx_global.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Ürün liste öğesi - dealer fiyatı ile")
public record ProductListItemResponse(
        @Schema(description = "Ürün ID'si", example = "1")
        Long id,

        @Schema(description = "Ürün adı", example = "Titanyum İmplant")
        String name,

        @Schema(description = "Ürün kodu", example = "TI-001")
        String code,

        @Schema(description = "Kategori adı", example = "Dental İmplantlar")
        String categoryName,

        @Schema(description = "Ana resim URL'si")
        String primaryImageUrl,

        @Schema(description = "Stok miktarı", example = "100")
        Integer stockQuantity,

        @Schema(description = "Birim", example = "adet")
        String unit,

        @Schema(description = "Stokta var mı?", example = "true")
        Boolean isInStock,

        @Schema(description = "Süresi dolmuş mu?", example = "false")
        Boolean isExpired,

        @Schema(description = "Bu dealer için varsayılan fiyat")
        BigDecimal dealerPrice,

        @Schema(description = "Para birimi", example = "TRY")
        String currency,

        @Schema(description = "Fiyat geçerli mi?", example = "true")
        Boolean priceValid,

        @Schema(description = "Son kullanma tarihi", example = "2027-01-15")
        LocalDate expiryDate

) {}
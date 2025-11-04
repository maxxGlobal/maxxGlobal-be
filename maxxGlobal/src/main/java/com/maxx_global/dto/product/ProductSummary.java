package com.maxx_global.dto.product;

import com.maxx_global.dto.productPrice.ProductPriceInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Ürün özet bilgileri - listeler ve dropdown'lar için")
public record ProductSummary(
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

        @Schema(description = "Ürün aktif mi?", example = "true")
        Boolean isActive,

        @Schema(description = "Stokta var mı?", example = "true")
        Boolean isInStock,

        @Schema(description = "Ürün statüsü", example = "Aktif")
        String status,

        @Schema(description = "Kullanıcının favorisinde mi?", example = "true")
        Boolean isFavorite



){
}

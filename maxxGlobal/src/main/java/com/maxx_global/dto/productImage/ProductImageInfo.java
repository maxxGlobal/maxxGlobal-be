package com.maxx_global.dto.productImage;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ürün resim bilgisi")
public record ProductImageInfo(
        @Schema(description = "Resim ID'si", example = "1")
        Long id,

        @Schema(description = "Resim URL'si", example = "https://example.com/images/product1.jpg")
        String imageUrl,

        @Schema(description = "Ana resim mi?", example = "true")
        Boolean isPrimary
){
}

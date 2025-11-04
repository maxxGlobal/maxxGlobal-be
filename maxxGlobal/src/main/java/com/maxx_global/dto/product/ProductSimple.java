package com.maxx_global.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Ürün basit bilgiler - dropdown ve select listeleri için")
public record ProductSimple(
        @Schema(description = "Ürün ID'si", example = "1")
        Long id,

        @Schema(description = "Ürün adı", example = "Titanyum İmplant")
        String name,

        @Schema(description = "Ürün kodu", example = "TI-001")
        String code,

        @Schema(description = "Resim URL'si")
        String imageUrl,

        @Schema(description = "Ürünün aktif varyantları")
        List<ProductSimpleVariant> variants
) {}
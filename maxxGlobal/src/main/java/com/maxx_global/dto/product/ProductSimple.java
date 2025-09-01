package com.maxx_global.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ürün basit bilgiler - dropdown ve select listeleri için")
public record ProductSimple(
        @Schema(description = "Ürün ID'si", example = "1")
        Long id,

        @Schema(description = "Ürün adı", example = "Titanyum İmplant")
        String name,

        @Schema(description = "Ürün kodu", example = "TI-001")
        String code
) {}
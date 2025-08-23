package com.maxx_global.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aranabilir ürün field bilgisi")
public record ProductSearchField(
        @Schema(description = "Field adı", example = "code")
        String fieldName,

        @Schema(description = "Field açıklaması", example = "Ürün Kodu")
        String displayName,

        @Schema(description = "Field tipi", example = "STRING")
        String fieldType,

        @Schema(description = "Partial search destekliyor mu?", example = "true")
        Boolean supportsPartialMatch,

        @Schema(description = "Örnek değer", example = "TI-001")
        String exampleValue
) {}
package com.maxx_global.dto.productPriceExcell;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Excel import hata detayı")
public record PriceImportError(
        @Schema(description = "Satır numarası", example = "15")
        Integer rowNumber,

        @Schema(description = "Ürün kodu", example = "TI-001")
        String productCode,

        @Schema(description = "Hata mesajı")
        String errorMessage,

        @Schema(description = "Satır verisi")
        String rowData
) {}
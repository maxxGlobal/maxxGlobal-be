package com.maxx_global.dto.productPrice;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Bayi fiyat bilgisi")
public record DealerPriceInfo(
        @Schema(description = "Bayi ID'si", example = "1")
        Long dealerId,

        @Schema(description = "Bayi adı", example = "ABC Medikal Ltd.")
        String dealerName,

        @Schema(description = "Fiyat miktarı", example = "150.75")
        BigDecimal amount,

        @Schema(description = "Şu an geçerli mi?", example = "true")
        Boolean isValidNow
){}
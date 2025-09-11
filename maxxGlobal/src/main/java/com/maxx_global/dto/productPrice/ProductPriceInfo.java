package com.maxx_global.dto.productPrice;

import com.maxx_global.enums.CurrencyType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Ürün fiyat bilgisi - response için")
public record ProductPriceInfo(
        @Schema(description = "Fiyat ID'si", example = "11")
        Long productPriceId,

        @Schema(description = "Para birimi", example = "TRY")
        CurrencyType currency,

        @Schema(description = "Fiyat miktarı", example = "150.75")
        BigDecimal amount
) {}
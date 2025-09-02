package com.maxx_global.dto.productPrice;

import com.maxx_global.enums.CurrencyType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Para birimi ve fiyat bilgisi")
public record PriceInfo(
        @Schema(description = "Para birimi", example = "TRY")
        CurrencyType currency,

        @Schema(description = "Fiyat miktarı", example = "150.75")
        BigDecimal amount
) {}
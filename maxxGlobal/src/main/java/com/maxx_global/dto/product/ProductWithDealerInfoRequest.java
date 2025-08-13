package com.maxx_global.dto.product;

import com.maxx_global.enums.CurrencyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Dealer bilgisi ile ürün istek modeli")
public record ProductWithDealerInfoRequest(

        @Schema(description = "Dealer ID'si", example = "1", required = true)
        @NotNull(message = "Dealer ID is required")
        @Min(value = 1, message = "Dealer ID must be greater than 0")
        Long dealerId,

        @Schema(description = "Para birimi", example = "TRY")
        CurrencyType currency

) {
    // Default values
    public CurrencyType currency() {
        return currency != null ? currency : CurrencyType.TRY;
    }
}
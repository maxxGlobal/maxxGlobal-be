package com.maxx_global.dto.productPrice;

import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.PriceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Ürün fiyatı özet bilgileri - dropdown ve listeler için")
public record ProductPriceSummary(

        @Schema(description = "Fiyat ID'si", example = "1")
        Long id,

        @Schema(description = "Ürün adı", example = "Titanyum İmplant")
        String productName,

        @Schema(description = "Bayi adı", example = "ABC Medikal Ltd.")
        String dealerName,

        @Schema(description = "Para birimi", example = "TRY")
        CurrencyType currency,

        @Schema(description = "Fiyat tipi", example = "PRICE_1")
        PriceType priceType,

        @Schema(description = "Fiyat miktarı", example = "150.75")
        BigDecimal amount,

        @Schema(description = "Şu an geçerli mi?", example = "true")
        Boolean isValidNow
){}
package com.maxx_global.dto.productPrice;

import com.maxx_global.enums.CurrencyType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Bayiler arası fiyat karşılaştırması için model")
public record DealerPriceComparison(

        @Schema(description = "Ürün ID'si", example = "1")
        Long productId,

        @Schema(description = "Ürün adı", example = "Titanyum İmplant")
        String productName,

        @Schema(description = "Para birimi", example = "TRY")
        CurrencyType currency,

        @Schema(description = "Bayiler ve fiyatları")
        List<DealerPriceInfo> dealerPrices
){}
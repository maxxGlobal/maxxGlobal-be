package com.maxx_global.dto.dealer;

import com.maxx_global.enums.CurrencyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Dealer bazlı ürün arama kriterleri")
public record DealerProductSearchRequest(

        @Schema(description = "Dealer ID'si", example = "1", required = true)
        @NotNull(message = "Dealer ID is required")
        @Min(value = 1, message = "Dealer ID must be greater than 0")
        Long dealerId,

        @Schema(description = "Para birimi", example = "TRY")
        CurrencyType currency,

        @Schema(description = "Arama terimi", example = "implant")
        String searchTerm,

        @Schema(description = "Kategori ID'leri")
        List<Long> categoryIds,

        @Schema(description = "Minimum fiyat")
        BigDecimal minPrice,

        @Schema(description = "Maksimum fiyat")
        BigDecimal maxPrice,

        @Schema(description = "Sadece stokta olanlar", example = "true")
        Boolean inStockOnly,

        @Schema(description = "Sadece fiyatı olan ürünler", example = "true")
        Boolean withPriceOnly,

        @Schema(description = "Sadece geçerli fiyatlı ürünler", example = "true")
        Boolean validPricesOnly

) {
    // Default values
    public CurrencyType currency() {
        return currency != null ? currency : CurrencyType.TRY;
    }

    public Boolean inStockOnly() {
        return inStockOnly != null ? inStockOnly : false;
    }

    public Boolean withPriceOnly() {
        return withPriceOnly != null ? withPriceOnly : false;
    }

    public Boolean validPricesOnly() {
        return validPricesOnly != null ? validPricesOnly : true;
    }
}
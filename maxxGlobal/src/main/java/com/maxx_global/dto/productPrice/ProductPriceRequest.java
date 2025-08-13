package com.maxx_global.dto.productPrice;

import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.PriceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Ürün fiyatı oluşturma ve güncelleme için istek modeli")
public record ProductPriceRequest(

        @Schema(description = "Ürün ID'si", example = "1", required = true)
        @NotNull(message = "Product ID is required")
        @Min(value = 1, message = "Product ID must be greater than 0")
        Long productId,

        @Schema(description = "Bayi ID'si", example = "1", required = true)
        @NotNull(message = "Dealer ID is required")
        @Min(value = 1, message = "Dealer ID must be greater than 0")
        Long dealerId,

        @Schema(description = "Para birimi", example = "TRY", required = true)
        @NotNull(message = "Currency is required")
        CurrencyType currency,

        @Schema(description = "Fiyat tipi", example = "PRICE_1", required = true)
        @NotNull(message = "Price type is required")
        PriceType priceType,

        @Schema(description = "Fiyat miktarı", example = "150.75", required = true)
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 8, fraction = 2, message = "Amount format is invalid")
        BigDecimal amount,

        @Schema(description = "Geçerlilik başlangıç tarihi (kampanya için)", example = "2025-01-01T00:00:00")
        LocalDateTime validFrom,

        @Schema(description = "Geçerlilik bitiş tarihi (kampanya için)", example = "2025-12-31T23:59:59")
        LocalDateTime validUntil,

        @Schema(description = "Fiyat aktif mi?", example = "true")
        Boolean isActive

) {
    // Custom validation
    public void validate() {
        if (validFrom != null && validUntil != null && validFrom.isAfter(validUntil)) {
            throw new IllegalArgumentException("Valid from date cannot be after valid until date");
        }

        if (validUntil != null && validUntil.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Valid until date cannot be in the past");
        }
    }
}
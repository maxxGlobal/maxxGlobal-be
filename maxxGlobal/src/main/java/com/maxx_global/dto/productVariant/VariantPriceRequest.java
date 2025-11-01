package com.maxx_global.dto.productVariant;

import com.maxx_global.enums.CurrencyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Variant fiyat bilgisi - request için (ID otomatik atanır)")
public record VariantPriceRequest(
        @Schema(description = "Dealer ID (opsiyonel - null ise genel fiyat)", example = "5")
        Long dealerId,

        @Schema(description = "Para birimi", example = "TRY", required = true)
        @NotNull(message = "Para birimi gereklidir")
        CurrencyType currency,

        @Schema(description = "Fiyat miktarı", example = "150.75", required = true)
        @NotNull(message = "Fiyat miktarı gereklidir")
        @DecimalMin(value = "0.0", inclusive = false, message = "Fiyat 0'dan büyük olmalıdır")
        BigDecimal amount,

        @Schema(description = "Geçerlilik başlangıç tarihi (opsiyonel)", example = "2024-01-01T00:00:00")
        LocalDateTime validFrom,

        @Schema(description = "Geçerlilik bitiş tarihi (opsiyonel)", example = "2024-12-31T23:59:59")
        LocalDateTime validTo
) {}
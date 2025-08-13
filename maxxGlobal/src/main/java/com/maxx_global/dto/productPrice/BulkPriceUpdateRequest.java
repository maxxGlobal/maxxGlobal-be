package com.maxx_global.dto.productPrice;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Toplu fiyat güncelleme için istek modeli")
public record BulkPriceUpdateRequest(

        @Schema(description = "Güncellenecek fiyat ID'leri", required = true)
        @NotEmpty(message = "Price IDs cannot be empty")
        @Size(max = 100, message = "Maximum 100 prices can be updated at once")
        List<Long> priceIds,

        @Schema(description = "Yeni fiyat miktarı (tüm seçili fiyatlar için)")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        BigDecimal newAmount,

        @Schema(description = "Yüzde artış/azalış", example = "10.5")
        @DecimalMin(value = "-100", message = "Percentage cannot be less than -100")
        @DecimalMax(value = "1000", message = "Percentage cannot be more than 1000")
        BigDecimal percentageChange,

        @Schema(description = "Yeni aktiflik durumu")
        Boolean isActive,

        @Schema(description = "Yeni geçerlilik başlangıç tarihi")
        LocalDateTime validFrom,

        @Schema(description = "Yeni geçerlilik bitiş tarihi")
        LocalDateTime validUntil

) {
    public void validate() {
        if (newAmount == null && percentageChange == null && isActive == null &&
                validFrom == null && validUntil == null) {
            throw new IllegalArgumentException("At least one field must be provided for update");
        }

        if (newAmount != null && percentageChange != null) {
            throw new IllegalArgumentException("Cannot specify both newAmount and percentageChange");
        }
    }
}
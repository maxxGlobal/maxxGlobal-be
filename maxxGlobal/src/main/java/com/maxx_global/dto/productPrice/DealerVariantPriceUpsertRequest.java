package com.maxx_global.dto.productPrice;

import com.maxx_global.enums.CurrencyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Schema(description = "Bir bayinin ürün varyant fiyatlarını kaydetmek veya güncellemek için istek modeli")
public record DealerVariantPriceUpsertRequest(
        @Schema(description = "Güncellenecek varyant fiyatları listesi", required = true)
        @NotEmpty(message = "En az bir varyant fiyatı sağlanmalıdır")
        @Valid
        List<VariantPriceUpsertItem> variants
) {

    public void validate() {
        variants.forEach(VariantPriceUpsertItem::validate);
    }

    @Schema(description = "Belirli bir varyant için gönderilen fiyat bilgileri")
    public static record VariantPriceUpsertItem(
            @Schema(description = "Ürün varyant ID'si", example = "101", required = true)
            @NotNull(message = "Varyant ID'si gereklidir")
            Long variantId,

            @Schema(description = "Para birimine göre fiyatlar", required = true)
            @NotEmpty(message = "Varyant için en az bir fiyat girilmelidir")
            @Valid
            List<VariantCurrencyPriceUpsert> prices
    ) {
        public void validate() {
            Set<CurrencyType> seenCurrencies = new HashSet<>();
            for (VariantCurrencyPriceUpsert price : prices) {
                if (!seenCurrencies.add(price.currency())) {
                    throw new IllegalArgumentException("Varyant için bir para birimi birden fazla kez gönderildi: "
                            + price.currency());
                }
                price.validate();
            }
        }
    }

    @Schema(description = "Bir varyant için para birimi bazında fiyat bilgisi")
    public static record VariantCurrencyPriceUpsert(
            @Schema(description = "Para birimi", example = "TRY", required = true)
            @NotNull(message = "Para birimi gereklidir")
            CurrencyType currency,

            @Schema(description = "Fiyat miktarı", example = "150.75", required = true)
            @NotNull(message = "Fiyat miktarı gereklidir")
            @DecimalMin(value = "0.01", message = "Fiyat 0'dan büyük olmalıdır")
            @Digits(integer = 8, fraction = 2, message = "Fiyat formatı geçersiz")
            BigDecimal amount,

            @Schema(description = "Geçerlilik başlangıç tarihi", example = "2024-01-01T00:00:00")
            LocalDateTime validFrom,

            @Schema(description = "Geçerlilik bitiş tarihi", example = "2024-12-31T23:59:59")
            LocalDateTime validUntil,

            @Schema(description = "Fiyat aktif mi?", example = "true")
            Boolean isActive
    ) {
        public void validate() {
            if (validFrom != null && validUntil != null && validFrom.isAfter(validUntil)) {
                throw new IllegalArgumentException("Geçerlilik başlangıç tarihi bitiş tarihinden sonra olamaz");
            }
        }
    }
}

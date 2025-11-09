package com.maxx_global.dto.productPrice;

import com.maxx_global.enums.CurrencyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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

            @Schema(description = "Fiyat miktarı. Null gönderildiğinde mevcut kayıt silinir", example = "150.75")
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

            if (amount != null) {
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Fiyat 0'dan büyük olmalıdır");
                }

                BigDecimal normalized = amount.stripTrailingZeros();
                int scale = Math.max(normalized.scale(), 0);
                if (scale > 2) {
                    throw new IllegalArgumentException("Fiyat en fazla iki ondalık basamak içermelidir");
                }

                int precision = normalized.precision();
                int integerDigits = precision - scale;
                if (integerDigits > 8) {
                    throw new IllegalArgumentException("Fiyat 8 haneden uzun olamaz");
                }
            }
        }
    }
}

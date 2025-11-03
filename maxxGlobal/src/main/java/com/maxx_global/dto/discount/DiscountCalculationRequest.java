package com.maxx_global.dto.discount;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "İndirim hesaplama için istek modeli")
public record DiscountCalculationRequest(

        @Schema(description = "Ürün ID'si", example = "1")
        @Min(value = 1, message = "Ürün ID'si 1'den büyük olmalıdır")
        Long productId,

        @Schema(description = "Varyant ID'si", example = "10")
        @Min(value = 1, message = "Varyant ID'si 1'den büyük olmalıdır")
        Long variantId,

        @Schema(description = "Bayi ID'si", example = "1", required = true)
        @NotNull(message = "Bayi ID'si gereklidir")
        @Min(value = 1, message = "Bayi ID'si 1'den büyük olmalıdır")
        Long dealerId,

        @Schema(description = "Ürün miktarı", example = "5", required = true)
        @NotNull(message = "Miktar gereklidir")
        @Min(value = 1, message = "Miktar 1'den büyük olmalıdır")
        Integer quantity,

        @Schema(description = "Birim fiyat", example = "100.00", required = true)
        @NotNull(message = "Birim fiyat gereklidir")
        @DecimalMin(value = "0.01", message = "Birim fiyat 0'dan büyük olmalıdır")
        BigDecimal unitPrice,

        @Schema(description = "Toplam sipariş tutarı", example = "500.00")
        @DecimalMin(value = "0", message = "Toplam tutar negatif olamaz")
        BigDecimal totalOrderAmount,

        @Schema(description = "Dahil edilecek indirim ID'leri (boşsa tümü)")
        List<Long> includeDiscountIds,

        @Schema(description = "Hariç tutulacak indirim ID'leri")
        List<Long> excludeDiscountIds

) {}
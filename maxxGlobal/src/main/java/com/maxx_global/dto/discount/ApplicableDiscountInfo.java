package com.maxx_global.dto.discount;

import com.maxx_global.enums.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Uygulanabilir indirim bilgisi")
public record ApplicableDiscountInfo(

        @Schema(description = "İndirim ID'si", example = "1")
        Long discountId,

        @Schema(description = "İndirim adı", example = "Kış Kampanyası")
        String discountName,

        @Schema(description = "İndirim tipi", example = "PERCENTAGE")
        DiscountType discountType,

        @Schema(description = "İndirim değeri", example = "15.00")
        BigDecimal discountValue,

        @Schema(description = "Hesaplanan indirim tutarı", example = "75.00")
        BigDecimal calculatedDiscountAmount,

        @Schema(description = "İndirimli birim fiyat", example = "85.00")
        BigDecimal discountedUnitPrice,

        @Schema(description = "Minimum sipariş tutarı şartı sağlandı mı?", example = "true")
        Boolean minimumOrderMet,

        @Schema(description = "Maksimum indirim limiti uygulandı mı?", example = "false")
        Boolean maximumDiscountApplied,

        @Schema(description = "İndirim uygulanabilir mi?", example = "true")
        Boolean isApplicable

) {}

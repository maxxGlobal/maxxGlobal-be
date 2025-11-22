package com.maxx_global.dto.discount;

import com.maxx_global.enums.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "İndirim özet bilgileri - dropdown ve listeler için")
public record DiscountSummary(

        @Schema(description = "İndirim ID'si", example = "1")
        Long id,

        @Schema(description = "İndirim adı", example = "Kış Kampanyası")
        String name,

        @Schema(description = "İndirim adı (İngilizce)", example = "Winter Campaign")
        String nameEn,

        @Schema(description = "İndirim tipi", example = "PERCENTAGE")
        DiscountType discountType,

        @Schema(description = "İndirim değeri", example = "15.50")
        BigDecimal discountValue,

        @Schema(description = "Bitiş tarihi", example = "2024-12-31T23:59:59")
        LocalDateTime endDate,

        @Schema(description = "Şu an geçerli mi?", example = "true")
        Boolean isValidNow
) {}
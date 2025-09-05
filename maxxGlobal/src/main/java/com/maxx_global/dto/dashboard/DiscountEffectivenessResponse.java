package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "İndirim etkinliği")
public record DiscountEffectivenessResponse(
        @Schema(description = "Tarih aralığı")
        String period,

        @Schema(description = "İndirim listesi")
        List<DiscountEffectivenessData> discounts
) {}
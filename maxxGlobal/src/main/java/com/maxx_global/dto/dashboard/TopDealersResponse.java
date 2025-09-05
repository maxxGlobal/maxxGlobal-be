package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "En çok sipariş veren bayiler")
public record TopDealersResponse(
        @Schema(description = "Tarih aralığı")
        String period,

        @Schema(description = "Bayi listesi")
        List<TopDealerData> dealers
) {}
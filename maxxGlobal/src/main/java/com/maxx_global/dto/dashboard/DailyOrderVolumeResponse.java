package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Günlük sipariş hacmi")
public record DailyOrderVolumeResponse(
        @Schema(description = "Tarih aralığı")
        String period,

        @Schema(description = "Günlük veriler")
        List<DailyOrderData> dailyData,

        @Schema(description = "Ortalama günlük sipariş", example = "12.5")
        Double averageDailyOrders,

        @Schema(description = "En yoğun gün")
        String peakDay
) {}
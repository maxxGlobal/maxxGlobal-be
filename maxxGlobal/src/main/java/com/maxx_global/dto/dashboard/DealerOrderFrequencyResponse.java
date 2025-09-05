package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Bayi sipariş sıklığı")
public record DealerOrderFrequencyResponse(
        @Schema(description = "Tarih aralığı")
        String period,

        @Schema(description = "Sıklık verileri")
        List<DealerFrequencyData> frequencyData
) {}

package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Bayi performans grafiği")
public record DealerPerformanceChartResponse(
        @Schema(description = "Tarih aralığı")
        String period,

        @Schema(description = "Performans verileri")
        List<DealerPerformanceData> performanceData // Doğru import: dashboard package'ından
) {}
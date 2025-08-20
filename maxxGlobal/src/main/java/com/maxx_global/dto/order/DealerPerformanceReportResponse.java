package com.maxx_global.dto.order;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;

public record DealerPerformanceReportResponse(
        LocalDate startDate,
        LocalDate endDate,
        Map<Long, DealerPerformanceData> dealerPerformances,
        List<DealerPerformanceData> topPerformers, // En iyi 10 bayi
        DealerPerformanceData totalSummary // Genel özet
) {}
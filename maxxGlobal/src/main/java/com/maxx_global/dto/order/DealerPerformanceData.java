package com.maxx_global.dto.order;

import java.math.BigDecimal;

public record DealerPerformanceData(
        Long dealerId,
        String dealerName,
        Long totalOrders,
        Long completedOrders,
        Long cancelledOrders,
        BigDecimal totalRevenue,
        BigDecimal avgOrderValue,
        Double completionRate,
        Double cancellationRate,
        Integer ranking // Performans sıralaması
) {}
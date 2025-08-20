package com.maxx_global.dto.order;

import java.math.BigDecimal;

public record OrderStatisticsResponse(
        Long totalOrders,
        Long pendingOrders,
        Long approvedOrders,
        Long completedOrders,
        Long cancelledOrders,
        Long rejectedOrders,
        BigDecimal totalRevenue,
        BigDecimal pendingRevenue
) {}
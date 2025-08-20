package com.maxx_global.dto.order;

import com.maxx_global.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record OrderDailyReportResponse(
        LocalDate reportDate,
        Long totalOrders,
        BigDecimal totalRevenue,
        BigDecimal pendingAmount,
        Map<OrderStatus, Long> statusCounts
) {}
package com.maxx_global.dto.order;

import com.maxx_global.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record OrderMonthlyReportResponse(
        Integer year,           // 1
        Integer month,          // 2
        String monthName,       // 3
        Long totalOrders,       // 4
        BigDecimal totalRevenue, // 5
        Map<LocalDate, Long> dailyOrderCounts, // 6
        Map<OrderStatus, Long> statusCounts     // 7
) {}
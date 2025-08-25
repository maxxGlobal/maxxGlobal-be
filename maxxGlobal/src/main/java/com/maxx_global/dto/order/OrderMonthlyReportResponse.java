package com.maxx_global.dto.order;

import com.maxx_global.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

public record OrderMonthlyReportResponse(
        Integer year,           // 1
        Integer month,          // 2
        String monthName,       // 3
        Long totalOrders,       // 4
        BigDecimal totalRevenue, // 5
        Map<LocalDate, Long> dailyOrderCounts, // 6
        Map<OrderStatus, Long> statusCounts     // 7
) {
    // Factory method - OrderStatus enum'larını Türkçe'ye çeviren
    public static OrderMonthlyReportResponse create(
            Integer year,
            Integer month,
            String monthName,
            Long totalOrders,
            BigDecimal totalRevenue,
            Map<LocalDate, Long> dailyOrderCounts,
            Map<OrderStatus, Long> enumStatusCounts) {

        Map<OrderStatus, Long> turkishStatusCounts = enumStatusCounts.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> OrderStatus.valueOf(entry.getKey().getDisplayName()),
                        Map.Entry::getValue
                ));

        return new OrderMonthlyReportResponse(
                year,
                month,
                monthName,
                totalOrders,
                totalRevenue,
                dailyOrderCounts,
                turkishStatusCounts
        );
    }
}
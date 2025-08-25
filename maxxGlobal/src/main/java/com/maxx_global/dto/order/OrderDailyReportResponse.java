package com.maxx_global.dto.order;

import com.maxx_global.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

public record OrderDailyReportResponse(
        LocalDate reportDate,
        Long totalOrders,
        BigDecimal totalRevenue,
        BigDecimal pendingAmount,
        Map<OrderStatus, Long> statusCounts
) {

    // Factory method - OrderStatus enum'larını Türkçe'ye çeviren
    public static OrderDailyReportResponse create(
            LocalDate reportDate,
            Long totalOrders,
            BigDecimal totalRevenue,
            BigDecimal pendingAmount,
            Map<OrderStatus, Long> enumStatusCounts) {

        Map<OrderStatus, Long> turkishStatusCounts = enumStatusCounts.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> OrderStatus.valueOf(entry.getKey().getDisplayName()),
                        Map.Entry::getValue
                ));

        return new OrderDailyReportResponse(
                reportDate,
                totalOrders,
                totalRevenue,
                pendingAmount,
                turkishStatusCounts
        );
    }
}
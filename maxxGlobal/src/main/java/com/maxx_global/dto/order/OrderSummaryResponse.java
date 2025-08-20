// 2. OrderSummaryResponse - Kullanıcı sipariş özeti
package com.maxx_global.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long totalOrders,
        Long pendingOrders,
        Long approvedOrders,
        Long completedOrders,
        Long cancelledOrders,
        Long rejectedOrders,
        BigDecimal totalSpent,
        BigDecimal pendingAmount,
        LocalDateTime lastOrderDate,
        String mostOrderedProduct
) {}
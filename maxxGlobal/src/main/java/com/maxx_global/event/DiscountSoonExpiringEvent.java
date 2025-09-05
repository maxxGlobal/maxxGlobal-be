package com.maxx_global.event;

import com.maxx_global.entity.Discount;
import java.time.LocalDateTime;

public record DiscountSoonExpiringEvent(
        Discount discount,
        int daysUntilExpiration
) {
}
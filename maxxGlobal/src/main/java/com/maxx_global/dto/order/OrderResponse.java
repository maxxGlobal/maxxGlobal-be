package com.maxx_global.dto.order;

import com.maxx_global.dto.appUser.UserSummary;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String dealerName,
        UserSummary createdBy,  // Siparişi oluşturan kullanıcı özeti
        List<OrderItemSummary> items,
        LocalDateTime createdDate,
        String status
) {}


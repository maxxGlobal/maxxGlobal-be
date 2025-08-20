package com.maxx_global.dto.order;

import java.util.List;

public record OrderHistoryResponse(
        OrderResponse order,
        List<OrderHistoryEntry> historyEntries
) {}
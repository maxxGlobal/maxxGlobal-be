package com.maxx_global.dto.order;

import java.time.LocalDateTime;

public record OrderHistoryEntry(
        Long id,
        String action, // "CREATED", "APPROVED", "REJECTED", "CANCELLED", "STATUS_CHANGED", "EDITED"
        String fromStatus,
        String toStatus,
        String description,
        String performedBy, // Kullanıcı adı
        LocalDateTime timestamp,
        String notes
) {}
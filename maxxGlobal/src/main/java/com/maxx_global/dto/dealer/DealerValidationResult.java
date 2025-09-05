package com.maxx_global.dto.dealer;

public record DealerValidationResult(
        Long dealerId,
        boolean isValid,
        String dealerName,
        int activeUserCount
) {
}
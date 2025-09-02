package com.maxx_global.dto.dealer;

import com.maxx_global.enums.CurrencyType;

public record DealerSummary(
        Long id,
        String name,
        String status,
        CurrencyType preferredCurrency // YENİ FIELD
) {}

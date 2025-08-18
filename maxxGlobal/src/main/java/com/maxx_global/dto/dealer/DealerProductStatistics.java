package com.maxx_global.dto.dealer;

import com.maxx_global.enums.CurrencyType;

public record DealerProductStatistics(
        Long dealerId,
        Long totalProducts,
        Long productsWithPrice,
        Long inStockProducts,
        CurrencyType currency
) {}
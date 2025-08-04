package com.maxx_global.dto.productPrice;

import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.PriceType;

import java.math.BigDecimal;

public record ProductPriceResponse(
        Long id,                  // Price kaydının ID’si
        CurrencyType currency,
        PriceType priceType,
        BigDecimal amount
) {}

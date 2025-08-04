package com.maxx_global.dto.productPrice;

import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.PriceType;

import java.math.BigDecimal;

public record ProductPriceRequest(
        Long productId,           // Fiyatın hangi ürüne ait olduğu
        CurrencyType currency,    // TRY, USD, EUR
        PriceType priceType,      // STANDARD, WHOLESALE, DISCOUNT vb.
        BigDecimal amount         // Fiyat miktarı
) {}

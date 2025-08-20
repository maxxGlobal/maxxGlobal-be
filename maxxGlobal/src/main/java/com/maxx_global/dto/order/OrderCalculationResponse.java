package com.maxx_global.dto.order;

import java.math.BigDecimal;
import java.util.List;

public record OrderCalculationResponse(
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String currency,
        Integer totalItems,
        List<OrderItemCalculation> itemCalculations, // Detaylı ürün hesaplamaları
        List<String> stockWarnings,
        String discountDescription // İndirim açıklaması
) {}
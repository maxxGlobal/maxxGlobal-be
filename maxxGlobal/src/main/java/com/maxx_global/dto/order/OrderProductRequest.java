package com.maxx_global.dto.order;

public record  OrderProductRequest(
        Long productPriceId, // product gibi bilgiler buradan çekileceks.
        Integer quantity
) {}

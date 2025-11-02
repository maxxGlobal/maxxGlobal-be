package com.maxx_global.dto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemRequest(
        @NotNull(message = "Dealer ID gereklidir")
        Long dealerId,

        @NotNull(message = "Product price ID gereklidir")
        Long productPriceId,

        @NotNull(message = "Miktar gereklidir")
        @Min(value = 1, message = "Miktar en az 1 olmalıdır")
        Integer quantity
) {}

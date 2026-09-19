package com.maxx_global.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderProductRequest(
        @NotNull(message = "Ürün varyantı gereklidir")
        @Min(value = 1, message = "Geçerli bir ürün varyantı seçilmelidir")
        Long productVariantId,
        Long productPriceId,
        @NotNull(message = "Miktar gereklidir")
        @Min(value = 1, message = "Miktar en az 1 olmalıdır")
        Integer quantity
) {}

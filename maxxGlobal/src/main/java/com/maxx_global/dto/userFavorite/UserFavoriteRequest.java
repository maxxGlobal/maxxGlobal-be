package com.maxx_global.dto.userFavorite;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Kullanıcı favori ekleme/çıkarma isteği")
public record UserFavoriteRequest(
        @Schema(description = "Ürün ID'si", example = "1", required = true)
        @NotNull(message = "Product ID is required")
        @Min(value = 1, message = "Product ID must be greater than 0")
        Long productId
) {}
package com.maxx_global.dto.userFavorite;

import com.maxx_global.dto.product.ProductSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Kullanıcı favori yanıt modeli")
public record UserFavoriteResponse(
        @Schema(description = "Favori ID'si", example = "1")
        Long id,

        @Schema(description = "Ürün özet bilgileri")
        ProductSummary product,

        @Schema(description = "Favorilere eklenme tarihi")
        LocalDateTime createdAt,

        @Schema(description = "Son güncelleme tarihi")
        LocalDateTime updatedAt
) {}
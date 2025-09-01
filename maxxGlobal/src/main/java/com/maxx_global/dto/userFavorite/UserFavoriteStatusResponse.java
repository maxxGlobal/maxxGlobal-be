package com.maxx_global.dto.userFavorite;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Favori durumu yanıtı")
public record UserFavoriteStatusResponse(
        @Schema(description = "Ürün ID'si", example = "1")
        Long productId,

        @Schema(description = "Favori mi?", example = "true")
        Boolean isFavorite,

        @Schema(description = "Favori ID'si (varsa)", example = "5")
        Long favoriteId
) {}
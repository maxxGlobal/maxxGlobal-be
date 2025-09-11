package com.maxx_global.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Toplu ürün getirme için istek modeli")
public record BulkProductRequest(

        @Schema(description = "Ürün ID'leri listesi", example = "[1, 2, 3, 4, 5]", required = true)
        @NotEmpty(message = "Ürün ID listesi boş olamaz")
        @Size(min = 1, max = 100, message = "En az 1, en fazla 100 ürün ID'si gönderilebilir")
        List<Long> productIds

) {

    public void validate() {
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("Ürün ID listesi boş olamaz");
        }

        if (productIds.size() > 100) {
            throw new IllegalArgumentException("En fazla 100 ürün ID'si gönderilebilir");
        }

        // Duplicate ID kontrolü
        if (productIds.stream().distinct().count() != productIds.size()) {
            throw new IllegalArgumentException("Tekrar eden ürün ID'leri bulundu");
        }

        // Null ID kontrolü
        if (productIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Geçersiz ürün ID'si bulundu");
        }
    }
}
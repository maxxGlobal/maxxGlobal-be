// 1. OrderRequest'i güncelle - discountId ekle
package com.maxx_global.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OrderRequest(
        @NotNull(message = "Dealer ID gereklidir")
        @Min(value = 1, message = "Dealer ID 1'den büyük olmalıdır")
        Long dealerId,

        @NotEmpty(message = "En az bir ürün seçilmelidir")
        @Valid
        List<OrderProductRequest> products,

        // YENİ - İndirim desteği
        Long discountId, // Opsiyonel - uygulanacak indirim

        String notes // Kullanıcı notu (opsiyonel)
) {
    public void validate() {
        if (products == null || products.isEmpty()) {
            throw new IllegalArgumentException("Sipariş için en az bir ürün seçilmelidir");
        }

        if (products.size() > 50) {
            throw new IllegalArgumentException("Bir siparişte maksimum 50 farklı ürün olabilir");
        }
    }
}
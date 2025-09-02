package com.maxx_global.dto.productPrice;

import com.maxx_global.entity.ProductPrice;

import java.util.List;

/**
 * Fiyat gruplama için helper DTO
 */
public record PriceGroup(Long productId, String productName, String productCode, Long dealerId, String dealerName,
                         List<ProductPrice> prices) {

    // Ana fiyat (ilk fiyat)
    public ProductPrice getMainPrice() {
        return prices.isEmpty() ? null : prices.get(0);
    }

    // Grup anahtarı oluştur
    public static String createGroupKey(Long productId, Long dealerId) {
        return productId + "_" + dealerId;
    }

    public String getGroupKey() {
        return createGroupKey(productId, dealerId);
    }
}
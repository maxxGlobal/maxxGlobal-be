package com.maxx_global.event;

import com.maxx_global.entity.Discount;

public record DiscountUpdatedEvent(
        Discount discount,
        Discount previousDiscount, // Önceki hali ile karşılaştırma için
        boolean isActivationChanged, // Aktif/pasif durumu değişti mi?
        boolean isDateChanged // Tarih değişti mi?
) {
}
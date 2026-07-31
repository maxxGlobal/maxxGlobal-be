package com.maxx_global.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class CartItemTest {
    @Test
    void recalculationKeepsMissingPriceNull() {
        CartItem item = new CartItem();
        item.setQuantity(3);
        item.setUnitPrice(null);
        item.recalculateTotals();
        assertNull(item.getTotalPrice());
    }
}

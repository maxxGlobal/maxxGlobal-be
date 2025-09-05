package com.maxx_global.event;

import com.maxx_global.entity.Discount;

public record DiscountCreatedEvent(Discount discount) {
}
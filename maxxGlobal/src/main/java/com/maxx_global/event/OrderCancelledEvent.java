package com.maxx_global.event;

import com.maxx_global.entity.Order;

public record OrderCancelledEvent (Order order) {
}

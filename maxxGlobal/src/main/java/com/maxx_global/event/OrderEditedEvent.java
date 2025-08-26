package com.maxx_global.event;

import com.maxx_global.entity.Order;

public record OrderEditedEvent(Order order) {
}

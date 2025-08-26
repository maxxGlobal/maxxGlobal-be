package com.maxx_global.event;

import com.maxx_global.entity.Order;

public record OrderStatusChangedEvent(Order order,String previousStatus) {
}

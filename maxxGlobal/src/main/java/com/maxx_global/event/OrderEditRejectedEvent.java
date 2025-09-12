// maxxGlobal/src/main/java/com/maxx_global/event/OrderEditRejectedEvent.java
package com.maxx_global.event;

import com.maxx_global.entity.Order;

public record OrderEditRejectedEvent(
        Order order,
        String customerRejectionReason
) {}
// maxxGlobal/src/main/java/com/maxx_global/dto/order/OrderChangeDetail.java
package com.maxx_global.dto.order;

import java.math.BigDecimal;

public record OrderChangeDetail(
        String changeType, // "ITEM_REMOVED", "QUANTITY_CHANGED", "ITEM_ADDED"
        Long productId,
        String productName,
        Integer originalQuantity,
        Integer newQuantity,
        BigDecimal originalPrice,
        BigDecimal newPrice,
        String description
) {}
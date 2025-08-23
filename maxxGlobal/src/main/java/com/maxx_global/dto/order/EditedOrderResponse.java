// maxxGlobal/src/main/java/com/maxx_global/dto/order/EditedOrderResponse.java
package com.maxx_global.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EditedOrderResponse(
        Long id,
        String orderNumber,
        OrderResponse originalOrder, // Orijinal sipariş
        OrderResponse editedOrder,   // Düzenlenmiş sipariş
        String editReason,
        String adminNotes,
        LocalDateTime editedDate,
        String editedBy, // Admin adı
        List<OrderChangeDetail> changes, // Değişikliklerin detayı
        BigDecimal originalTotal,
        BigDecimal editedTotal,
        BigDecimal totalDifference,
        Boolean requiresCustomerApproval
) {}
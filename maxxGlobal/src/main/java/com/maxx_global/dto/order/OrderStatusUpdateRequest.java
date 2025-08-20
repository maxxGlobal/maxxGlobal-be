package com.maxx_global.dto.order;

import com.maxx_global.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Sipariş durumu güncelleme için istek modeli")
public record OrderStatusUpdateRequest(
        @Schema(description = "Yeni sipariş durumu", example = "APPROVED", required = true)
        @NotNull(message = "Sipariş durumu gereklidir")
        OrderStatus status,

        @Schema(description = "Admin notu", example = "Stok kontrolü yapıldı, onaylandı")
        @Size(max = 1000, message = "Admin notu 1000 karakteri geçemez")
        String adminNote
) {}
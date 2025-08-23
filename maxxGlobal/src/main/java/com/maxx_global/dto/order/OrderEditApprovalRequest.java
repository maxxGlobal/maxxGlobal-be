// maxxGlobal/src/main/java/com/maxx_global/dto/order/OrderEditApprovalRequest.java
package com.maxx_global.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Düzenlenmiş sipariş onay/red isteği")
public record OrderEditApprovalRequest(
        @Schema(description = "Onay durumu", example = "true", required = true)
        @NotNull(message = "Onay durumu gereklidir")
        Boolean approved,

        @Schema(description = "Müşteri notu", example = "Değişiklikleri onaylıyorum")
        @Size(max = 500, message = "Not 500 karakteri geçemez")
        String customerNote
) {}
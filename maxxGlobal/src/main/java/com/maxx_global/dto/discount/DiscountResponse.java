package com.maxx_global.dto.discount;

import com.maxx_global.dto.dealer.DealerSummary;
import com.maxx_global.dto.product.ProductSummary;
import com.maxx_global.enums.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "İndirim yanıt modeli")
public record DiscountResponse(

        @Schema(description = "İndirim ID'si", example = "1")
        Long id,

        @Schema(description = "İndirim adı", example = "Kış Kampanyası")
        String name,

        @Schema(description = "İndirim açıklaması", example = "2024 Kış dönemi özel indirim kampanyası")
        String description,

        @Schema(description = "İndirim tipi", example = "PERCENTAGE")
        DiscountType discountType,

        @Schema(description = "İndirim değeri", example = "15.50")
        BigDecimal discountValue,

        @Schema(description = "Başlangıç tarihi", example = "2024-12-01T00:00:00")
        LocalDateTime startDate,

        @Schema(description = "Bitiş tarihi", example = "2024-12-31T23:59:59")
        LocalDateTime endDate,

        @Schema(description = "Uygulanacak ürünler")
        List<ProductSummary> applicableProducts,

        @Schema(description = "Uygulanacak bayiler")
        List<DealerSummary> applicableDealers,

        @Schema(description = "İndirim aktif mi?", example = "true")
        Boolean isActive,

        @Schema(description = "Şu an geçerli mi?", example = "true")
        Boolean isValidNow,

        @Schema(description = "Minimum sipariş tutarı", example = "100.00")
        BigDecimal minimumOrderAmount,

        @Schema(description = "Maksimum indirim tutarı", example = "500.00")
        BigDecimal maximumDiscountAmount,

        @Schema(description = "Oluşturulma tarihi", example = "2024-01-01T10:30:00")
        LocalDateTime createdDate,

        @Schema(description = "Güncellenme tarihi", example = "2024-01-01T10:30:00")
        LocalDateTime updatedDate,

        @Schema(description = "Durum", example = "ACTIVE")
        String status
) {}
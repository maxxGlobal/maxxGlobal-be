package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Sipariş durum dağılımı")
public record OrderStatusDistributionResponse(
        @Schema(description = "Toplam sipariş sayısı", example = "1250")
        Long totalOrders,

        @Schema(description = "Durum dağılımları")
        List<OrderStatusData> statusDistribution
) {}
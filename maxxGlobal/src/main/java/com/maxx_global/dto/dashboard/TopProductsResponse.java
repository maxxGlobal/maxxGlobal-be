package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "En çok sipariş edilen ürünler")
public record TopProductsResponse(
        @Schema(description = "Tarih aralığı")
        String period,

        @Schema(description = "Ürün listesi")
        List<TopProductData> products
) {}
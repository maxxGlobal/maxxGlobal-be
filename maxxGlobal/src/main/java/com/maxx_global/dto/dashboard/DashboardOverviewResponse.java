package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// === GENEL OVERVIEW ===
@Schema(description = "Dashboard genel bakış")
public record DashboardOverviewResponse(
        @Schema(description = "Toplam kullanıcı sayısı", example = "150")
        Long totalUsers,

        @Schema(description = "Aktif kullanıcı sayısı", example = "142")
        Long activeUsers,

        @Schema(description = "Toplam bayi sayısı", example = "45")
        Long totalDealers,

        @Schema(description = "Toplam ürün sayısı", example = "1250")
        Long totalProducts,

        @Schema(description = "Toplam sipariş sayısı", example = "3420")
        Long totalOrders,

        @Schema(description = "Bekleyen sipariş sayısı", example = "23")
        Long pendingOrders,

        @Schema(description = "Bu ay tamamlanan sipariş sayısı", example = "187")
        Long completedOrdersThisMonth,

        @Schema(description = "Bu ayın toplam cirosu", example = "1250000.50")
        BigDecimal revenueThisMonth,

        @Schema(description = "Geçen ayın toplam cirosu", example = "1180000.25")
        BigDecimal revenueLastMonth,

        @Schema(description = "Ciro artış oranı (%)", example = "5.93")
        Double revenueGrowthPercentage,

        @Schema(description = "Bu ayın ortalama sipariş değeri", example = "6684.49")
        BigDecimal averageOrderValueThisMonth,

        @Schema(description = "Düşük stok ürün sayısı", example = "12")
        Long lowStockProducts,

        @Schema(description = "Süresi yaklaşan ürün sayısı", example = "8")
        Long expiringProducts
) {}
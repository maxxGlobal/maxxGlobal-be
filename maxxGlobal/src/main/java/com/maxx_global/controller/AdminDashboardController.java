package com.maxx_global.controller;

import com.maxx_global.dto.dashboard.*;
import com.maxx_global.entity.AppUser;
import com.maxx_global.service.AdminDashboardService;
import com.maxx_global.service.AppUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@Tag(name = "Admin Dashboard", description = "Admin dashboard API'leri")
@PreAuthorize("hasPermission(null, 'ADMIN_DASHBOARD')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final AppUserService appUserService;

    public AdminDashboardController(AdminDashboardService adminDashboardService,
                                    AppUserService appUserService) {
        this.adminDashboardService = adminDashboardService;
        this.appUserService = appUserService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Dashboard genel bakış",
            description = "Ana sayfa için temel istatistikler")
    public ResponseEntity<DashboardOverviewResponse> getDashboardOverview(Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        DashboardOverviewResponse overview = adminDashboardService.getDashboardOverview();
        return ResponseEntity.ok(overview);
    }

    @GetMapping("/statistics")
    @Operation(summary = "Genel istatistikler",
            description = "Kullanıcı, bayi, ürün, sipariş sayıları")
    public ResponseEntity<SystemStatisticsResponse> getSystemStatistics(Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        SystemStatisticsResponse statistics = adminDashboardService.getSystemStatistics();
        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/charts/monthly-orders")
    @Operation(summary = "Aylık sipariş trendi",
            description = "Son 12 ayın sipariş trendi - Line Chart için")
    public ResponseEntity<MonthlyOrderTrendResponse> getMonthlyOrderTrend(
            @Parameter(description = "Kaç ay geriye gidilecek (varsayılan: 12)")
            @RequestParam(defaultValue = "12") int months,
            Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        MonthlyOrderTrendResponse trend = adminDashboardService.getMonthlyOrderTrend(months);
        return ResponseEntity.ok(trend);
    }

    @GetMapping("/charts/daily-orders")
    @Operation(summary = "Günlük sipariş hacmi",
            description = "Son 30 günün sipariş hacmi - Bar Chart için")
    public ResponseEntity<DailyOrderVolumeResponse> getDailyOrderVolume(
            @Parameter(description = "Kaç gün geriye gidilecek (varsayılan: 30)")
            @RequestParam(defaultValue = "30") int days,
            Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        DailyOrderVolumeResponse volume = adminDashboardService.getDailyOrderVolume(days);
        return ResponseEntity.ok(volume);
    }

    @GetMapping("/charts/order-status-distribution")
    @Operation(summary = "Sipariş durum dağılımı",
            description = "Sipariş durumlarının yüzdelik dağılımı - Donut Chart için")
    public ResponseEntity<OrderStatusDistributionResponse> getOrderStatusDistribution(
            @Parameter(description = "Tarih aralığı (gün) - varsayılan: 90")
            @RequestParam(defaultValue = "90") int days,
            Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        OrderStatusDistributionResponse distribution = adminDashboardService.getOrderStatusDistribution(days);
        return ResponseEntity.ok(distribution);
    }

    @GetMapping("/charts/top-dealers")
    @Operation(summary = "En çok sipariş veren bayiler",
            description = "Top 10 bayi - Bar Chart için")
    public ResponseEntity<TopDealersResponse> getTopDealers(
            @Parameter(description = "Kaç bayi gösterilecek (varsayılan: 10)")
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Tarih aralığı (gün) - varsayılan: 90")
            @RequestParam(defaultValue = "90") int days,
            Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        TopDealersResponse topDealers = adminDashboardService.getTopDealers(limit, days);
        return ResponseEntity.ok(topDealers);
    }

    @GetMapping("/charts/revenue-trend")
    @Operation(summary = "Aylık gelir trendi",
            description = "Son 12 ayın gelir trendi - Line Chart için")
    public ResponseEntity<RevenueTimelineResponse> getRevenueTrend(
            @Parameter(description = "Kaç ay geriye gidilecek (varsayılan: 12)")
            @RequestParam(defaultValue = "12") int months,
            Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        RevenueTimelineResponse revenue = adminDashboardService.getRevenueTrend(months);
        return ResponseEntity.ok(revenue);
    }

    @GetMapping("/charts/average-order-value")
    @Operation(summary = "Ortalama sipariş değeri trendi",
            description = "Aylık ortalama sipariş değeri - Line Chart için")
    public ResponseEntity<AverageOrderValueTrendResponse> getAverageOrderValueTrend(
            @Parameter(description = "Kaç ay geriye gidilecek (varsayılan: 12)")
            @RequestParam(defaultValue = "12") int months,
            Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        AverageOrderValueTrendResponse aov = adminDashboardService.getAverageOrderValueTrend(months);
        return ResponseEntity.ok(aov);
    }

    @GetMapping("/charts/dealer-performance")
    @Operation(summary = "En performanslı bayiler",
            description = "Ciro bazında top bayiler - Horizontal Bar Chart için")
    public ResponseEntity<DealerPerformanceChartResponse> getDealerPerformance(
            @Parameter(description = "Kaç bayi gösterilecek (varsayılan: 10)")
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Tarih aralığı (gün) - varsayılan: 90")
            @RequestParam(defaultValue = "90") int days,
            Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        DealerPerformanceChartResponse performance = adminDashboardService.getDealerPerformanceChart(limit, days);
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/charts/dealer-order-frequency")
    @Operation(summary = "Bayi sipariş sıklığı",
            description = "Bayilerin sipariş sıklığı dağılımı - Scatter Plot için")
    public ResponseEntity<DealerOrderFrequencyResponse> getDealerOrderFrequency(
            @Parameter(description = "Tarih aralığı (gün) - varsayılan: 90")
            @RequestParam(defaultValue = "90") int days,
            Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        DealerOrderFrequencyResponse frequency = adminDashboardService.getDealerOrderFrequency(days);
        return ResponseEntity.ok(frequency);
    }

    @GetMapping("/charts/top-products")
    @Operation(summary = "En çok sipariş edilen ürünler",
            description = "En popüler ürünler - Bar Chart için")
    public ResponseEntity<TopProductsResponse> getTopProducts(
            @Parameter(description = "Kaç ürün gösterilecek (varsayılan: 10)")
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Tarih aralığı (gün) - varsayılan: 90")
            @RequestParam(defaultValue = "90") int days,
            Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        TopProductsResponse topProducts = adminDashboardService.getTopProducts(limit, days);
        return ResponseEntity.ok(topProducts);
    }

    @GetMapping("/charts/discount-effectiveness")
    @Operation(summary = "En etkili indirimler",
            description = "Kullanım sayısına göre indirimler - Bar Chart için")
    public ResponseEntity<DiscountEffectivenessResponse> getDiscountEffectiveness(
            @Parameter(description = "Kaç indirim gösterilecek (varsayılan: 10)")
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Tarih aralığı (gün) - varsayılan: 90")
            @RequestParam(defaultValue = "90") int days,
            Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        DiscountEffectivenessResponse discounts = adminDashboardService.getDiscountEffectiveness(limit, days);
        return ResponseEntity.ok(discounts);
    }

    @GetMapping("/alerts")
    @Operation(summary = "Acil eylem gerektiren durumlar",
            description = "Düşük stok, bekleyen siparişler vb. uyarılar")
    public ResponseEntity<AlertsResponse> getAlerts(Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        AlertsResponse alerts = adminDashboardService.getAlerts();
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/recent-activities")
    @Operation(summary = "Son aktiviteler",
            description = "Son siparişler, güncellemeler vb.")
    public ResponseEntity<RecentActivitiesResponse> getRecentActivities(
            @Parameter(description = "Kaç aktivite gösterilecek (varsayılan: 20)")
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        RecentActivitiesResponse activities = adminDashboardService.getRecentActivities(limit);
        return ResponseEntity.ok(activities);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Dashboard verilerini yenile",
            description = "Cache'lenmiş dashboard verilerini temizle ve yenile")
    public ResponseEntity<String> refreshDashboard(Authentication authentication) {
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        adminDashboardService.refreshDashboardCache();
        return ResponseEntity.ok("Dashboard verileri başarıyla yenilendi");
    }
}
package com.maxx_global.service;

import com.maxx_global.dto.dashboard.*;
import com.maxx_global.entity.Discount;
import com.maxx_global.entity.Order;
import com.maxx_global.entity.OrderItem;
import com.maxx_global.entity.Product;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.OrderStatus;
import com.maxx_global.repository.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final Logger logger = Logger.getLogger(AdminDashboardService.class.getName());

    private final AppUserRepository appUserRepository;
    private final DealerRepository dealerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final DiscountRepository discountRepository;
    private final DiscountUsageRepository discountUsageRepository;
    private final NotificationRepository notificationRepository;

    public AdminDashboardService(AppUserRepository appUserRepository,
                                 DealerRepository dealerRepository,
                                 ProductRepository productRepository,
                                 OrderRepository orderRepository,
                                 DiscountRepository discountRepository,
                                 DiscountUsageRepository discountUsageRepository,
                                 NotificationRepository notificationRepository) {
        this.appUserRepository = appUserRepository;
        this.dealerRepository = dealerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.discountRepository = discountRepository;
        this.discountUsageRepository = discountUsageRepository;
        this.notificationRepository = notificationRepository;
    }

    // ==================== GENEL OVERVIEW ====================

    /**
     * Dashboard genel bakış - Ana istatistikler
     */
    @Cacheable(value = "dashboardOverview", unless = "#result == null")
    public DashboardOverviewResponse getDashboardOverview() {
        logger.info("Generating dashboard overview");

        // Kullanıcı istatistikleri
        Long totalUsers = appUserRepository.countByStatus(EntityStatus.ACTIVE);
        Long activeUsers = appUserRepository.countByStatus(EntityStatus.ACTIVE);

        // Bayi istatistikleri
        Long totalDealers = dealerRepository.count();

        // Ürün istatistikleri
        Long totalProducts = productRepository.countByStatus(EntityStatus.ACTIVE);
        Long lowStockProducts = (long) productRepository.findLowStockProducts(10, EntityStatus.ACTIVE).size();
        Long expiringProducts = (long) productRepository.findProductsExpiringBefore(
                LocalDate.now().plusDays(30), EntityStatus.ACTIVE).size();

        // Sipariş istatistikleri - Bu ay ve geçen ay
        LocalDateTime thisMonthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime thisMonthEnd = LocalDate.now().atTime(23, 59, 59);
        LocalDateTime lastMonthStart = LocalDate.now().minusMonths(1).withDayOfMonth(1).atStartOfDay();
        LocalDateTime lastMonthEnd = LocalDate.now().minusMonths(1).atTime(23, 59, 59);

        List<Order> allOrders = orderRepository.findAll();
        List<Order> thisMonthOrders = orderRepository.findOrdersInDateRange(thisMonthStart, thisMonthEnd);
        List<Order> lastMonthOrders = orderRepository.findOrdersInDateRange(lastMonthStart, lastMonthEnd);

        Long totalOrders = (long) allOrders.size();
        Long pendingOrders = allOrders.stream()
                .mapToLong(o -> o.getOrderStatus() == OrderStatus.PENDING ? 1 : 0).sum();
        Long completedOrdersThisMonth = thisMonthOrders.stream()
                .mapToLong(o -> o.getOrderStatus() == OrderStatus.COMPLETED ? 1 : 0).sum();

        // Ciro hesaplamaları
        BigDecimal revenueThisMonth = thisMonthOrders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal revenueLastMonth = lastMonthOrders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Ciro büyüme oranı hesapla
        Double revenueGrowthPercentage = 0.0;
        if (revenueLastMonth.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal growth = revenueThisMonth.subtract(revenueLastMonth)
                    .divide(revenueLastMonth, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            revenueGrowthPercentage = growth.doubleValue();
        }

        // Ortalama sipariş değeri
        BigDecimal averageOrderValueThisMonth = BigDecimal.ZERO;
        if (completedOrdersThisMonth > 0) {
            averageOrderValueThisMonth = revenueThisMonth
                    .divide(BigDecimal.valueOf(completedOrdersThisMonth), 2, RoundingMode.HALF_UP);
        }

        return new DashboardOverviewResponse(
                totalUsers,
                activeUsers,
                totalDealers,
                totalProducts,
                totalOrders,
                pendingOrders,
                completedOrdersThisMonth,
                revenueThisMonth,
                revenueLastMonth,
                revenueGrowthPercentage,
                averageOrderValueThisMonth,
                lowStockProducts,
                expiringProducts
        );
    }

    /**
     * Sistem istatistikleri - Detaylı breakdown
     */
    @Cacheable(value = "systemStatistics", unless = "#result == null")
    public SystemStatisticsResponse getSystemStatistics() {
        logger.info("Generating system statistics");

        // Kullanıcı istatistikleri
        UserStatistics userStats = new UserStatistics(
                appUserRepository.count(),
                appUserRepository.countByStatus(EntityStatus.ACTIVE),
                appUserRepository.countByStatus(EntityStatus.INACTIVE),
                getNewUsersThisMonth()
        );

        // Bayi istatistikleri
        DealerStatistics dealerStats = new DealerStatistics(
                dealerRepository.count(),
                (long) dealerRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE).size(),
                getNewDealersThisMonth()
        );

        // Ürün istatistikleri
        ProductStatistics productStats = new ProductStatistics(
                productRepository.countByStatus(EntityStatus.ACTIVE),
                productRepository.countInStockProducts(EntityStatus.ACTIVE),
                productRepository.countOutOfStockProducts(EntityStatus.ACTIVE),
                productRepository.countExpiredProducts(EntityStatus.ACTIVE),
                productRepository.countProductsWithoutImages(EntityStatus.ACTIVE)
        );

        // Sipariş istatistikleri
        List<Order> allOrders = orderRepository.findAll();
        OrderStatistics orderStats = new OrderStatistics(
                (long) allOrders.size(),
                allOrders.stream().mapToLong(o -> o.getOrderStatus() == OrderStatus.PENDING ? 1 : 0).sum(),
                allOrders.stream().mapToLong(o -> o.getOrderStatus() == OrderStatus.APPROVED ? 1 : 0).sum(),
                allOrders.stream().mapToLong(o -> o.getOrderStatus() == OrderStatus.COMPLETED ? 1 : 0).sum(),
                allOrders.stream().mapToLong(o -> o.getOrderStatus() == OrderStatus.CANCELLED ? 1 : 0).sum(),
                allOrders.stream().mapToLong(o -> o.getOrderStatus() == OrderStatus.SHIPPED ? 1 : 0).sum(),
                allOrders.stream().mapToLong(o -> o.getOrderStatus() == OrderStatus.REJECTED ? 1 : 0).sum(),
                allOrders.stream().mapToLong(o -> o.getOrderStatus() == OrderStatus.EDITED_PENDING_APPROVAL ? 1 : 0).sum(),
                getOrdersThisMonth(),
                getTotalRevenue(allOrders)
        );

        return new SystemStatisticsResponse(userStats, dealerStats, productStats, orderStats);
    }

    // ==================== CHART DATA METHODS ====================

    /**
     * Aylık sipariş trendi (Son 12 ay) - Line Chart
     */
    @Cacheable(value = "monthlyOrderTrend", key = "#months", unless = "#result == null")
    public MonthlyOrderTrendResponse getMonthlyOrderTrend(Integer months) {
        logger.info("Generating monthly order trend for last " + months + " months");

        int monthsToShow = months != null ? months : 12;
        LocalDateTime endDate = LocalDate.now().atTime(23, 59, 59);
        LocalDateTime startDate = endDate.minusMonths(monthsToShow).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        List<Order> orders = orderRepository.findOrdersInDateRange(startDate, endDate);

        // Aylık gruplandırma
        Map<String, List<Order>> monthlyOrders = orders.stream()
                .collect(Collectors.groupingBy(order ->
                        order.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                ));

        List<MonthlyOrderData> monthlyData = new ArrayList<>();
        LocalDate currentMonth = startDate.toLocalDate().withDayOfMonth(1);

        while (!currentMonth.isAfter(endDate.toLocalDate())) {
            String monthKey = currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            List<Order> monthOrders = monthlyOrders.getOrDefault(monthKey, new ArrayList<>());

            Long orderCount = (long) monthOrders.size();
            BigDecimal revenue = monthOrders.stream()
                    .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String monthName = currentMonth.getMonth().getDisplayName(TextStyle.FULL, new Locale("tr", "TR"))
                    + " " + currentMonth.getYear();

            monthlyData.add(new MonthlyOrderData(monthKey, monthName, orderCount, revenue));
            currentMonth = currentMonth.plusMonths(1);
        }

        // Trend yönü hesapla
        String trendDirection = calculateTrendDirection(monthlyData);
        Double averageMonthlyOrders = monthlyData.stream()
                .mapToDouble(data -> data.orderCount().doubleValue())
                .average()
                .orElse(0.0);

        String period = startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " - " +
                endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        return new MonthlyOrderTrendResponse(period, monthlyData, trendDirection, averageMonthlyOrders);
    }

    /**
     * Günlük sipariş hacmi (Son X gün) - Bar Chart
     */
    @Cacheable(value = "dailyOrderVolume", key = "#days", unless = "#result == null")
    public DailyOrderVolumeResponse getDailyOrderVolume(Integer days) {
        logger.info("Generating daily order volume for last " + days + " days");

        int daysToShow = days != null ? days : 30;
        LocalDateTime endDate = LocalDate.now().atTime(23, 59, 59);
        LocalDateTime startDate = endDate.minusDays(daysToShow).withHour(0).withMinute(0).withSecond(0);

        List<Order> orders = orderRepository.findOrdersInDateRange(startDate, endDate);

        // Günlük gruplandırma
        Map<LocalDate, List<Order>> dailyOrders = orders.stream()
                .collect(Collectors.groupingBy(order -> order.getOrderDate().toLocalDate()));

        List<DailyOrderData> dailyData = new ArrayList<>();
        LocalDate currentDate = startDate.toLocalDate();

        while (!currentDate.isAfter(endDate.toLocalDate())) {
            List<Order> dayOrders = dailyOrders.getOrDefault(currentDate, new ArrayList<>());

            Long orderCount = (long) dayOrders.size();
            BigDecimal revenue = dayOrders.stream()
                    .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String dayName = currentDate.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("tr", "TR"));

            dailyData.add(new DailyOrderData(currentDate, dayName, orderCount, revenue));
            currentDate = currentDate.plusDays(1);
        }

        // En yoğun günü bul
        String peakDay = dailyData.stream()
                .max(Comparator.comparing(DailyOrderData::orderCount))
                .map(data -> data.date().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " (" + data.orderCount() + " sipariş)")
                .orElse("Veri yok");

        Double averageDailyOrders = dailyData.stream()
                .mapToDouble(data -> data.orderCount().doubleValue())
                .average()
                .orElse(0.0);

        String period = startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " - " +
                endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        return new DailyOrderVolumeResponse(period, dailyData, averageDailyOrders, peakDay);
    }

    /**
     * Sipariş durum dağılımı - Donut Chart
     */
    @Cacheable(value = "orderStatusDistribution", key = "#days", unless = "#result == null")
    public OrderStatusDistributionResponse getOrderStatusDistribution(Integer days) {
        logger.info("Generating order status distribution for last " + days + " days");

        int daysToShow = days != null ? days : 90;
        LocalDateTime startDate = LocalDateTime.now().minusDays(daysToShow);
        List<Order> orders = orderRepository.findOrdersInDateRange(startDate, LocalDateTime.now());
        Long totalOrders = (long) orders.size();

        // Status dağılımını hesapla
        Map<OrderStatus, Long> statusCounts = orders.stream()
                .collect(Collectors.groupingBy(Order::getOrderStatus, Collectors.counting()));

        List<OrderStatusData> statusDistribution = Arrays.stream(OrderStatus.values())
                .map(status -> {
                    Long count = statusCounts.getOrDefault(status, 0L);
                    Double percentage = totalOrders > 0 ?
                            (count.doubleValue() / totalOrders.doubleValue()) * 100 : 0.0;

                    return new OrderStatusData(
                            status.getDisplayName(),
                            status.name(),
                            count,
                            Math.round(percentage * 100.0) / 100.0,
                            getStatusColor(status)
                    );
                })
                .filter(data -> data.count() > 0)
                .collect(Collectors.toList());

        return new OrderStatusDistributionResponse(totalOrders, statusDistribution);
    }

    /**
     * En çok sipariş veren bayiler (Top N) - Bar Chart
     */
    @Cacheable(value = "topDealers", key = "#limit + '_' + #days", unless = "#result == null")
    public TopDealersResponse getTopDealers(Integer limit, Integer days) {
        logger.info("Generating top dealers list for last " + days + " days");

        int limitToShow = limit != null ? limit : 10;
        int daysToShow = days != null ? days : 90;
        LocalDateTime startDate = LocalDateTime.now().minusDays(daysToShow);
        List<Order> recentOrders = orderRepository.findOrdersInDateRange(startDate, LocalDateTime.now());

        // Bayi bazında sipariş gruplandırması
        Map<Long, List<Order>> dealerOrders = recentOrders.stream()
                .collect(Collectors.groupingBy(order -> order.getUser().getDealer().getId()));

        List<TopDealerData> topDealers = dealerOrders.entrySet().stream()
                .map(entry -> {
                    Long dealerId = entry.getKey();
                    List<Order> orders = entry.getValue();

                    String dealerName = orders.isEmpty() ? "Bilinmeyen" :
                            orders.get(0).getUser().getDealer().getName();

                    Long orderCount = (long) orders.size();
                    BigDecimal totalRevenue = orders.stream()
                            .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                            .map(Order::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal averageOrderValue = orderCount > 0 ?
                            totalRevenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP) :
                            BigDecimal.ZERO;

                    return new TopDealerData(dealerId, dealerName, orderCount, totalRevenue, averageOrderValue, 0);
                })
                .sorted((d1, d2) -> d2.totalRevenue().compareTo(d1.totalRevenue()))
                .limit(limitToShow)
                .collect(Collectors.toList());

        // Ranking bilgisini set et
        for (int i = 0; i < topDealers.size(); i++) {
            TopDealerData dealer = topDealers.get(i);
            topDealers.set(i, new TopDealerData(
                    dealer.dealerId(), dealer.dealerName(), dealer.orderCount(),
                    dealer.totalRevenue(), dealer.averageOrderValue(), i + 1
            ));
        }

        String period = startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " - " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        return new TopDealersResponse(period, topDealers);
    }

    public RevenueTimelineResponse getRevenueTrend(Integer months) {
        return getRevenueTimeline(months);
    }
    /**
     * Aylık gelir trendi (Son 12 ay) - Line Chart
     */
    @Cacheable(value = "revenueTimeline", key = "#months", unless = "#result == null")
    public RevenueTimelineResponse getRevenueTimeline(Integer months) {
        logger.info("Generating revenue timeline");

        int monthsToShow = months != null ? months : 12;
        LocalDateTime endDate = LocalDate.now().atTime(23, 59, 59);
        LocalDateTime startDate = endDate.minusMonths(monthsToShow).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        List<OrderStatus> statuses = Arrays.asList(OrderStatus.COMPLETED,OrderStatus.APPROVED,OrderStatus.SHIPPED);
        List<Order> orders = orderRepository.findOrdersInDateRangeWithStatus(startDate, endDate,statuses);


        // Aylık gelir gruplandırması
        Map<String, List<Order>> monthlyOrders = orders.stream()
                .collect(Collectors.groupingBy(order ->
                        order.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                ));

        List<RevenueData> monthlyRevenue = new ArrayList<>();
        LocalDate currentMonth = startDate.toLocalDate().withDayOfMonth(1);
        BigDecimal previousMonthRevenue = BigDecimal.ZERO;

        while (!currentMonth.isAfter(endDate.toLocalDate())) {
            String monthKey = currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            List<Order> monthOrders = monthlyOrders.getOrDefault(monthKey, new ArrayList<>());

            BigDecimal revenue = monthOrders.stream()
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Önceki aya göre değişim yüzdesi
            Double changePercentage = 0.0;
            if (previousMonthRevenue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal change = revenue.subtract(previousMonthRevenue)
                        .divide(previousMonthRevenue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                changePercentage = change.doubleValue();
            }

            String monthName = currentMonth.getMonth().getDisplayName(TextStyle.FULL, new Locale("tr", "TR"))
                    + " " + currentMonth.getYear();

            monthlyRevenue.add(new RevenueData(monthKey, monthName, revenue, changePercentage));
            previousMonthRevenue = revenue;
            currentMonth = currentMonth.plusMonths(1);
        }

        // Toplam ve ortalama hesapla
        BigDecimal totalRevenue = monthlyRevenue.stream()
                .map(RevenueData::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageMonthlyRevenue = monthlyRevenue.size() > 0 ?
                totalRevenue.divide(BigDecimal.valueOf(monthlyRevenue.size()), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        // Büyüme oranı hesapla (ilk ve son ay karşılaştırması)
        Double growthRate = 0.0;
        if (monthlyRevenue.size() >= 2) {
            BigDecimal firstMonth = monthlyRevenue.get(0).revenue();
            BigDecimal lastMonth = monthlyRevenue.get(monthlyRevenue.size() - 1).revenue();
            if (firstMonth.compareTo(BigDecimal.ZERO) > 0) {
                growthRate = lastMonth.subtract(firstMonth)
                        .divide(firstMonth, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }
        }

        String period = startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " - " +
                endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        return new RevenueTimelineResponse(period, monthlyRevenue, totalRevenue, averageMonthlyRevenue, growthRate);
    }

    /**
     * Ortalama sipariş değeri trendi - Line Chart
     */

    public AverageOrderValueTrendResponse getAverageOrderValueTrend(Integer months) {
        logger.info("Generating average order value trend");

        int monthsToShow = months != null ? months : 12;
        LocalDateTime endDate = LocalDate.now().atTime(23, 59, 59);
        LocalDateTime startDate = endDate.minusMonths(monthsToShow).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        List<OrderStatus> statuses = Arrays.asList(OrderStatus.COMPLETED,OrderStatus.APPROVED,OrderStatus.SHIPPED);
        List<Order> orders = orderRepository.findOrdersInDateRangeWithStatus(startDate, endDate,statuses);


        // Aylık AOV hesaplama
        Map<String, List<Order>> monthlyOrders = orders.stream()
                .collect(Collectors.groupingBy(order ->
                        order.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                ));

        List<AverageOrderValueData> monthlyAOV = new ArrayList<>();
        LocalDate currentMonth = startDate.toLocalDate().withDayOfMonth(1);

        while (!currentMonth.isAfter(endDate.toLocalDate())) {
            String monthKey = currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            List<Order> monthOrders = monthlyOrders.getOrDefault(monthKey, new ArrayList<>());

            Long orderCount = (long) monthOrders.size();
            BigDecimal totalRevenue = monthOrders.stream()
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal averageOrderValue = orderCount > 0 ?
                    totalRevenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP) :
                    BigDecimal.ZERO;

            String monthName = currentMonth.getMonth().getDisplayName(TextStyle.FULL, new Locale("tr", "TR"))
                    + " " + currentMonth.getYear();

            monthlyAOV.add(new AverageOrderValueData(monthKey, monthName, averageOrderValue, orderCount));
            currentMonth = currentMonth.plusMonths(1);
        }

        // Genel ortalama AOV hesapla
        BigDecimal totalRevenue = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Long totalOrderCount = (long) orders.size();
        BigDecimal overallAverageOrderValue = totalOrderCount > 0 ?
                totalRevenue.divide(BigDecimal.valueOf(totalOrderCount), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        // Trend yönü hesapla
        String trendDirection = calculateAOVTrendDirection(monthlyAOV);

        String period = startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " - " +
                endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        return new AverageOrderValueTrendResponse(period, monthlyAOV, overallAverageOrderValue, trendDirection);
    }

    /**
     * Bayi performans grafiği - Horizontal Bar Chart
     */
    @Cacheable(value = "dealerPerformance", key = "#limit + '_' + #days", unless = "#result == null")
    public DealerPerformanceChartResponse getDealerPerformanceChart(Integer limit, Integer days) {
        logger.info("Generating dealer performance chart for last " + days + " days");

        int limitToShow = limit != null ? limit : 10;
        int daysToShow = days != null ? days : 90;
        LocalDateTime startDate = LocalDateTime.now().minusDays(daysToShow);
        List<Order> orders = orderRepository.findOrdersInDateRange(startDate, LocalDateTime.now());

        // Bayi bazında performans hesaplama
        Map<Long, List<Order>> dealerOrders = orders.stream()
                .collect(Collectors.groupingBy(order -> order.getUser().getDealer().getId()));

        List<DealerPerformanceData> performanceData = dealerOrders.entrySet().stream()
                .map(entry -> {
                    Long dealerId = entry.getKey();
                    List<Order> dealerOrderList = entry.getValue();

                    String dealerName = dealerOrderList.isEmpty() ? "Bilinmeyen" :
                            dealerOrderList.get(0).getUser().getDealer().getName();

                    Long totalOrders = (long) dealerOrderList.size();
                    Long completedOrders = dealerOrderList.stream()
                            .mapToLong(o -> o.getOrderStatus() == OrderStatus.COMPLETED ? 1 : 0).sum();

                    BigDecimal totalRevenue = dealerOrderList.stream()
                            .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                            .map(Order::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    Double completionRate = totalOrders > 0 ?
                            (completedOrders.doubleValue() / totalOrders.doubleValue()) * 100 : 0.0;

                    BigDecimal averageOrderValue = completedOrders > 0 ?
                            totalRevenue.divide(BigDecimal.valueOf(completedOrders), 2, RoundingMode.HALF_UP) :
                            BigDecimal.ZERO;

                    // Performans skoru hesapla
                    Double performanceScore = (totalRevenue.doubleValue() / 10000 * 0.7) + (completionRate * 0.3);
                    performanceScore = Math.min(10.0, Math.max(0.0, performanceScore));

                    return new DealerPerformanceData(
                            dealerId, dealerName, totalRevenue, totalOrders, completionRate,
                            averageOrderValue, Math.round(performanceScore * 10.0) / 10.0
                    );
                })
                .sorted((d1, d2) -> d2.totalRevenue().compareTo(d1.totalRevenue()))
                .limit(limitToShow)
                .collect(Collectors.toList());

        String period = startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " - " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        return new DealerPerformanceChartResponse(period, performanceData);
    }

    /**
     * Bayi sipariş sıklığı - Scatter Plot
     */
    @Cacheable(value = "dealerOrderFrequency", key = "#days", unless = "#result == null")
    public DealerOrderFrequencyResponse getDealerOrderFrequency(Integer days) {
        logger.info("Generating dealer order frequency for last " + days + " days");

        int daysToShow = days != null ? days : 90;
        LocalDateTime startDate = LocalDateTime.now().minusDays(daysToShow);
        List<Order> orders = orderRepository.findOrdersInDateRange(startDate, LocalDateTime.now());

        // Bayi bazında sıklık hesaplama
        Map<Long, List<Order>> dealerOrders = orders.stream()
                .collect(Collectors.groupingBy(order -> order.getUser().getDealer().getId()));

        List<DealerFrequencyData> frequencyData = dealerOrders.entrySet().stream()
                .map(entry -> {
                    Long dealerId = entry.getKey();
                    List<Order> dealerOrderList = entry.getValue();

                    if (dealerOrderList.isEmpty()) return null;

                    String dealerName = dealerOrderList.get(0).getUser().getDealer().getName();
                    Long orderCount = (long) dealerOrderList.size();

                    BigDecimal totalRevenue = dealerOrderList.stream()
                            .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                            .map(Order::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal averageOrderValue = orderCount > 0 ?
                            totalRevenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP) :
                            BigDecimal.ZERO;

                    // Sipariş sıklığı hesapla (gün bazında)
                    Double orderFrequencyDays = orderCount > 0 ?
                            daysToShow / orderCount.doubleValue() : daysToShow;

                    return new DealerFrequencyData(
                            dealerId, dealerName, orderCount, averageOrderValue,
                            totalRevenue, Math.round(orderFrequencyDays * 10.0) / 10.0
                    );
                })
                .filter(Objects::nonNull)
                .filter(data -> data.orderCount() > 0)
                .collect(Collectors.toList());

        String period = startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " - " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        return new DealerOrderFrequencyResponse(period, frequencyData);
    }

    /**
     * En çok sipariş edilen ürünler - Bar Chart
     */
    @Cacheable(value = "topProducts", key = "#limit + '_' + #days", unless = "#result == null")
    public TopProductsResponse getTopProducts(Integer limit, Integer days) {
        logger.info("Generating top products list for last " + days + " days");

        int limitToShow = limit != null ? limit : 10;
        int daysToShow = days != null ? days : 90;
        LocalDateTime startDate = LocalDateTime.now().minusDays(daysToShow);
        List<Order> recentOrders = orderRepository.findOrdersInDateRange(startDate, LocalDateTime.now());

        // Ürün bazında sipariş toplama
        Map<Long, List<Object[]>> productOrderData = new HashMap<>();

        for (Order order : recentOrders) {
            for (var item : order.getItems()) {
                Long productId = item.getProduct().getId();
                productOrderData.computeIfAbsent(productId, k -> new ArrayList<>())
                        .add(new Object[]{item, order});
            }
        }

        List<TopProductData> topProducts = productOrderData.entrySet().stream()
                .map(entry -> {
                    Long productId = entry.getKey();
                    List<Object[]> productItems = entry.getValue();

                    if (productItems.isEmpty()) return null;

                    var firstItem = (OrderItem) productItems.get(0)[0];
                    String productName = firstItem.getProduct().getName();
                    String productCode = firstItem.getProduct().getCode();
                    String categoryName = firstItem.getProduct().getCategory() != null ?
                            firstItem.getProduct().getCategory().getName() : "Kategori Yok";

                    Long totalQuantityOrdered = productItems.stream()
                            .mapToLong(data -> ((OrderItem) data[0]).getQuantity().longValue())
                            .sum();

                    Long orderCount = (long) productItems.stream()
                            .map(data -> ((Order) data[1]).getId())
                            .collect(Collectors.toSet())
                            .size();

                    BigDecimal totalRevenue = productItems.stream()
                            .filter(data -> ((Order) data[1]).getOrderStatus() == OrderStatus.COMPLETED)
                            .map(data -> ((OrderItem) data[0]).getTotalPrice())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new TopProductData(
                            productId, productName, productCode, categoryName,
                            totalQuantityOrdered, orderCount, totalRevenue, 0
                    );
                })
                .filter(Objects::nonNull)
                .sorted((p1, p2) -> p2.totalQuantityOrdered().compareTo(p1.totalQuantityOrdered()))
                .limit(limitToShow)
                .collect(Collectors.toList());

        // Ranking bilgisini set et
        for (int i = 0; i < topProducts.size(); i++) {
            TopProductData product = topProducts.get(i);
            topProducts.set(i, new TopProductData(
                    product.productId(), product.productName(), product.productCode(),
                    product.categoryName(), product.totalQuantityOrdered(), product.orderCount(),
                    product.totalRevenue(), i + 1
            ));
        }

        String period = startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " - " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        return new TopProductsResponse(period, topProducts);
    }

    /**
     * En etkili indirimler - Bar Chart
     */
    @Cacheable(value = "discountEffectiveness", key = "#limit + '_' + #days", unless = "#result == null")
    public DiscountEffectivenessResponse getDiscountEffectiveness(Integer limit, Integer days) {
        logger.info("Generating discount effectiveness for last " + days + " days");

        int limitToShow = limit != null ? limit : 10;
        int daysToShow = days != null ? days : 180;
        LocalDateTime startDate = LocalDateTime.now().minusDays(daysToShow);

        // Aktif indirimleri getir
        List<Discount> allDiscounts = discountRepository.findAll();

        List<DiscountEffectivenessData> effectivenessData = allDiscounts.stream()
                .map(discount -> {
                    // İndirim kullanım sayısı
                    Long usageCount = discountUsageRepository.countByDiscount(
                            discount.getId(),
                            Arrays.asList(OrderStatus.COMPLETED, OrderStatus.APPROVED, OrderStatus.SHIPPED)
                    );

                    // İndirimle yapılan siparişleri bul
                    Long affectedOrders = orderRepository.countByAppliedDiscountIdAndOrderStatusIn(
                            discount.getId(),
                            Arrays.asList(OrderStatus.COMPLETED, OrderStatus.APPROVED, OrderStatus.SHIPPED)
                    );

                    // Toplam tasarruf hesapla
                    List<Order> discountOrders = orderRepository.findAll().stream()
                            .filter(o -> o.getAppliedDiscount() != null &&
                                    o.getAppliedDiscount().getId().equals(discount.getId()) &&
                                    o.getOrderDate().isAfter(startDate) &&
                                    (o.getOrderStatus() == OrderStatus.COMPLETED ||
                                            o.getOrderStatus() == OrderStatus.APPROVED ||
                                            o.getOrderStatus() == OrderStatus.SHIPPED))
                            .collect(Collectors.toList());

                    BigDecimal totalSavings = discountOrders.stream()
                            .map(Order::getDiscountAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Etkinlik skoru hesapla
                    Double effectivenessScore = 0.0;
                    if (usageCount > 0) {
                        effectivenessScore = Math.min(10.0,
                                (usageCount.doubleValue() * 0.3) +
                                        (totalSavings.doubleValue() / 1000.0 * 0.7));
                    }

                    return new DiscountEffectivenessData(
                            discount.getId(),
                            discount.getName(),
                            discount.getDiscountType().name(),
                            discount.getDiscountValue(),
                            usageCount,
                            totalSavings,
                            affectedOrders,
                            Math.round(effectivenessScore * 10.0) / 10.0
                    );
                })
                .filter(data -> data.usageCount() > 0)
                .sorted((d1, d2) -> d2.effectivenessScore().compareTo(d1.effectivenessScore()))
                .limit(limitToShow)
                .collect(Collectors.toList());

        String period = startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " - " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        return new DiscountEffectivenessResponse(period, effectivenessData);
    }

    /**
     * Son aktiviteler listesi
     */
    public RecentActivitiesResponse getRecentActivities(Integer limit) {
        logger.info("Generating recent activities");

        int limitToShow = limit != null ? limit : 20;
        LocalDateTime last7Days = LocalDateTime.now().minusDays(7);

        List<ActivityData> activities = new ArrayList<>();

        // Son siparişler
        List<Order> recentOrders = orderRepository.findOrdersInDateRange(last7Days, LocalDateTime.now())
                .stream()
                .sorted((o1, o2) -> o2.getOrderDate().compareTo(o1.getOrderDate()))
                .limit(limitToShow)
                .collect(Collectors.toList());

        for (Order order : recentOrders) {
            String activityType = order.getOrderStatus().getDisplayName();
            String title = getOrderActivityTitle(order.getOrderStatus());
            String description = order.getUser().getDealer().getName() + " - " +
                    order.getOrderNumber() + " (" + order.getTotalAmount() + " TL)";
            String userName = order.getUser().getFirstName() + " " + order.getUser().getLastName();
            String actionUrl = "/admin/orders/" + order.getId();
            String icon = getOrderActivityIcon(order.getOrderStatus());
            String color = getOrderActivityColor(order.getOrderStatus());

            activities.add(new ActivityData(
                    order.getId(),
                    activityType,
                    title,
                    description,
                    userName,
                    order.getOrderNumber(),
                    "ORDER",
                    actionUrl,
                    order.getOrderDate(),
                    icon,
                    color
            ));
        }

        // Aktiviteleri tarih sırasına göre sırala
        activities.sort((a1, a2) -> a2.createdAt().compareTo(a1.createdAt()));

        return new RecentActivitiesResponse(
                activities.stream().limit(limitToShow).collect(Collectors.toList()),
                activities.size()
        );
    }

    /**
     * Sistem uyarıları
     */
    public AlertsResponse getSystemAlerts() {
        logger.info("Generating system alerts");

        List<AlertData> alerts = new ArrayList<>();

        // Düşük stok uyarıları
        List<Product> lowStockProducts = productRepository.findLowStockProducts(10, EntityStatus.ACTIVE);
        if (!lowStockProducts.isEmpty()) {
            alerts.add(new AlertData(
                    null,
                    "LOW_STOCK",
                    "Düşük Stok Uyarısı",
                    lowStockProducts.size() + " ürünün stoğu kritik seviyede",
                    "HIGH",
                    null,
                    "PRODUCT",
                    "/admin/products?filter=lowStock",
                    LocalDateTime.now(),
                    false
            ));
        }

        // Süresi yaklaşan ürünler
        List<Product> expiringProducts = productRepository.findProductsExpiringBefore(
                LocalDate.now().plusDays(30), EntityStatus.ACTIVE);
        if (!expiringProducts.isEmpty()) {
            alerts.add(new AlertData(
                    null,
                    "EXPIRING_PRODUCTS",
                    "Ürün Son Kullanma Tarihi Uyarısı",
                    expiringProducts.size() + " ürünün son kullanma tarihi yaklaşıyor",
                    "MEDIUM",
                    null,
                    "PRODUCT",
                    "/admin/products?filter=expiring",
                    LocalDateTime.now(),
                    false
            ));
        }

        // Bekleyen siparişler
        List<Order> pendingOrders = orderRepository.findByOrderStatus(OrderStatus.PENDING,
                PageRequest.of(0, 100)).getContent();
        if (!pendingOrders.isEmpty()) {
            alerts.add(new AlertData(
                    null,
                    "PENDING_ORDERS",
                    "Bekleyen Siparişler",
                    pendingOrders.size() + " sipariş onay bekliyor",
                    "HIGH",
                    null,
                    "ORDER",
                    "/admin/orders?status=PENDING",
                    LocalDateTime.now(),
                    false
            ));
        }

        // Resmi olmayan ürünler
        Long productsWithoutImages = productRepository.countProductsWithoutImages(EntityStatus.ACTIVE);
        if (productsWithoutImages > 0) {
            alerts.add(new AlertData(
                    null,
                    "PRODUCTS_WITHOUT_IMAGES",
                    "Resmi Olmayan Ürünler",
                    productsWithoutImages + " ürünün resmi bulunmuyor",
                    "LOW",
                    null,
                    "PRODUCT",
                    "/admin/products?filter=withoutImages",
                    LocalDateTime.now(),
                    false
            ));
        }

        // Kritik ve toplam uyarı sayısını hesapla
        Integer criticalAlerts = (int) alerts.stream()
                .mapToLong(alert -> "HIGH".equals(alert.severity()) ? 1 : 0)
                .sum();

        return new AlertsResponse(alerts.size(), criticalAlerts, alerts);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private Long getNewUsersThisMonth() {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return appUserRepository.findAll().stream()
                .mapToLong(user -> user.getCreatedAt() != null &&
                        user.getCreatedAt().isAfter(startOfMonth) ? 1 : 0)
                .sum();
    }

    private Long getNewDealersThisMonth() {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return dealerRepository.findAll().stream()
                .mapToLong(dealer -> dealer.getCreatedAt() != null &&
                        dealer.getCreatedAt().isAfter(startOfMonth) ? 1 : 0)
                .sum();
    }

    private Long getOrdersThisMonth() {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = LocalDate.now().atTime(23, 59, 59);
        return (long) orderRepository.findOrdersInDateRange(startOfMonth, endOfMonth).size();
    }

    private BigDecimal getTotalRevenue(List<Order> orders) {
        return orders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String calculateTrendDirection(List<MonthlyOrderData> monthlyData) {
        if (monthlyData.size() < 2) return "STABLE";

        Long firstMonth = monthlyData.get(0).orderCount();
        Long lastMonth = monthlyData.get(monthlyData.size() - 1).orderCount();

        if (lastMonth > firstMonth * 1.1) return "UP";
        if (lastMonth < firstMonth * 0.9) return "DOWN";
        return "STABLE";
    }

    private String calculateAOVTrendDirection(List<AverageOrderValueData> aovData) {
        if (aovData.size() < 2) return "STABLE";

        BigDecimal firstMonth = aovData.get(0).averageOrderValue();
        BigDecimal lastMonth = aovData.get(aovData.size() - 1).averageOrderValue();

        if (firstMonth.compareTo(BigDecimal.ZERO) == 0) return "STABLE";

        BigDecimal changeRatio = lastMonth.divide(firstMonth, 4, RoundingMode.HALF_UP);
        if (changeRatio.compareTo(BigDecimal.valueOf(1.1)) > 0) return "UP";
        if (changeRatio.compareTo(BigDecimal.valueOf(0.9)) < 0) return "DOWN";
        return "STABLE";
    }

    private String getStatusColor(OrderStatus status) {
        return switch (status) {
            case PENDING -> "#ffc107";
            case APPROVED -> "#17a2b8";
            case COMPLETED -> "#28a745";
            case CANCELLED -> "#dc3545";
            case REJECTED -> "#fd7e14";
            case SHIPPED -> "#6f42c1";
            case EDITED_PENDING_APPROVAL -> "#20c997";
        };
    }

    private String getOrderActivityTitle(OrderStatus status) {
        return switch (status) {
            case PENDING -> "Yeni Sipariş";
            case APPROVED -> "Sipariş Onaylandı";
            case COMPLETED -> "Sipariş Tamamlandı";
            case CANCELLED -> "Sipariş İptal Edildi";
            case REJECTED -> "Sipariş Reddedildi";
            case SHIPPED -> "Sipariş Kargoya Verildi";
            case EDITED_PENDING_APPROVAL -> "Sipariş Düzenlendi";
        };
    }

    private String getOrderActivityIcon(OrderStatus status) {
        return switch (status) {
            case PENDING -> "clock";
            case APPROVED -> "check-circle";
            case COMPLETED -> "package-check";
            case CANCELLED -> "x-circle";
            case REJECTED -> "x-circle";
            case SHIPPED -> "truck";
            case EDITED_PENDING_APPROVAL -> "edit";
        };
    }

    private String getOrderActivityColor(OrderStatus status) {
        return switch (status) {
            case PENDING -> "warning";
            case APPROVED -> "info";
            case COMPLETED -> "success";
            case CANCELLED, REJECTED -> "danger";
            case SHIPPED -> "primary";
            case EDITED_PENDING_APPROVAL -> "secondary";
        };
    }

    public AlertsResponse getAlerts() {
        return getSystemAlerts();
    }

    /**
     * Dashboard cache'ini yenile
     */
    @CacheEvict(value = {
            "dashboardOverview",
            "systemStatistics",
            "monthlyOrderTrend",
            "dailyOrderVolume",
            "orderStatusDistribution",
            "topDealers",
            "revenueTimeline",
            "averageOrderValueTrend",
            "dealerPerformance",
            "dealerOrderFrequency",
            "topProducts",
            "discountEffectiveness"
    }, allEntries = true)
    @Transactional
    public void refreshDashboardCache() {
        logger.info("All dashboard caches cleared");
    }
}
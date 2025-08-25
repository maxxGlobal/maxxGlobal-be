package com.maxx_global.service;

import com.maxx_global.dto.order.*;
import com.maxx_global.entity.*;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.OrderStatus;
import com.maxx_global.repository.OrderRepository;
import com.maxx_global.repository.ProductPriceRepository;
import com.maxx_global.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger logger = Logger.getLogger(OrderService.class.getName());

    private final OrderRepository orderRepository;
    private final ProductPriceRepository productPriceRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;

    // Facade Pattern - Diğer servisleri çağır
    private final DealerService dealerService;
    private final DiscountService discountService;
    private final ProductService productService;
    private final AppUserService appUserService;
    private final MailService mailService;


    public OrderService(OrderRepository orderRepository,
                        ProductPriceRepository productPriceRepository,
                        ProductRepository productRepository,
                        OrderMapper orderMapper,
                        DealerService dealerService,
                        DiscountService discountService,
                        ProductService productService, AppUserService appUserService, MailService mailService) {
        this.orderRepository = orderRepository;
        this.productPriceRepository = productPriceRepository;
        this.productRepository = productRepository;
        this.orderMapper = orderMapper;
        this.dealerService = dealerService;
        this.discountService = discountService;
        this.productService = productService;
        this.appUserService = appUserService;
        this.mailService = mailService;
    }

    // ==================== END USER METHODS ====================

    /**
     * 1. Yeni sipariş oluşturur - Fiyat hesaplama ve indirim dahil
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request, AppUser currentUser) {
        logger.info("Creating order for user: " + currentUser.getId() + ", dealer: " + request.dealerId());

        // Validation
        request.validate();

        // Dealer varlık kontrolü
        dealerService.getDealerById(request.dealerId());

        // Kullanıcının bu dealer ile ilişkisi var mı kontrol et
        validateUserDealerRelation(currentUser, request.dealerId());

        // Order entity oluştur
        Order order = new Order();
        order.setUser(currentUser);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setOrderDate(LocalDateTime.now());
        order.setCurrency(CurrencyType.TRY); // Default currency
        order.setOrderNumber(generateOrderNumber());
        order.setNotes(request.notes());

        // Order items oluştur ve fiyat hesapla
        Set<OrderItem> orderItems = createOrderItems(order, request.products());
        order.setItems(orderItems);

        // Toplam tutarı hesapla
        BigDecimal subtotal = calculateSubtotal(orderItems);

        // İndirim uygula
        BigDecimal discountAmount = BigDecimal.ZERO;
        Discount appliedDiscount = null;

        if (request.discountId() != null) {
            appliedDiscount = applyDiscount(request.discountId(), request.dealerId(), subtotal, orderItems);
            if (appliedDiscount != null) {
                discountAmount = calculateDiscountAmount(appliedDiscount, subtotal, orderItems);
                order.setAppliedDiscount(appliedDiscount);
            }
        }

        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(subtotal.subtract(discountAmount));

        // Stok kontrolü yap
        validateStockAvailability(orderItems);

        // Siparişi kaydet
        Order savedOrder = orderRepository.save(order);

        // Stok güncelle (rezerve et)
        updateProductStocks(orderItems, true); // true = rezerve et

        try {
            mailService.sendNewOrderNotificationToAdmins(savedOrder);
        } catch (Exception e) {
            logger.warning("Failed to send new order notification email: " + e.getMessage());
            // Mail gönderimi hatası sipariş oluşturulmasını engellemez
        }

        logger.info("Order created successfully: " + savedOrder.getOrderNumber() +
                ", total: " + savedOrder.getTotalAmount());

        return orderMapper.toDto(savedOrder);
    }

    /**
     * 2. Kullanıcının siparişlerini listeler
     */
    public Page<OrderResponse> getOrdersByUser(Long userId, int page, int size,
                                               String sortBy, String sortDirection, String status) {
        logger.info("Fetching orders for user: " + userId + ", status filter: " + status);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Order> orders;
        if (status != null && !status.trim().isEmpty()) {
            OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
            orders = orderRepository.findByUserIdAndOrderStatus(userId, orderStatus, pageable);
        } else {
            orders = orderRepository.findByUserId(userId, pageable);
        }

        return orders.map(orderMapper::toDto);
    }

    /**
     * 3. Kullanıcı kendi siparişinin detayını görür
     */
    public OrderResponse getOrderByIdForUser(Long orderId, AppUser currentUser) {
        logger.info("Fetching order: " + orderId + " for user: " + currentUser.getId());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));

        // Kullanıcı kendi siparişini mi görüyor kontrol et
        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Bu siparişi görme yetkiniz yok");
        }

        return orderMapper.toDto(order);
    }

    /**
     * 4. Kullanıcı siparişini iptal eder (sadece PENDING durumunda)
     */
    @Transactional
    public OrderResponse cancelOrderByUser(Long orderId, AppUser currentUser, String cancelReason) {
        logger.info("User cancelling order: " + orderId + ", reason: " + cancelReason);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));

        // Yetki kontrolü
        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Bu siparişi iptal etme yetkiniz yok");
        }

        // Durum kontrolü
        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Sadece beklemede olan siparişler iptal edilebilir. " +
                    "Mevcut durum: " + order.getOrderStatus());
        }

        // Siparişi iptal et
        order.setOrderStatus(OrderStatus.CANCELLED);
        if (cancelReason != null && !cancelReason.trim().isEmpty()) {
            String currentNotes = order.getNotes() != null ? order.getNotes() : "";
            order.setNotes(currentNotes + "\n[İptal nedeni: " + cancelReason + "]");
        }

        // Stok iade et
        updateProductStocks(order.getItems(), false); // false = iade et

        Order savedOrder = orderRepository.save(order);
        logger.info("Order cancelled successfully: " + savedOrder.getOrderNumber());

        return orderMapper.toDto(savedOrder);
    }

    /**
     * 5. Kullanıcının sipariş özetini getirir
     */
    public OrderSummaryResponse getOrderSummaryByUser(Long userId) {
        logger.info("Fetching order summary for user: " + userId);

        List<Order> userOrders = orderRepository.findByUserId(userId);

        // İstatistikleri hesapla
        Long totalOrders = (long) userOrders.size();
        Long pendingOrders = userOrders.stream()
                .mapToLong(o -> o.getOrderStatus() == OrderStatus.PENDING ? 1 : 0).sum();
        Long approvedOrders = userOrders.stream()
                .mapToLong(o -> o.getOrderStatus() == OrderStatus.APPROVED ? 1 : 0).sum();
        Long completedOrders = userOrders.stream()
                .mapToLong(o -> o.getOrderStatus() == OrderStatus.COMPLETED ? 1 : 0).sum();
        Long cancelledOrders = userOrders.stream()
                .mapToLong(o -> o.getOrderStatus() == OrderStatus.CANCELLED ? 1 : 0).sum();
        Long rejectedOrders = userOrders.stream()
                .mapToLong(o -> o.getOrderStatus() == OrderStatus.REJECTED ? 1 : 0).sum();

        BigDecimal totalSpent = userOrders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingAmount = userOrders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.PENDING || o.getOrderStatus() == OrderStatus.APPROVED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime lastOrderDate = userOrders.stream()
                .map(Order::getOrderDate)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        // En çok sipariş edilen ürün
        String mostOrderedProduct = findMostOrderedProduct(userOrders);

        return new OrderSummaryResponse(
                totalOrders, pendingOrders, approvedOrders, completedOrders,
                cancelledOrders, rejectedOrders, totalSpent, pendingAmount,
                lastOrderDate, mostOrderedProduct
        );
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private void validateUserDealerRelation(AppUser user, Long dealerId) {
        // Kullanıcının dealer'ı var mı ve doğru mu kontrol et
        if (user.getDealer() == null) {
            throw new IllegalArgumentException("Kullanıcının bağlı olduğu bir bayi yok");
        }
        if (!user.getDealer().getId().equals(dealerId)) {
            throw new IllegalArgumentException("Sadece kendi bayiniz için sipariş oluşturabilirsiniz");
        }
    }

    private Set<OrderItem> createOrderItems(Order order, List<OrderProductRequest> productRequests) {
        Set<OrderItem> orderItems = new HashSet<>();

        for (OrderProductRequest productRequest : productRequests) {
            // ProductPrice'ı bul
            ProductPrice productPrice = productPriceRepository.findById(productRequest.productPriceId())
                    .orElseThrow(() -> new EntityNotFoundException("Ürün fiyatı bulunamadı: " + productRequest.productPriceId()));

            // Fiyat geçerli mi kontrol et
            if (!productPrice.isValidNow()) {
                throw new IllegalArgumentException("Ürün fiyatı geçersiz: " + productPrice.getProduct().getName());
            }

            // OrderItem oluştur
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(productPrice.getProduct());
            orderItem.setQuantity(productRequest.quantity());
            orderItem.setUnitPrice(productPrice.getAmount());
            orderItem.setTotalPrice(productPrice.getAmount().multiply(BigDecimal.valueOf(productRequest.quantity())));

            orderItems.add(orderItem);
        }

        return orderItems;
    }

    private BigDecimal calculateSubtotal(Set<OrderItem> orderItems) {
        return orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Discount applyDiscount(Long discountId, Long dealerId, BigDecimal subtotal, Set<OrderItem> orderItems) {
        try {
            // Discount varlık kontrolü
            var discountResponse = discountService.getDiscountById(discountId);

            // İndirim geçerli mi ve bu dealer/ürünler için uygulanabilir mi kontrol et
            // Bu kontrol DiscountService'de yapılmalı
            // Şimdilik basit bir kontrol yapalım

            logger.info("Applying discount: " + discountId + " for dealer: " + dealerId);
            return new Discount(); // Geçici - gerçek discount entity dönecek

        } catch (Exception e) {
            logger.warning("Could not apply discount: " + e.getMessage());
            return null;
        }
    }

    private BigDecimal calculateDiscountAmount(Discount discount, BigDecimal subtotal, Set<OrderItem> orderItems) {
        // İndirim hesaplama logic'i
        // DiscountService'den hesaplama yaptırılabilir
        return BigDecimal.ZERO; // Geçici
    }

    private void validateStockAvailability(Set<OrderItem> orderItems) {
        for (OrderItem item : orderItems) {
            Product product = item.getProduct();
            if (product.getStockQuantity() < item.getQuantity()) {
                throw new IllegalArgumentException("Yetersiz stok: " + product.getName() +
                        " (İstenilen: " + item.getQuantity() + ", Mevcut: " + product.getStockQuantity() + ")");
            }
        }
    }

    private void updateProductStocks(Set<OrderItem> orderItems, boolean reserve) {
        for (OrderItem item : orderItems) {
            Product product = item.getProduct();
            int newStock;

            if (reserve) {
                // Stok rezerve et (azalt)
                newStock = product.getStockQuantity() - item.getQuantity();
            } else {
                // Stok iade et (artır)
                newStock = product.getStockQuantity() + item.getQuantity();
            }

            product.setStockQuantity(Math.max(0, newStock));
            productRepository.save(product);

            logger.info("Updated stock for product " + product.getName() +
                    ": " + product.getStockQuantity() + " (reserve: " + reserve + ")");
        }
    }

    private String generateOrderNumber() {
        // Sipariş numarası format: ORD-YYYYMMDD-HHMMSS-XXX
        LocalDateTime now = LocalDateTime.now();
        String datePart = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String timePart = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        String randomPart = String.format("%03d", new Random().nextInt(1000));

        return "ORD-" + datePart + "-" + timePart + "-" + randomPart;
    }

    private String findMostOrderedProduct(List<Order> orders) {
        Map<String, Long> productCounts = orders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                .flatMap(o -> o.getItems().stream())
                .collect(Collectors.groupingBy(
                        item -> item.getProduct().getName(),
                        Collectors.summingLong(OrderItem::getQuantity)
                ));

        return productCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Henüz tamamlanmış sipariş yok");
    }

    // OrderService.java - Eksik metodları ekle

// ==================== ADMIN METHODS ====================

    /**
     * Admin - Tüm siparişleri listeler (filtreleme ile)
     */
    public Page<OrderResponse> getAllOrdersForAdmin(int page, int size, String sortBy, String sortDirection,
                                                    String status, Long dealerId, Long userId) {
        logger.info("Admin fetching all orders with filters");

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Filtreleme kriterlerine göre query oluştur
        Page<Order> orders;
        if (status != null || dealerId != null || userId != null) {
            orders = orderRepository.findOrdersWithFilters(
                    status != null ? OrderStatus.valueOf(status.toUpperCase()) : null,
                    dealerId, userId, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }

        return orders.map(orderMapper::toDto);
    }

    /**
     * Admin - Siparişi onayla
     */
    @Transactional
    public OrderResponse approveOrder(Long orderId, AppUser admin, String adminNote) {
        logger.info("Admin approving order: " + orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Sadece beklemede olan siparişler onaylanabilir. Mevcut durum: " + order.getOrderStatus());
        }

        order.setOrderStatus(OrderStatus.APPROVED);
        if (adminNote != null && !adminNote.trim().isEmpty()) {
            order.setAdminNotes(adminNote);
        }

        Order savedOrder = orderRepository.save(order);

        try {
            mailService.sendOrderApprovedNotificationToCustomer(savedOrder);
        } catch (Exception e) {
            logger.warning("Failed to send order approved notification email: " + e.getMessage());
        }
        return orderMapper.toDto(savedOrder);
    }

    /**
     * Admin - Siparişi reddet
     */
    @Transactional
    public OrderResponse rejectOrder(Long orderId, AppUser admin, String rejectionReason) {
        logger.info("Admin rejecting order: " + orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Sadece beklemede olan siparişler reddedilebilir. Mevcut durum: " + order.getOrderStatus());
        }

        order.setOrderStatus(OrderStatus.REJECTED);
        if (rejectionReason != null && !rejectionReason.trim().isEmpty()) {
            order.setAdminNotes(rejectionReason);
        }

        // Stok iade et
        updateProductStocks(order.getItems(), false);

        Order savedOrder = orderRepository.save(order);
        try {
            mailService.sendOrderRejectedNotificationToCustomer(savedOrder);
        } catch (Exception e) {
            logger.warning("Failed to send order rejected notification email: " + e.getMessage());
        }
        return orderMapper.toDto(savedOrder);
    }

    /**
     * Admin - Sipariş durumunu güncelle
     */
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String newStatus, AppUser admin, String statusNote) {
        logger.info("Admin updating order status: " + orderId + " to " + newStatus);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));

        OrderStatus previousStatus = order.getOrderStatus();

        OrderStatus targetStatus = OrderStatus.valueOf(newStatus.toUpperCase());

        // Durum geçişi validasyonu
        validateStatusTransition(order.getOrderStatus(), targetStatus);

        order.setOrderStatus(targetStatus);

        if (statusNote != null && !statusNote.trim().isEmpty()) {
            String currentNotes = order.getAdminNotes() != null ? order.getAdminNotes() : "";
            order.setAdminNotes(currentNotes + "\n[" + targetStatus + ": " + statusNote + "]");
        }

        Order savedOrder = orderRepository.save(order);
        try {
            mailService.sendOrderStatusChangeNotificationToCustomer(savedOrder, previousStatus.name());
        } catch (Exception e) {
            logger.warning("Failed to send order status change notification email: " + e.getMessage());
        }
        return orderMapper.toDto(savedOrder);
    }

    /**
     * Admin - Sipariş istatistikleri
     */
    public OrderStatisticsResponse getOrderStatisticsForAdmin(String startDate, String endDate) {
        logger.info("Admin fetching order statistics");

        // Tarih aralığını parse et
        LocalDateTime start = startDate != null ? LocalDate.parse(startDate).atStartOfDay() : LocalDateTime.now().minusMonths(1);
        LocalDateTime end = endDate != null ? LocalDate.parse(endDate).atTime(23, 59, 59) : LocalDateTime.now();

        List<Order> orders = orderRepository.findOrdersInDateRange(start, end);

        // İstatistikleri hesapla
        Long totalOrders = (long) orders.size();
        Long pendingOrders = orders.stream().mapToLong(o -> o.getOrderStatus() == OrderStatus.PENDING ? 1 : 0).sum();
        Long approvedOrders = orders.stream().mapToLong(o -> o.getOrderStatus() == OrderStatus.APPROVED ? 1 : 0).sum();
        Long completedOrders = orders.stream().mapToLong(o -> o.getOrderStatus() == OrderStatus.COMPLETED ? 1 : 0).sum();
        Long cancelledOrders = orders.stream().mapToLong(o -> o.getOrderStatus() == OrderStatus.CANCELLED ? 1 : 0).sum();
        Long rejectedOrders = orders.stream().mapToLong(o -> o.getOrderStatus() == OrderStatus.REJECTED ? 1 : 0).sum();

        BigDecimal totalRevenue = orders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingRevenue = orders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.PENDING || o.getOrderStatus() == OrderStatus.APPROVED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrderStatisticsResponse(
                totalOrders, pendingOrders, approvedOrders, completedOrders,
                cancelledOrders, rejectedOrders, totalRevenue, pendingRevenue
        );
    }

    /**
     * Admin - Sipariş arama
     */
    public Page<OrderResponse> searchOrdersForAdmin(String searchTerm, int page, int size) {
        logger.info("Admin searching orders with term: " + searchTerm);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderDate"));
        Page<Order> orders = orderRepository.searchOrders(searchTerm, pageable);

        return orders.map(orderMapper::toDto);
    }

// ==================== PRIVATE HELPER METHODS ====================

    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        // Geçerli durum geçişlerini kontrol et
        switch (currentStatus) {
            case PENDING:
                if (targetStatus != OrderStatus.APPROVED && targetStatus != OrderStatus.REJECTED && targetStatus != OrderStatus.CANCELLED) {
                    throw new IllegalStateException("PENDING durumundan sadece APPROVED, REJECTED veya CANCELLED durumuna geçiş yapılabilir");
                }
                break;
            case APPROVED:
                if (targetStatus != OrderStatus.SHIPPED && targetStatus != OrderStatus.CANCELLED) {
                    throw new IllegalStateException("APPROVED durumundan sadece SHIPPED veya CANCELLED durumuna geçiş yapılabilir");
                }
                break;
            case SHIPPED:
                if (targetStatus != OrderStatus.COMPLETED) {
                    throw new IllegalStateException("SHIPPED durumundan sadece COMPLETED durumuna geçiş yapılabilir");
                }
                break;
            case COMPLETED:
            case CANCELLED:
            case REJECTED:
                throw new IllegalStateException("Final durumlardan (" + currentStatus + ") başka duruma geçiş yapılamaz");
            default:
                throw new IllegalStateException("Bilinmeyen sipariş durumu: " + currentStatus);
        }
    }

    // ==================== YENİ EKLENECEK METODLAR ====================

    /**
     * Son siparişleri getir (hızlı erişim için)
     */
    public Page<OrderResponse> getRecentOrdersByUser(Long userId, int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching recent orders for user: " + userId + ", page: " + page + ", size: " + size);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Order> orders = orderRepository.findByUserId(userId, pageable);

        return orders.map(orderMapper::toDto);
    }

    /**
     * Bayiye göre siparişleri listele
     */
    public Page<OrderResponse> getOrdersByDealer(Long dealerId, AppUser currentUser, int page, int size,
                                                 String sortBy, String sortDirection) {
        logger.info("Fetching orders for dealer: " + dealerId);

        // Bayi varlık kontrolü
        dealerService.getDealerById(dealerId);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Order> orders = orderRepository.findByUserDealerId(dealerId, pageable);
        return orders.map(orderMapper::toDto);
    }

    /**
     * Tarih aralığına göre siparişleri listele
     */
    public Page<OrderResponse> getOrdersByDateRange(String startDate, String endDate, AppUser currentUser,
                                                    int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching orders by date range: " + startDate + " to " + endDate);

        try {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);

            if (start.isAfter(end)) {
                throw new IllegalArgumentException("Başlangıç tarihi bitiş tarihinden sonra olamaz");
            }

            Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<Order> orders = orderRepository.findByUserIdAndOrderDateBetween(
                    currentUser.getId(), start, end, pageable);

            return orders.map(orderMapper::toDto);

        } catch (Exception e) {
            throw new IllegalArgumentException("Geçersiz tarih formatı. YYYY-MM-DD formatında giriniz");
        }
    }

    /**
     * Sipariş geçmişini getir
     */
    public OrderHistoryResponse getOrderHistory(Long orderId, AppUser currentUser) {
        logger.info("Fetching order history for id: " + orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));

        // Yetki kontrolü - kullanıcı sadece kendi siparişlerini görebilir
        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Bu siparişin geçmişini görme yetkiniz yok");
        }

        // Sipariş geçmişi oluştur (order status change log'ları)
        List<OrderHistoryEntry> historyEntries = new ArrayList<>();

        return new OrderHistoryResponse(
                orderMapper.toDto(order),
                historyEntries
        );
    }

    /**
     * Duruma göre siparişleri listele
     */
    public Page<OrderResponse> getOrdersByStatus(String status, AppUser currentUser, int page, int size,
                                                 String sortBy, String sortDirection) {
        logger.info("Fetching orders by status: " + status);

        try {
            OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());

            Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<Order> orders = orderRepository.findByUserIdAndOrderStatus(currentUser.getId(), orderStatus, pageable);

            return orders.map(orderMapper::toDto);

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Geçersiz sipariş durumu: " + status +
                    ". Geçerli durumlar: PENDING, APPROVED, SHIPPED, COMPLETED, CANCELLED, REJECTED");
        }
    }

    /**
     * Sipariş tutarı hesapla (sepet için)
     */
    public OrderCalculationResponse calculateOrderTotal(OrderRequest request, AppUser currentUser) {
        logger.info("Calculating order total for dealer: " + request.dealerId());

        // Validation
        request.validate();

        // Dealer varlık kontrolü
        dealerService.getDealerById(request.dealerId());

        // Kullanıcının bu dealer ile ilişkisi var mı kontrol et
        validateUserDealerRelation(currentUser, request.dealerId());

        // Geçici order items oluştur (kaydetmeden)
        Set<OrderItem> orderItems = createOrderItemsForCalculation(request.products());

        // Subtotal hesapla
        BigDecimal subtotal = calculateSubtotal(orderItems);

        // İndirim hesapla (varsa)
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.discountId() != null) {
            Discount appliedDiscount = applyDiscount(request.discountId(), request.dealerId(), subtotal, orderItems);
            if (appliedDiscount != null) {
                discountAmount = calculateDiscountAmount(appliedDiscount, subtotal, orderItems);
            }
        }

        BigDecimal totalAmount = subtotal.subtract(discountAmount);

        // Stok durumunu kontrol et (warning olarak)
        List<String> stockWarnings = checkStockWarnings(orderItems);

        // Ürün detaylarını hazırla
        List<OrderItemCalculation> itemCalculations = orderItems.stream()
                .map(item -> new OrderItemCalculation(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getProduct().getCode(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getTotalPrice(),
                        item.getProduct().getStockQuantity() >= item.getQuantity(),
                        item.getProduct().getStockQuantity(),
                        BigDecimal.ZERO, // ürün bazında indirim henüz yok
                        determineStockStatus(item.getProduct(), item.getQuantity())
                ))
                .collect(Collectors.toList());

        String discountDescription = request.discountId() != null ?
                "İndirim uygulandı" : null;

        return new OrderCalculationResponse(
                subtotal,
                discountAmount,
                totalAmount,
                "TRY", // currency
                orderItems.size(), // totalItems
                itemCalculations, // itemCalculations
                stockWarnings, // stockWarnings
                discountDescription // discountDescription
        );
    }

    /**
     * Admin - Onay bekleyen siparişleri listele
     */
    public Page<OrderResponse> getPendingApprovalOrders(int page, int size, String sortBy, String sortDirection) {
        logger.info("Admin fetching pending approval orders");

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Order> orders = orderRepository.findByOrderStatus(OrderStatus.PENDING, pageable);
        return orders.map(orderMapper::toDto);
    }

    /**
     * Admin - Siparişten ürün çıkar
     */
    @Transactional
    public OrderResponse removeItemFromOrder(Long orderId, Long itemId, AppUser admin, String removeReason) {
        logger.info("Admin removing item " + itemId + " from order " + orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));

        if (order.getOrderStatus() != OrderStatus.PENDING && order.getOrderStatus() != OrderStatus.APPROVED) {
            throw new IllegalStateException("Sadece beklemede veya onaylanmış siparişlerden ürün çıkarılabilir");
        }

        // İlgili order item'ı bul
        OrderItem itemToRemove = order.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Sipariş kalemi bulunamadı: " + itemId));

        // Son ürün ise siparişi iptal et
        if (order.getItems().size() == 1) {
            throw new IllegalStateException("Siparişte tek ürün kaldığı için çıkarılamaz. Siparişi iptal ediniz");
        }

        // Stok iade et
        Product product = itemToRemove.getProduct();
        product.setStockQuantity(product.getStockQuantity() + itemToRemove.getQuantity());
        productRepository.save(product);

        // Item'ı siparişten çıkar
        order.getItems().remove(itemToRemove);

        // Toplam tutarı yeniden hesapla
        BigDecimal newSubtotal = calculateSubtotal(order.getItems());
        order.setTotalAmount(newSubtotal.subtract(order.getDiscountAmount()));

        // Admin not ekle
        if (removeReason != null && !removeReason.trim().isEmpty()) {
            String currentNotes = order.getAdminNotes() != null ? order.getAdminNotes() : "";
            order.setAdminNotes(currentNotes + "\n[Ürün çıkarıldı - " + product.getName() + ": " + removeReason + "]");
        }

        Order savedOrder = orderRepository.save(order);
        return orderMapper.toDto(savedOrder);
    }

    /**
     * Günlük rapor
     */
    public OrderDailyReportResponse getDailyReport(String reportDate, AppUser currentUser) {
        logger.info("Generating daily report for date: " + reportDate);

        LocalDate targetDate = reportDate != null ?
                LocalDate.parse(reportDate) : LocalDate.now();

        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.atTime(23, 59, 59);

        List<Order> orders = orderRepository.findByUserIdAndOrderDateBetween(
                currentUser.getId(), startOfDay, endOfDay);

        // Günlük istatistikleri hesapla
        Long totalOrders = (long) orders.size();
        BigDecimal totalRevenue = orders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingAmount = orders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.PENDING)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<OrderStatus, Long> enumStatusCounts = orders.stream()
                .collect(Collectors.groupingBy(Order::getOrderStatus, Collectors.counting()));

        // Factory method kullanarak Türkçe statuslar ile response oluştur
        return OrderDailyReportResponse.create(
                targetDate,
                totalOrders,
                totalRevenue,
                pendingAmount,
                enumStatusCounts
        );
    }

    /**
     * Aylık rapor
     */
    public OrderMonthlyReportResponse getMonthlyReport(Integer year, Integer month, AppUser currentUser) {
        logger.info("Generating monthly report for year: " + year + ", month: " + month);

        int targetYear = year != null ? year : LocalDate.now().getYear();
        int targetMonth = month != null ? month : LocalDate.now().getMonthValue();

        if (targetMonth < 1 || targetMonth > 12) {
            throw new IllegalArgumentException("Ay değeri 1-12 arasında olmalıdır");
        }

        LocalDate startOfMonth = LocalDate.of(targetYear, targetMonth, 1);
        LocalDate endOfMonth = startOfMonth.plusMonths(1).minusDays(1);

        LocalDateTime startDateTime = startOfMonth.atStartOfDay();
        LocalDateTime endDateTime = endOfMonth.atTime(23, 59, 59);

        List<Order> orders = orderRepository.findByUserIdAndOrderDateBetween(
                currentUser.getId(), startDateTime, endDateTime);

        // Aylık istatistikleri hesapla
        Long totalOrders = (long) orders.size();
        BigDecimal totalRevenue = orders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Günlük bazda dağılım
        Map<LocalDate, Long> dailyOrderCounts = orders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getOrderDate().toLocalDate(),
                        Collectors.counting()
                ));

        Map<OrderStatus, Long> enumStatusCounts = orders.stream()
                .collect(Collectors.groupingBy(Order::getOrderStatus, Collectors.counting()));

        // Factory method kullanarak Türkçe statuslar ile response oluştur
        return OrderMonthlyReportResponse.create(
                targetYear,
                targetMonth,
                getMonthName(targetMonth),
                totalOrders,
                totalRevenue,
                dailyOrderCounts,
                enumStatusCounts
        );
    }

    /**
     * Bayi performans raporu
     */
    public DealerPerformanceReportResponse getDealerPerformanceReport(String startDate, String endDate, Long dealerId) {
        logger.info("Generating dealer performance report");

        LocalDateTime start = startDate != null ?
                LocalDate.parse(startDate).atStartOfDay() : LocalDateTime.now().minusMonths(3);
        LocalDateTime end = endDate != null ?
                LocalDate.parse(endDate).atTime(23, 59, 59) : LocalDateTime.now();

        List<Order> orders;
        if (dealerId != null) {
            // Belirli bayi için
            orders = orderRepository.findByUserDealerIdAndOrderDateBetween(dealerId, start, end);
        } else {
            // Tüm bayiler için
            orders = orderRepository.findOrdersInDateRange(start, end);
        }

        // Bayi bazında performans hesapla
        Map<Long, DealerPerformanceData> dealerPerformance = orders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getUser().getDealer().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                this::calculateDealerPerformance
                        )
                ));

        return new DealerPerformanceReportResponse(
                start.toLocalDate(),
                end.toLocalDate(),
                dealerPerformance,
                dealerPerformance.values().stream()
                        .sorted((d1, d2) -> d2.totalRevenue().compareTo(d1.totalRevenue()))
                        .limit(10)
                        .collect(Collectors.toList()),
                calculateTotalSummary(dealerPerformance.values())
        );
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private Set<OrderItem> createOrderItemsForCalculation(List<OrderProductRequest> productRequests) {
        Set<OrderItem> orderItems = new HashSet<>();

        for (OrderProductRequest productRequest : productRequests) {
            ProductPrice productPrice = productPriceRepository.findById(productRequest.productPriceId())
                    .orElseThrow(() -> new EntityNotFoundException("Ürün fiyatı bulunamadı: " + productRequest.productPriceId()));

            if (!productPrice.isValidNow()) {
                throw new IllegalArgumentException("Ürün fiyatı geçersiz: " + productPrice.getProduct().getName());
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(productPrice.getProduct());
            orderItem.setQuantity(productRequest.quantity());
            orderItem.setUnitPrice(productPrice.getAmount());
            orderItem.setTotalPrice(productPrice.getAmount().multiply(BigDecimal.valueOf(productRequest.quantity())));

            orderItems.add(orderItem);
        }

        return orderItems;
    }

    private List<String> checkStockWarnings(Set<OrderItem> orderItems) {
        List<String> warnings = new ArrayList<>();

        for (OrderItem item : orderItems) {
            Product product = item.getProduct();
            if (product.getStockQuantity() < item.getQuantity()) {
                warnings.add(product.getName() + " için yetersiz stok (İstenilen: " +
                        item.getQuantity() + ", Mevcut: " + product.getStockQuantity() + ")");
            } else if (product.getStockQuantity() - item.getQuantity() < 5) {
                warnings.add(product.getName() + " için stok azalıyor (Kalan: " +
                        (product.getStockQuantity() - item.getQuantity()) + ")");
            }
        }

        return warnings;
    }

    private String determineStockStatus(Product product, Integer requestedQuantity) {
        int availableStock = product.getStockQuantity();

        if (availableStock == 0) {
            return "OUT_OF_STOCK";
        } else if (availableStock < requestedQuantity) {
            return "INSUFFICIENT_STOCK";
        } else if (availableStock <= 10) { // 10'dan az ise düşük stok
            return "LOW_STOCK";
        } else {
            return "IN_STOCK";
        }
    }

    private String getMonthName(Integer month) {
        String[] monthNames = {
                "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
                "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
        };
        return monthNames[month - 1];
    }

    private DealerPerformanceData calculateTotalSummary(Collection<DealerPerformanceData> performances) {
        Long totalOrders = performances.stream().mapToLong(DealerPerformanceData::totalOrders).sum();
        Long totalCompleted = performances.stream().mapToLong(DealerPerformanceData::completedOrders).sum();
        Long totalCancelled = performances.stream().mapToLong(DealerPerformanceData::cancelledOrders).sum();
        BigDecimal totalRevenue = performances.stream()
                .map(DealerPerformanceData::totalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgOrderValue = totalOrders > 0 ?
                totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, BigDecimal.ROUND_HALF_UP) :
                BigDecimal.ZERO;

        Double completionRate = totalOrders > 0 ?
                (totalCompleted.doubleValue() / totalOrders.doubleValue()) * 100 : 0.0;

        Double cancellationRate = totalOrders > 0 ?
                (totalCancelled.doubleValue() / totalOrders.doubleValue()) * 100 : 0.0;

        return new DealerPerformanceData(
                null, "Genel Toplam",
                totalOrders, totalCompleted, totalCancelled,
                totalRevenue, avgOrderValue, completionRate, cancellationRate, 0
        );
    }

    private DealerPerformanceData calculateDealerPerformance(List<Order> dealerOrders) {
        Long totalOrders = (long) dealerOrders.size();
        BigDecimal totalRevenue = dealerOrders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgOrderValue = totalOrders > 0 ?
                totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, BigDecimal.ROUND_HALF_UP) :
                BigDecimal.ZERO;

        Long completedOrders = dealerOrders.stream()
                .mapToLong(o -> o.getOrderStatus() == OrderStatus.COMPLETED ? 1 : 0).sum();

        Double completionRate = totalOrders > 0 ?
                (completedOrders.doubleValue() / totalOrders.doubleValue()) * 100 : 0.0;

        Long cancelledOrders = dealerOrders.stream()
                .mapToLong(o -> o.getOrderStatus() == OrderStatus.CANCELLED ? 1 : 0).sum();

        Double cancellationRate = totalOrders > 0 ?
                (cancelledOrders.doubleValue() / totalOrders.doubleValue()) * 100 : 0.0;

        String dealerName = dealerOrders.isEmpty() ? "Bilinmeyen" :
                dealerOrders.get(0).getUser().getDealer().getName();

        Long dealerId = dealerOrders.isEmpty() ? null :
                dealerOrders.get(0).getUser().getDealer().getId();


        return new DealerPerformanceData(
                dealerId,
                dealerName,
                totalOrders,
                completedOrders,
                cancelledOrders,
                totalRevenue,
                avgOrderValue,
                completionRate,
                cancellationRate,
                0 // ranking will be set separately
        );
    }

    // OrderService.java'ya eklenecek metodlar (OrderHistory olmadan):

    /**
     * Düzenlenmiş sipariş detaylarını getir (müşteri için)
     */
    public EditedOrderResponse getEditedOrderDetails(Long orderId, AppUser currentUser) {
        logger.info("Fetching edited order details for order: " + orderId + ", user: " + currentUser.getId());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));

        // Kullanıcı kendi siparişini mi görüyor kontrol et
        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Bu siparişin detaylarını görme yetkiniz yok");
        }

        // Sipariş düzenlenmiş mi kontrol et
        if (order.getOrderStatus() != OrderStatus.EDITED_PENDING_APPROVAL) {
            throw new IllegalStateException("Bu sipariş düzenlenmemiş veya farklı durumda: " + order.getOrderStatus());
        }

        OrderResponse editedOrder = orderMapper.toDto(order);

        // Admin notlarından değişiklikleri çıkar
        List<OrderChangeDetail> changes = extractChangesFromAdminNotes(order.getAdminNotes());

        // Admin notlarından orijinal totali çıkar
        BigDecimal originalTotal = extractOriginalTotalFromAdminNotes(order.getAdminNotes());
        BigDecimal editedTotal = editedOrder.totalAmount();
        BigDecimal totalDifference = editedTotal.subtract(originalTotal);

        // Admin bilgilerini al
        String editedBy = getLastEditorName(order);
        LocalDateTime editedDate = order.getUpdatedAt();
        String editReason = extractEditReasonFromAdminNotes(order.getAdminNotes());

        return new EditedOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                null, // originalOrder artık gerekli değil
                editedOrder,
                editReason,
                order.getAdminNotes(),
                editedDate,
                editedBy,
                changes,
                originalTotal,
                editedTotal,
                totalDifference,
                true // Her düzenleme müşteri onayı gerektirir
        );
    }

    /**
     * Düzenlenmiş siparişi onayla veya reddet (müşteri tarafından)
     */
    @Transactional
    public OrderResponse approveOrRejectEditedOrder(Long orderId, AppUser currentUser,
                                                    Boolean approved, String customerNote) {
        logger.info("Customer " + (approved ? "approving" : "rejecting") + " edited order: " + orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));

        // Yetki kontrolü
        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Bu siparişi onaylama/reddetme yetkiniz yok");
        }

        // Durum kontrolü
        if (order.getOrderStatus() != OrderStatus.EDITED_PENDING_APPROVAL) {
            throw new IllegalStateException("Bu sipariş düzenleme onayı beklemediği için işlem yapılamaz. " +
                    "Mevcut durum: " + order.getOrderStatus());
        }

        if (approved) {
            // Müşteri onayladı - sipariş APPROVED durumuna geç
            order.setOrderStatus(OrderStatus.APPROVED);

            // Müşteri notunu ekle
            if (customerNote != null && !customerNote.trim().isEmpty()) {
                String currentNotes = order.getNotes() != null ? order.getNotes() : "";
                order.setNotes(currentNotes + "\n[Müşteri onayı: " + customerNote + "]");
            }

            // Admin notuna onay bilgisi ekle
            String currentAdminNotes = order.getAdminNotes() != null ? order.getAdminNotes() : "";
            order.setAdminNotes(currentAdminNotes + "\n[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) +
                    " - Müşteri düzenlemeleri onayladı]");

            logger.info("Customer approved edited order: " + orderId);

        } else {
            // Müşteri reddetti - sipariş CANCELLED durumuna geç ve stokları iade et
            order.setOrderStatus(OrderStatus.CANCELLED);

            // Stok iade et
            updateProductStocks(order.getItems(), false); // false = iade et

            // Red nedenini ekle
            String rejectionNote = customerNote != null && !customerNote.trim().isEmpty() ?
                    customerNote : "Müşteri düzenlenmiş siparişi reddetti";

            String currentNotes = order.getNotes() != null ? order.getNotes() : "";
            order.setNotes(currentNotes + "\n[Müşteri reddi: " + rejectionNote + "]");

            // Admin notuna red bilgisi ekle
            String currentAdminNotes = order.getAdminNotes() != null ? order.getAdminNotes() : "";
            order.setAdminNotes(currentAdminNotes + "\n[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) +
                    " - Müşteri düzenlemeleri reddetti: " + rejectionNote + "]");

            logger.info("Customer rejected edited order: " + orderId);
        }

        Order savedOrder = orderRepository.save(order);
        return orderMapper.toDto(savedOrder);
    }

    /**
     * Admin sipariş düzenleme metodunu güncelle (durum yönetimi ekle)
     */
    @Transactional
    public OrderResponse editOrderByAdmin(Long orderId, OrderRequest updatedRequest, AppUser admin, String editReason) {
        logger.info("Admin editing order: " + orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));

        if (order.getOrderStatus() != OrderStatus.PENDING && order.getOrderStatus() != OrderStatus.APPROVED) {
            throw new IllegalStateException("Sadece beklemede veya onaylanmış siparişler düzenlenebilir");
        }

        // Orijinal değerleri admin notlarına kaydet
        BigDecimal originalTotal = order.getTotalAmount();
        String originalItemsInfo = order.getItems().stream()
                .map(item -> item.getProduct().getName() + " x" + item.getQuantity())
                .collect(Collectors.joining(", "));

        // Mevcut stokları geri ver
        updateProductStocks(order.getItems(), false);

        // Yeni order item'ları oluştur
        Set<OrderItem> newOrderItems = createOrderItems(order, updatedRequest.products());

        // Yeni stok kontrolü
        validateStockAvailability(newOrderItems);

        // Order güncelle
        order.getItems().clear();
        order.setItems(newOrderItems);

        // Fiyat hesaplama
        BigDecimal newSubtotal = calculateSubtotal(newOrderItems);

        // İndirim yeniden uygula (varsa)
        BigDecimal discountAmount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        if (order.getAppliedDiscount() != null) {
            discountAmount = calculateDiscountAmount(order.getAppliedDiscount(), newSubtotal, newOrderItems);
            order.setDiscountAmount(discountAmount);
        }

        order.setTotalAmount(newSubtotal.subtract(discountAmount));

        // Sipariş düzenleme onayı gerekiyor
        order.setOrderStatus(OrderStatus.EDITED_PENDING_APPROVAL);

        // Yeni kalem bilgilerini hazırla
        String newItemsInfo = newOrderItems.stream()
                .map(item -> item.getProduct().getName() + " x" + item.getQuantity())
                .collect(Collectors.joining(", "));

        // Admin notlarını güncelle - düzenleme detaylarını ekle
        String editDetails = String.format(
                "\n[%s - %s %s düzenledi: %s]" +
                        "\nÖnceki kalemler: %s (Toplam: %s TL)" +
                        "\nYeni kalemler: %s (Toplam: %s TL)" +
                        "\nFark: %s TL",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
                admin.getFirstName(), admin.getLastName(),
                editReason != null ? editReason : "Düzenleme nedeni belirtilmemiş",
                originalItemsInfo, originalTotal,
                newItemsInfo, order.getTotalAmount(),
                order.getTotalAmount().subtract(originalTotal)
        );

        String currentAdminNotes = order.getAdminNotes() != null ? order.getAdminNotes() : "";
        order.setAdminNotes(currentAdminNotes + editDetails);

        // Yeni stokları rezerve et
        updateProductStocks(newOrderItems, true);

        Order savedOrder = orderRepository.save(order);
        try {
            mailService.sendOrderEditedNotificationToCustomer(savedOrder);
        } catch (Exception e) {
            logger.warning("Failed to send order edited notification email: " + e.getMessage());
        }
        logger.info("Order marked as EDITED_PENDING_APPROVAL - customer approval required");

        return orderMapper.toDto(savedOrder);
    }

// ==================== PRIVATE HELPER METHODS ====================

    /**
     * Admin notlarından değişiklikleri çıkar
     */
    private List<OrderChangeDetail> extractChangesFromAdminNotes(String adminNotes) {
        List<OrderChangeDetail> changes = new ArrayList<>();

        if (adminNotes == null || !adminNotes.contains("düzenledi:")) {
            return changes;
        }

        // Admin notlarından değişiklik bilgilerini parse et
        String[] lines = adminNotes.split("\n");
        for (String line : lines) {
            if (line.contains("Önceki kalemler:") && line.contains("Yeni kalemler:")) {
                changes.add(new OrderChangeDetail(
                        "ORDER_EDITED",
                        null,
                        "Sipariş düzenlendi",
                        null,
                        null,
                        null,
                        null,
                        "Admin tarafından sipariş düzenlendi. Detaylar admin notlarında mevcut."
                ));
                break;
            }
        }

        return changes;
    }

    /**
     * Admin notlarından orijinal totali çıkar
     */
    private BigDecimal extractOriginalTotalFromAdminNotes(String adminNotes) {
        if (adminNotes == null) {
            return BigDecimal.ZERO;
        }

        // "Toplam: XXX TL" formatını ara
        String[] lines = adminNotes.split("\n");
        for (String line : lines) {
            if (line.contains("Önceki kalemler:") && line.contains("Toplam:")) {
                try {
                    String totalPart = line.substring(line.indexOf("Toplam:") + 7);
                    totalPart = totalPart.substring(0, totalPart.indexOf("TL")).trim();
                    return new BigDecimal(totalPart);
                } catch (Exception e) {
                    logger.warning("Could not parse original total from admin notes: " + e.getMessage());
                }
            }
        }

        return BigDecimal.ZERO;
    }

    /**
     * Son düzenleyenin adını al
     */
    private String getLastEditorName(Order order) {
        if (order.getAdminNotes() != null && order.getAdminNotes().contains("düzenledi:")) {
            String[] lines = order.getAdminNotes().split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (line.contains("düzenledi:")) {
                    // "[21.08.2025 14:30 - Ahmet Yılmaz düzenledi:" formatından ismi çıkar
                    try {
                        String namePart = line.substring(line.indexOf(" - ") + 3, line.indexOf(" düzenledi:"));
                        return namePart;
                    } catch (Exception e) {
                        logger.warning("Could not parse editor name: " + e.getMessage());
                    }
                }
            }
        }
        return "Admin";
    }

    /**
     * Admin notlarından düzenleme nedenini çıkar
     */
    private String extractEditReasonFromAdminNotes(String adminNotes) {
        if (adminNotes == null || !adminNotes.contains("düzenledi:")) {
            return "Düzenleme nedeni belirtilmemiş";
        }

        String[] lines = adminNotes.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.contains("düzenledi:")) {
                try {
                    String reasonStart = line.substring(line.indexOf("düzenledi:") + "düzenledi:".length());
                    String reason = reasonStart.substring(0, reasonStart.indexOf("]")).trim();
                    return reason.isEmpty() ? "Düzenleme nedeni belirtilmemiş" : reason;
                } catch (Exception e) {
                    logger.warning("Could not parse edit reason: " + e.getMessage());
                }
            }
        }

        return "Düzenleme nedeni belirtilmemiş";
    }
}
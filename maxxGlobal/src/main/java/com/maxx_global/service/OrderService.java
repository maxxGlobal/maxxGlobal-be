package com.maxx_global.service;

import com.maxx_global.dto.discount.DiscountResponse;
import com.maxx_global.dto.order.*;
import com.maxx_global.entity.*;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.DiscountType;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.OrderStatus;
import com.maxx_global.event.*;
import com.maxx_global.repository.OrderItemRepository;
import com.maxx_global.repository.OrderRepository;
import com.maxx_global.repository.ProductPriceRepository;
import com.maxx_global.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final OrderItemRepository orderItemRepository;

    // Facade Pattern - Diğer servisleri çağır
    private final DealerService dealerService;
    private final DiscountService discountService;
    private final OrderPdfService orderPdfService;
    private final ApplicationEventPublisher applicationEventPublisher; // ✅ EKLE



    public OrderService(OrderRepository orderRepository,
                        ProductPriceRepository productPriceRepository,
                        ProductRepository productRepository,
                        OrderMapper orderMapper, OrderItemRepository orderItemRepository,
                        DealerService dealerService,
                        DiscountService discountService,
                        OrderPdfService orderPdfService,
                        ApplicationEventPublisher applicationEventPublisher) {
        this.orderRepository = orderRepository;
        this.productPriceRepository = productPriceRepository;
        this.productRepository = productRepository;
        this.orderMapper = orderMapper;
        this.orderItemRepository = orderItemRepository;
        this.dealerService = dealerService;
        this.discountService = discountService;
        this.orderPdfService = orderPdfService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    // ==================== END USER METHODS ====================

    /**
     * 1. Yeni sipariş oluşturur - Fiyat hesaplama ve indirim dahil
     */
    // OrderService.java - createOrder metodunda discount validation düzenlemeleri

    @Transactional
    public OrderResponse createOrder(OrderRequest request, AppUser currentUser) {
        logger.info("Creating order for user: " + currentUser.getId() + ", dealer: " + request.dealerId());

        // Validation
        request.validate();

        // Dealer varlık kontrolü
        dealerService.getDealerById(request.dealerId());

        // Kullanıcının bu dealer ile ilişkisi var mı kontrol et
        validateUserDealerRelation(currentUser, request.dealerId());

        // ProductPrice'ları kontrol et ve currency'leri validate et
        List<ProductPrice> productPrices = validateAndGetProductPrices(request.products());
        CurrencyType orderCurrency = productPrices.get(0).getCurrency();

        // Order entity oluştur
        Order order = new Order();
        order.setUser(currentUser);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setOrderDate(LocalDateTime.now());
        order.setCurrency(orderCurrency);
        order.setOrderNumber(generateOrderNumber());
        order.setNotes(request.notes());

        // Order items oluştur ve fiyat hesapla
        Set<OrderItem> orderItems = createOrderItemsWithValidation(order, request.products(), productPrices);
        order.setItems(orderItems);

        // Toplam tutarı hesapla
        BigDecimal subtotal = calculateSubtotal(orderItems);

        // İndirim uygula VE validate et
        BigDecimal discountAmount = BigDecimal.ZERO;
        Discount appliedDiscount = null;

        if (request.discountId() != null) {
            appliedDiscount = validateAndApplyDiscount(
                    request.discountId(),
                    request.dealerId(),
                    subtotal,
                    orderItems,
                    currentUser
            );

            if (appliedDiscount != null) {
                discountAmount = calculateDiscountAmountForOrder(appliedDiscount, subtotal, orderItems);
                order.setAppliedDiscount(appliedDiscount);

                logger.info("Applied discount: " + appliedDiscount.getName() +
                        " (Amount: " + discountAmount + ", Type: " + appliedDiscount.getDiscountType() + ")");
            }
        }

        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(subtotal.subtract(discountAmount));

        // Stok kontrolü yap
        validateStockAvailability(orderItems);

        // Siparişi kaydet
        Order savedOrder = orderRepository.save(order);
        applicationEventPublisher.publishEvent(new OrderCreatedEvent(savedOrder));

        // Stok güncelle (rezerve et)
        updateProductStocks(orderItems, true); // true = rezerve et

        // ✅ YENİ - İndirim kullanımını kaydet
        if (appliedDiscount != null) {
            recordDiscountUsageAfterOrder(savedOrder, currentUser);
        }

        logger.info("Order created successfully: " + savedOrder.getOrderNumber() +
                ", total: " + savedOrder.getTotalAmount() +
                ", discount applied: " + (appliedDiscount != null ? appliedDiscount.getName() : "None"));

        return orderMapper.toDto(savedOrder);
    }

// ==================== YENİ DISCOUNT VALIDATION METODLARI ====================

    /**
     * Comprehensive discount validation ve uygulama
     */
    private Discount validateAndApplyDiscount(Long discountId, Long dealerId,
                                              BigDecimal subtotal, Set<OrderItem> orderItems,
                                              AppUser currentUser) {
        logger.info("Validating and applying discount: " + discountId + " for dealer: " + dealerId);

        try {
            // 1. İndirim var mı kontrol et
            DiscountResponse discountResponse = discountService.getDiscountById(discountId);

            // 2. İndirim aktif mi kontrol et
            if (!discountResponse.isActive()) {
                throw new IllegalArgumentException("Seçilen indirim aktif değil: " + discountResponse.name());
            }

            // 3. İndirim geçerlilik tarihleri kontrol et
            if (!discountResponse.isValidNow()) {
                throw new IllegalArgumentException("Seçilen indirim şu an geçerli değil: " +
                        discountResponse.name() + " (Başlangıç: " + discountResponse.startDate() +
                        ", Bitiş: " + discountResponse.endDate() + ")");
            }

            // ✅ YENİ - 4. Toplam kullanım limiti kontrolü
            if (discountResponse.usageLimit() != null && discountResponse.usageCount() != null) {
                if (discountResponse.usageCount() >= discountResponse.usageLimit()) {
                    throw new IllegalArgumentException("İndirim kullanım limiti dolmuş: " +
                            discountResponse.name() + " (" + discountResponse.usageCount() +
                            "/" + discountResponse.usageLimit() + ")");
                }
            }

            // ✅ YENİ - 5. Kullanıcı bazlı kullanım limiti kontrolü
            if (discountResponse.usageLimitPerCustomer() != null) {
                Long userUsageCount = discountService.getUserDiscountUsageCount(discountId, currentUser.getId());
                if (userUsageCount >= discountResponse.usageLimitPerCustomer()) {
                    throw new IllegalArgumentException("Bu indirimi kullanma limitinize ulaştınız: " +
                            discountResponse.name() + " (" + userUsageCount +
                            "/" + discountResponse.usageLimitPerCustomer() + " kullanım)");
                }
            }

            // ✅ YENİ - 6. Bayi bazlı tekrar kullanım engellemesi (isteğe bağlı - business logic'e göre)
            // Eğer bir bayi aynı indirimi sadece bir kez kullanabilecekse:
            if (discountService.hasDealerUsedDiscount(discountId, dealerId)) {
                throw new IllegalArgumentException("Bu bayi bu indirimi daha önce kullanmış: " +
                        discountResponse.name());
            }

            // 7. İndirim bu bayi için geçerli mi kontrol et
            if (!isDiscountValidForDealer(discountResponse, dealerId)) {
                throw new IllegalArgumentException("Seçilen indirim bu bayi için geçerli değil: " +
                        discountResponse.name());
            }

            // 8. İndirim bu ürünler için geçerli mi kontrol et
            if (!isDiscountValidForProducts(discountResponse, orderItems)) {
                throw new IllegalArgumentException("Seçilen indirim bu ürünler için geçerli değil: " +
                        discountResponse.name());
            }

            // 9. Minimum sipariş tutarı kontrolü
            if (discountResponse.minimumOrderAmount() != null &&
                    subtotal.compareTo(discountResponse.minimumOrderAmount()) < 0) {
                throw new IllegalArgumentException("Minimum sipariş tutarı karşılanmıyor. " +
                        "Gerekli: " + discountResponse.minimumOrderAmount() +
                        ", Mevcut: " + subtotal);
            }

            // 10. Kullanıcının bu indirimi kullanma yetkisi var mı kontrol et
            if (!canUserUseDiscount(currentUser, dealerId)) {
                throw new IllegalArgumentException("Bu indirimi kullanma yetkiniz bulunmuyor: " +
                        discountResponse.name());
            }

            // ✅ GÜNCELLENEN - 11. Genel kullanılabilirlik kontrolü
            if (!discountService.canUseDiscount(discountId, currentUser.getId(), dealerId)) {
                throw new IllegalArgumentException("İndirim şu anda kullanılamaz: " +
                        discountResponse.name());
            }

            // Tüm validationlar geçti - discount entity'yi repository'den getir
            Discount discount = discountService.getDiscountEntityById(discountId);

            return discount;

        } catch (EntityNotFoundException e) {
            throw new IllegalArgumentException("Seçilen indirim bulunamadı: " + discountId);
        } catch (IllegalArgumentException e) {
            logger.warning("Discount validation failed: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.severe("Unexpected error during discount validation: " + e.getMessage());
            throw new RuntimeException("İndirim kontrolü sırasında hata oluştu: " + e.getMessage());
        }
    }

    private void recordDiscountUsageAfterOrder(Order order, AppUser user) {
        if (order.getAppliedDiscount() != null && order.getDiscountAmount() != null &&
                order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            try {
                discountService.recordDiscountUsage(
                        order.getAppliedDiscount(),
                        user,
                        user.getDealer(),
                        order,
                        order.getDiscountAmount()
                );
                logger.info("Discount usage recorded for order: " + order.getOrderNumber() +
                        ", discount: " + order.getAppliedDiscount().getName() +
                        ", amount: " + order.getDiscountAmount());
            } catch (Exception e) {
                logger.severe("Error recording discount usage for order " + order.getOrderNumber() + ": " + e.getMessage());
                // Bu hata sipariş oluşturulmasını engellemez, sadece log'lanır
            }
        }
    }

    /**
     * Sipariş iptal/red edildiğinde discount usage'i sil
     */
    private void removeDiscountUsageAfterOrderCancellation(Order order) {
        if (order.getAppliedDiscount() != null) {
            try {
                discountService.removeDiscountUsage(order.getId());
                logger.info("Discount usage removed for order: " + order.getOrderNumber() +
                        ", discount: " + order.getAppliedDiscount().getName());
            } catch (Exception e) {
                logger.severe("Error removing discount usage for order " + order.getOrderNumber() + ": " + e.getMessage());
                // Bu hata iptal işlemini engellemez, sadece log'lanır
            }
        }
    }

    /**
     * İndirim bu bayi için geçerli mi kontrol et
     */
    private boolean isDiscountValidForDealer(DiscountResponse discount, Long dealerId) {
        // Eğer indirim belirli bayiler için kısıtlı değilse (genel indirim), geçerlidir
        if (discount.applicableDealers() == null || discount.applicableDealers().isEmpty()) {
            logger.info("Discount " + discount.name() + " is valid for all dealers");
            return true;
        }

        // Bayi listesinde var mı kontrol et
        boolean isValid = discount.applicableDealers().stream()
                .anyMatch(dealer -> dealer.id().equals(dealerId));

        logger.info("Discount " + discount.name() + " is " +
                (isValid ? "valid" : "not valid") + " for dealer: " + dealerId);

        return isValid;
    }

    /**
     * İndirim bu ürünler için geçerli mi kontrol et
     */
    private boolean isDiscountValidForProducts(DiscountResponse discount, Set<OrderItem> orderItems) {
        // Eğer indirim belirli ürünler için kısıtlı değilse (genel indirim), geçerlidir
        if (discount.applicableProducts() == null || discount.applicableProducts().isEmpty()) {
            logger.info("Discount " + discount.name() + " is valid for all products");
            return true;
        }

        // En az bir ürün indirim listesinde var mı kontrol et
        Set<Long> discountProductIds = discount.applicableProducts().stream()
                .map(product -> product.id())
                .collect(Collectors.toSet());

        boolean hasValidProduct = orderItems.stream()
                .anyMatch(item -> discountProductIds.contains(item.getProduct().getId()));

        if (!hasValidProduct) {
            String orderProductNames = orderItems.stream()
                    .map(item -> item.getProduct().getName())
                    .collect(Collectors.joining(", "));

            String discountProductNames = discount.applicableProducts().stream()
                    .map(product -> product.name())
                    .collect(Collectors.joining(", "));

            logger.warning("Discount " + discount.name() + " is not valid for any products in order. " +
                    "Order products: [" + orderProductNames + "], " +
                    "Discount products: [" + discountProductNames + "]");
        }

        return hasValidProduct;
    }

    /**
     * Kullanıcı bu indirimi kullanabilir mi kontrol et
     */
    private boolean canUserUseDiscount( AppUser user, Long dealerId) {
        // Temel kontrol: kullanıcının dealer'ı indirim için geçerli mi
        if (user.getDealer() == null || !user.getDealer().getId().equals(dealerId)) {
            logger.warning("User dealer mismatch for discount usage");
            return false;
        }

        return true;
    }

    /**
     * İndirim kullanım limiti kontrolü - Discount entity'deki usageLimit field'ını kullan
     */
    private boolean hasReachedDiscountUsageLimit(Long discountId, AppUser user) {
        try {
            // Discount entity'yi getir
            Discount discount = discountService.getDiscountEntityById(discountId);

            // Usage limit kontrolü - entity'de usageLimit field'ı varsa
            Integer usageLimit = discount.getUsageLimit(); // Bu field'ın Discount entity'de olduğunu varsayıyorum

            if (usageLimit == null || usageLimit <= 0) {
                // Limit yok veya sınırsız
                logger.info("No usage limit set for discount: " + discount.getName());
                return false;
            }

            // Bu indirimin kaç kez kullanıldığını say
            // Bu query için OrderRepository'ye yeni method eklemen gerekebilir
            Long currentUsageCount = orderRepository.countByAppliedDiscountIdAndOrderStatus(
                    discountId, OrderStatus.COMPLETED); // Sadece tamamlanmış siparişleri say

            boolean limitReached = currentUsageCount >= usageLimit;

            logger.info("Discount usage check - Discount: " + discount.getName() +
                    ", Usage limit: " + usageLimit +
                    ", Current usage: " + currentUsageCount +
                    ", Limit reached: " + limitReached);

            return limitReached;

        } catch (Exception e) {
            logger.severe("Error checking discount usage limit: " + e.getMessage());
            // Hata durumunda güvenli tarafta kal - limiti aşmış say
            return true;
        }
    }

    /**
     * Sipariş için indirim tutarını hesapla
     */
    private BigDecimal calculateDiscountAmountForOrder(Discount discount, BigDecimal subtotal,
                                                       Set<OrderItem> orderItems) {
        logger.info("Calculating discount amount for discount: " + discount.getName() +
                ", type: " + discount.getDiscountType() +
                ", value: " + discount.getDiscountValue() +
                ", subtotal: " + subtotal);

        BigDecimal discountAmount = BigDecimal.ZERO;

        try {
            if (discount.getDiscountType() == DiscountType.PERCENTAGE) {
                // Yüzde indirimi
                if (discount.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new IllegalArgumentException("İndirim yüzdesi 100'den büyük olamaz: " +
                            discount.getDiscountValue());
                }

                discountAmount = subtotal.multiply(discount.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            } else if (discount.getDiscountType() == DiscountType.FIXED_AMOUNT) {
                // Sabit tutar indirimi
                discountAmount = discount.getDiscountValue();

                // Sabit tutar subtotal'dan büyük olamaz
                if (discountAmount.compareTo(subtotal) > 0) {
                    logger.warning("Fixed discount amount (" + discountAmount +
                            ") is greater than subtotal (" + subtotal + "). Using subtotal as discount.");
                    discountAmount = subtotal;
                }
            }

            // Maksimum indirim limiti kontrolü
            if (discount.getMaximumDiscountAmount() != null &&
                    discountAmount.compareTo(discount.getMaximumDiscountAmount()) > 0) {
                logger.info("Applied maximum discount limit. Original: " + discountAmount +
                        ", Limited to: " + discount.getMaximumDiscountAmount());
                discountAmount = discount.getMaximumDiscountAmount();
            }

            // Negatif değer kontrolü
            if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
                logger.warning("Calculated discount amount is negative: " + discountAmount + ". Setting to zero.");
                discountAmount = BigDecimal.ZERO;
            }

            logger.info("Final discount amount calculated: " + discountAmount);

        } catch (Exception e) {
            logger.severe("Error calculating discount amount: " + e.getMessage());
            throw new RuntimeException("İndirim tutarı hesaplanırken hata oluştu: " + e.getMessage());
        }

        return discountAmount;
    }

// ==================== CALCULATE ORDER TOTAL METODUNU GÜNCELLE ====================

    /**
     * calculateOrderTotal metodunda da discount validation kullan
     */
    public OrderCalculationResponse calculateOrderTotal(OrderRequest request, AppUser currentUser) {
        logger.info("Calculating order total for dealer: " + request.dealerId());

        // Validation
        request.validate();

        // Dealer varlık kontrolü
        dealerService.getDealerById(request.dealerId());

        // Kullanıcının bu dealer ile ilişkisi var mı kontrol et
        validateUserDealerRelation(currentUser, request.dealerId());

        // Currency validation ile items oluştur
        Set<OrderItem> orderItems = createOrderItemsForCalculation(request.products());

        // Currency'i items'dan al
        CurrencyType orderCurrency = orderItems.iterator().next().getProduct() != null ?
                getProductPriceCurrency(orderItems.iterator().next().getProduct().getId(), request.dealerId()) :
                CurrencyType.TRY;

        // Subtotal hesapla
        BigDecimal subtotal = calculateSubtotal(orderItems);

        // İndirim hesapla (varsa) - YENİ VALIDATION İLE
        BigDecimal discountAmount = BigDecimal.ZERO;
        String discountDescription = null;

        if (request.discountId() != null) {
            try {
                Discount appliedDiscount = validateAndApplyDiscount(
                        request.discountId(),
                        request.dealerId(),
                        subtotal,
                        orderItems,
                        currentUser
                );

                if (appliedDiscount != null) {
                    discountAmount = calculateDiscountAmountForOrder(appliedDiscount, subtotal, orderItems);
                    discountDescription = appliedDiscount.getName() + " (" +
                            appliedDiscount.getDiscountType() + ": " +
                            appliedDiscount.getDiscountValue() +
                            (appliedDiscount.getDiscountType() == DiscountType.PERCENTAGE ? "%" : " TL") + ")";

                    logger.info("Discount calculation successful: " + discountDescription +
                            ", Amount: " + discountAmount);
                }
            } catch (IllegalArgumentException e) {
                // Hesaplama sırasında indirim hatasını log'la ama devam et
                logger.warning("Discount validation failed during calculation: " + e.getMessage());
                discountDescription = "İndirim uygulanamadı: " + e.getMessage();
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

        return new OrderCalculationResponse(
                subtotal,
                discountAmount,
                totalAmount,
                orderCurrency.name(),
                orderItems.size(),
                itemCalculations,
                stockWarnings,
                discountDescription
        );
    }
    private Set<OrderItem> createOrderItemsWithValidation(Order order, List<OrderProductRequest> productRequests,
                                                          List<ProductPrice> validatedPrices) {
        Set<OrderItem> orderItems = new HashSet<>();

        // ProductRequest ile ProductPrice'ları eşleştir
        for (int i = 0; i < productRequests.size(); i++) {
            OrderProductRequest productRequest = productRequests.get(i);
            ProductPrice productPrice = validatedPrices.get(i);

            // Double-check: ID'ler uyuşuyor mu?
            if (!productPrice.getId().equals(productRequest.productPriceId())) {
                throw new RuntimeException("ProductPrice validation hatası");
            }

            // OrderItem oluştur
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(productPrice.getProduct());
            orderItem.setQuantity(productRequest.quantity());
            orderItem.setProductPriceId(productPrice.getId());
            orderItem.setUnitPrice(productPrice.getAmount());
            orderItem.setTotalPrice(productPrice.getAmount().multiply(BigDecimal.valueOf(productRequest.quantity())));

            orderItems.add(orderItem);

            logger.info("Created OrderItem: " + productPrice.getProduct().getName() +
                    " x" + productRequest.quantity() +
                    " @ " + productPrice.getAmount() + " " + productPrice.getCurrency());
        }

        return orderItems;
    }

    private List<ProductPrice> validateAndGetProductPrices(List<OrderProductRequest> productRequests) {
        List<ProductPrice> productPrices = new ArrayList<>();
        CurrencyType orderCurrency = null;

        for (OrderProductRequest productRequest : productRequests) {
            // ProductPrice'ı bul
            ProductPrice productPrice = productPriceRepository.findById(productRequest.productPriceId())
                    .orElseThrow(() -> new EntityNotFoundException("Ürün fiyatı bulunamadı: " + productRequest.productPriceId()));

            // Fiyat geçerli mi kontrol et
            if (!productPrice.isValidNow()) {
                throw new IllegalArgumentException("Ürün fiyatı geçersiz: " + productPrice.getProduct().getName());
            }

            // Currency kontrolü
            if (orderCurrency == null) {
                // İlk ürün - currency'i belirle
                orderCurrency = productPrice.getCurrency();
                logger.info("Order currency set to: " + orderCurrency + " (from first product)");
            } else {
                // Diğer ürünler - currency uyumlu mu kontrol et
                if (!orderCurrency.equals(productPrice.getCurrency())) {
                    throw new IllegalArgumentException(
                            "Siparişte farklı para birimleri kullanılamaz! " +
                                    "Sipariş currency'si: " + orderCurrency +
                                    ", Ürün currency'si: " + productPrice.getCurrency() +
                                    " (Ürün: " + productPrice.getProduct().getName() + ")"
                    );
                }
            }

            productPrices.add(productPrice);
        }

        if (productPrices.isEmpty()) {
            throw new IllegalArgumentException("En az bir ürün seçilmelidir");
        }

        return productPrices;
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

    public OrderResponse getOrderByIdForAdmin(Long orderId) {
        logger.info("Admin fetching order details for id: " + orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));

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

        if(order.getAppliedDiscount() != null){
            removeDiscountUsageAfterOrderCancellation(order);
        }

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

    private BigDecimal calculateSubtotal(Set<OrderItem> orderItems) {
        return orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateDiscountAmount(Discount discount, BigDecimal subtotal, Set<OrderItem> orderItems) {
        BigDecimal discountAmount = null;
        if(discount.getDiscountType() == DiscountType.PERCENTAGE) {
             discountAmount = subtotal.multiply(discount.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (discount.getDiscountType() == DiscountType.FIXED_AMOUNT) {
            discountAmount = discount.getDiscountValue();
        }
        // İndirim hesaplama logic'i
        // DiscountService'den hesaplama yaptırılabilir
        return discountAmount; // Geçici
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

        applicationEventPublisher.publishEvent(new OrderApprovedEvent(savedOrder));

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
        if(order.getAppliedDiscount() != null){
            removeDiscountUsageAfterOrderCancellation(order);
        }
        Order savedOrder = orderRepository.save(order);
        applicationEventPublisher.publishEvent(new OrderRejectedEvent(savedOrder));

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
            order.setAdminNotes(currentNotes + "\n[" + targetStatus.getDisplayName() + ": " + statusNote + "]");
        }

        handleDiscountUsageOnStatusChange(order, previousStatus, targetStatus);

        Order savedOrder = orderRepository.save(order);
        applicationEventPublisher.publishEvent(new OrderStatusChangedEvent(savedOrder,previousStatus.name()));

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
//    public OrderCalculationResponse calculateOrderTotal(OrderRequest request, AppUser currentUser) {
//        logger.info("Calculating order total for dealer: " + request.dealerId());
//
//        // Validation
//        request.validate();
//
//        // Dealer varlık kontrolü
//        dealerService.getDealerById(request.dealerId());
//
//        // Kullanıcının bu dealer ile ilişkisi var mı kontrol et
//        validateUserDealerRelation(currentUser, request.dealerId());
//
//        // ✅ GÜNCELLENEN: Currency validation ile items oluştur
//        Set<OrderItem> orderItems = createOrderItemsForCalculation(request.products());
//
//        // ✅ GÜNCELLENEN: Currency'i items'dan al
//        CurrencyType orderCurrency = orderItems.iterator().next().getProduct() != null ?
//                getProductPriceCurrency(orderItems.iterator().next().getProduct().getId(), request.dealerId()) :
//                CurrencyType.TRY;
//
//        // Subtotal hesapla
//        BigDecimal subtotal = calculateSubtotal(orderItems);
//
//        // İndirim hesapla (varsa)
//        BigDecimal discountAmount = BigDecimal.ZERO;
//        if (request.discountId() != null) {
//            Discount appliedDiscount = applyDiscount(request.discountId(), request.dealerId(), subtotal, orderItems);
//            if (appliedDiscount != null) {
//                discountAmount = calculateDiscountAmount(appliedDiscount, subtotal, orderItems);
//            }
//        }
//
//        BigDecimal totalAmount = subtotal.subtract(discountAmount);
//
//        // Stok durumunu kontrol et (warning olarak)
//        List<String> stockWarnings = checkStockWarnings(orderItems);
//
//        // Ürün detaylarını hazırla
//        List<OrderItemCalculation> itemCalculations = orderItems.stream()
//                .map(item -> new OrderItemCalculation(
//                        item.getProduct().getId(),
//                        item.getProduct().getName(),
//                        item.getProduct().getCode(),
//                        item.getQuantity(),
//                        item.getUnitPrice(),
//                        item.getTotalPrice(),
//                        item.getProduct().getStockQuantity() >= item.getQuantity(),
//                        item.getProduct().getStockQuantity(),
//                        BigDecimal.ZERO, // ürün bazında indirim henüz yok
//                        determineStockStatus(item.getProduct(), item.getQuantity())
//                ))
//                .collect(Collectors.toList());
//
//        String discountDescription = request.discountId() != null ?
//                "İndirim uygulandı" : null;
//
//        return new OrderCalculationResponse(
//                subtotal,
//                discountAmount,
//                totalAmount,
//                orderCurrency.name(), // ✅ GÜNCELLENEN: Gerçek currency dönsün
//                orderItems.size(),
//                itemCalculations,
//                stockWarnings,
//                discountDescription
//        );
//    }
//
    private CurrencyType getProductPriceCurrency(Long productId, Long dealerId) {
        // Bu method performans için cache'lenebilir
        List<ProductPrice> prices = productPriceRepository.findByProductIdAndDealerIdAndStatus(
                productId, dealerId, EntityStatus.ACTIVE);

        return prices.stream()
                .filter(ProductPrice::isValidNow)
                .map(ProductPrice::getCurrency)
                .findFirst()
                .orElse(CurrencyType.TRY);
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



        // ⭐ Repository query metodunu kullan - Belirli item'ı sil
        orderItemRepository.deleteById(itemId);

        // Item'ı koleksiyondan çıkar
        order.getItems().remove(itemToRemove);

        // Toplam tutarı yeniden hesapla
        BigDecimal newSubtotal = calculateSubtotal(order.getItems());

        // ✅ BURADA KULLAN - Eğer indirim varsa ve minimum tutar karşılanmıyorsa
        if (order.getAppliedDiscount() != null) {
            // İndirim hala geçerli mi kontrol et
            if (order.getAppliedDiscount().getMinimumOrderAmount() != null &&
                    newSubtotal.compareTo(order.getAppliedDiscount().getMinimumOrderAmount()) < 0) {

                // İndirim artık geçersiz - kaldır
                removeDiscountUsageAfterOrderCancellation(order);
                order.setAppliedDiscount(null);
                order.setDiscountAmount(BigDecimal.ZERO);
            } else {
                // İndirim tutarını yeniden hesapla
                BigDecimal newDiscountAmount = calculateDiscountAmountForOrder(
                        order.getAppliedDiscount(), newSubtotal, order.getItems());
                order.setDiscountAmount(newDiscountAmount);
            }
        }
        order.setTotalAmount(newSubtotal.subtract(order.getDiscountAmount()));

        // Admin not ekle
        if (removeReason != null && !removeReason.trim().isEmpty()) {
            String currentNotes = order.getAdminNotes() != null ? order.getAdminNotes() : "";
            order.setAdminNotes(currentNotes + "\n[Ürün çıkarıldı - " + product.getName() + ": " + removeReason + "]");
        }

        Order savedOrder = orderRepository.save(order);
        return orderMapper.toDto(savedOrder);
    }

    private void handleDiscountUsageOnStatusChange(Order order, OrderStatus fromStatus, OrderStatus toStatus) {
        if (order.getAppliedDiscount() == null) {
            return; // İndirim yoksa bir şey yapmaya gerek yok
        }

        // Geçerli statuslar - bu statuslarda discount kullanımı sayılır
        List<OrderStatus> validStatuses = Arrays.asList(
                OrderStatus.APPROVED, OrderStatus.SHIPPED, OrderStatus.COMPLETED
        );

        boolean wasValidBefore = validStatuses.contains(fromStatus);
        boolean isValidNow = validStatuses.contains(toStatus);

        if (!wasValidBefore && isValidNow) {
            // Geçersizden geçerliye geçiş - usage kaydet
            logger.info("Order status changed to valid - recording discount usage for order: " + order.getOrderNumber());
            recordDiscountUsageAfterOrder(order, order.getUser());

        } else if (wasValidBefore && !isValidNow) {
            // Geçerliden geçersize geçiş - usage sil
            logger.info("Order status changed to invalid - removing discount usage for order: " + order.getOrderNumber());
            removeDiscountUsageAfterOrderCancellation(order);

            // Eğer CANCELLED veya REJECTED'a geçiyorsa stok da iade et
            if (toStatus == OrderStatus.CANCELLED || toStatus == OrderStatus.REJECTED) {
                updateProductStocks(order.getItems(), false); // false = iade et
            }
        }
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
        // Validation ile aynı mantık
        List<ProductPrice> validatedPrices = validateAndGetProductPrices(productRequests);

        Set<OrderItem> orderItems = new HashSet<>();

        for (int i = 0; i < productRequests.size(); i++) {
            OrderProductRequest productRequest = productRequests.get(i);
            ProductPrice productPrice = validatedPrices.get(i);

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(productPrice.getProduct());
            orderItem.setQuantity(productRequest.quantity());
            orderItem.setProductPriceId(productPrice.getId());
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
    // OrderService.java - approveOrRejectEditedOrder metodunda güncelleme

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

            // ✅ YENİ: Onay event'i (mevcut)
            Order savedOrder = orderRepository.save(order);
            applicationEventPublisher.publishEvent(new OrderApprovedEvent(savedOrder));

            return orderMapper.toDto(savedOrder);

        } else {
            // Müşteri reddetti - sipariş CANCELLED durumuna geç ve stokları iade et
            order.setOrderStatus(OrderStatus.CANCELLED);

            // Stok iade et
            updateProductStocks(order.getItems(), false); // false = iade et

            // Discount usage'i temizle (varsa)
            if(order.getAppliedDiscount() != null){
                removeDiscountUsageAfterOrderCancellation(order);
            }

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

            // ✅ YENİ: Red event'ini publish et
            Order savedOrder = orderRepository.save(order);
            applicationEventPublisher.publishEvent(new OrderEditRejectedEvent(savedOrder, rejectionNote));

            return orderMapper.toDto(savedOrder);
        }
    }

    @Transactional
    public OrderResponse editOrderByAdmin(Long orderId, OrderRequest updatedRequest, AppUser admin, String editReason) {
        logger.info("Admin editing order: " + orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));

        if (order.getOrderStatus() != OrderStatus.PENDING && order.getOrderStatus() != OrderStatus.APPROVED) {
            throw new IllegalStateException("Sadece beklemede veya onaylanmış siparişler düzenlenebilir");
        }

        // ✅ YENİ: Yeni ürünlerin currency validation'ı
        List<ProductPrice> newProductPrices = validateAndGetProductPrices(updatedRequest.products());
        CurrencyType newOrderCurrency = newProductPrices.get(0).getCurrency();

        // ✅ YENİ: Mevcut sipariş currency'si ile uyumlu mu kontrol et
        if (!order.getCurrency().equals(newOrderCurrency)) {
            logger.warning("Currency mismatch in order edit. Original: " + order.getCurrency() +
                    ", New: " + newOrderCurrency + ". Updating order currency.");

            // Currency'i güncelle (admin düzenliyorsa izin verebiliriz)
            order.setCurrency(newOrderCurrency);
        }

        // Orijinal değerleri kaydet
        BigDecimal originalTotal = order.getTotalAmount();
        String originalCurrency = order.getCurrency().name();
        String originalItemsInfo = order.getItems().stream()
                .map(item -> item.getProduct().getName() + " x" + item.getQuantity() +
                        " (" + item.getUnitPrice() + " " + originalCurrency + ")")
                .collect(Collectors.joining(", "));

        // Mevcut stokları geri ver
        updateProductStocks(order.getItems(), false);

        // ⭐ OrderItems'ı temizle
        try {
            orderItemRepository.deleteByOrderId(orderId);
            logger.info("Deleted all OrderItems for order: " + orderId);

            orderItemRepository.flush();
            order.getItems().clear();
            orderRepository.saveAndFlush(order);

        } catch (Exception e) {
            logger.severe("Error deleting order items: " + e.getMessage());
            throw new RuntimeException("Sipariş kalemleri silinirken hata oluştu: " + e.getMessage());
        }

        // ⭐ YENİ ITEM'LARI OLUŞTUR (Currency validation ile)
        try {
            // ✅ GÜNCELLENEN: Validated prices ile items oluştur
            Set<OrderItem> newOrderItems = createOrderItemsWithValidation(order, updatedRequest.products(), newProductPrices);

            // Order ile ilişkilendir
            for (OrderItem item : newOrderItems) {
                item.setOrder(order);
                order.getItems().add(item);
            }

            // Stok kontrolü
            validateStockAvailability(order.getItems());

            logger.info("Created " + newOrderItems.size() + " new OrderItems with currency: " + newOrderCurrency);

        } catch (Exception e) {
            logger.severe("Error creating new order items: " + e.getMessage());
            throw new RuntimeException("Yeni sipariş kalemleri oluşturulurken hata: " + e.getMessage());
        }

        // Fiyat hesaplama
        BigDecimal newSubtotal = calculateSubtotal(order.getItems());
        BigDecimal discountAmount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;

        if (order.getAppliedDiscount() != null) {
            discountAmount = calculateDiscountAmount(order.getAppliedDiscount(), newSubtotal, order.getItems());
            order.setDiscountAmount(discountAmount);
        }

        order.setTotalAmount(newSubtotal.subtract(discountAmount));
        order.setOrderStatus(OrderStatus.EDITED_PENDING_APPROVAL);

        // Admin notlarını güncelle
        String newCurrency = order.getCurrency().name();
        String newItemsInfo = order.getItems().stream()
                .map(item -> item.getProduct().getName() + " x" + item.getQuantity() +
                        " (" + item.getUnitPrice() + " " + newCurrency + ")")
                .collect(Collectors.joining(", "));

        // ✅ GÜNCELLENEN: Currency değişikliği bilgisi de dahil
        String editDetails = String.format(
                "\n[%s - %s %s düzenledi: %s]" +
                        "\nÖnceki kalemler: %s (Toplam: %s %s)" +
                        "\nYeni kalemler: %s (Toplam: %s %s)" +
                        "\nFark: %s %s" +
                        (originalCurrency.equals(newCurrency) ? "" : "\n⚠️ Para birimi değişti: " + originalCurrency + " → " + newCurrency),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
                admin.getFirstName(), admin.getLastName(),
                editReason != null ? editReason : "Düzenleme nedeni belirtilmemiş",
                originalItemsInfo, originalTotal, originalCurrency,
                newItemsInfo, order.getTotalAmount(), newCurrency,
                order.getTotalAmount().subtract(originalTotal), newCurrency
        );

        String currentAdminNotes = order.getAdminNotes() != null ? order.getAdminNotes() : "";
        order.setAdminNotes(currentAdminNotes + editDetails);

        // Yeni stokları rezerve et
        updateProductStocks(order.getItems(), true);

        try {
            // Final save
            Order savedOrder = orderRepository.save(order);
            applicationEventPublisher.publishEvent(new OrderEditedEvent(savedOrder));

            logger.info("Order edited successfully: " + savedOrder.getOrderNumber() +
                    ", new currency: " + savedOrder.getCurrency() +
                    ", new total: " + savedOrder.getTotalAmount());

            return orderMapper.toDto(savedOrder);

        } catch (Exception e) {
            logger.severe("Error saving edited order: " + e.getMessage());
            throw new RuntimeException("Düzenlenmiş sipariş kaydedilirken hata: " + e.getMessage());
        }
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

    // OrderService içine eklenecek PDF oluşturma metodu

    /**
     * Sipariş PDF'i oluştur (yetki kontrolü ile)
     */
    @Transactional(readOnly = true)
    public byte[] generateOrderPdf(Long orderId, AppUser currentUser) {
        logger.info("Generating PDF for order: " + orderId + ", user: " + currentUser.getId());

        // Sipariş varlık kontrolü
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sipariş bulunamadı: " + orderId));



        try {
            // OrderPdfService kullanarak PDF oluştur
            byte[] pdfBytes = orderPdfService.generateOrderPdf(order);

            if (pdfBytes == null || pdfBytes.length == 0) {
                throw new RuntimeException("PDF oluşturulamadı");
            }

            logger.info("PDF generated successfully for order: " + orderId +
                    ", size: " + pdfBytes.length + " bytes");

            return pdfBytes;

        } catch (Exception e) {
            logger.severe("Error generating PDF for order " + orderId + ": " + e.getMessage());
            throw new RuntimeException("PDF oluşturulurken hata oluştu: " + e.getMessage(), e);
        }
    }
}
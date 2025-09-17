package com.maxx_global.service;

import com.maxx_global.dto.discount.*;
import com.maxx_global.entity.*;
import com.maxx_global.enums.DiscountType;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.OrderStatus;
import com.maxx_global.event.DiscountCreatedEvent;
import com.maxx_global.event.DiscountUpdatedEvent;
import com.maxx_global.repository.DiscountRepository;
import com.maxx_global.repository.DiscountUsageRepository;
import com.maxx_global.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DiscountService {

    private static final Logger logger = Logger.getLogger(DiscountService.class.getName());

    private final DiscountRepository discountRepository;
    private final DiscountMapper discountMapper;
    private final OrderRepository orderRepository;
    private final DiscountUsageRepository discountUsageRepository;
    private final ApplicationEventPublisher eventPublisher;

    // Facade Pattern - Diğer servisleri çağır
    private final ProductService productService;
    private final DealerService dealerService;

    private static final List<OrderStatus> VALID_USAGE_STATUSES = Arrays.asList(
            OrderStatus.COMPLETED,
            OrderStatus.APPROVED,
            OrderStatus.SHIPPED,
            OrderStatus.PENDING
    );

    public DiscountService(DiscountRepository discountRepository,
                           DiscountMapper discountMapper,
                           OrderRepository orderRepository,
                           DiscountUsageRepository discountUsageRepository,
                           ApplicationEventPublisher eventPublisher,
                           ProductService productService,
                           DealerService dealerService) {
        this.discountRepository = discountRepository;
        this.discountMapper = discountMapper;
        this.orderRepository = orderRepository;
        this.discountUsageRepository = discountUsageRepository;
        this.eventPublisher = eventPublisher;
        this.productService = productService;
        this.dealerService = dealerService;
    }

    // ==================== READ İŞLEMLERİ ====================

    public Page<DiscountResponse> getAllDiscounts(int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching all discounts - page: " + page + ", size: " + size);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Discount> discounts = discountRepository.findAll(pageable);
        return discounts.map(discountMapper::toDto);
    }

    public List<DiscountResponse> getActiveDiscounts() {
        logger.info("Fetching active discounts");
        List<Discount> discounts = discountRepository.findActiveDiscounts(EntityStatus.ACTIVE);
        return discounts.stream()
                .map(discountMapper::toDto)
                .collect(Collectors.toList());
    }

    public DiscountResponse getDiscountById(Long id) {
        logger.info("Fetching discount with id: " + id);
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Discount not found with id: " + id));
        return discountMapper.toDto(discount);
    }

    public Discount getDiscountEntityById(Long id) {
        logger.info("Fetching discount with id: " + id);
        return discountRepository.findActiveDiscount(id, EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Discount not found with id: " + id));
    }

    public List<DiscountResponse> getDiscountsForProduct(Long productId, Long dealerId) {
        logger.info("Fetching discounts for product: " + productId + ", dealer: " + dealerId);

        // Facade Pattern - Product varlık kontrolü
        productService.getProductSummary(productId);

        List<Discount> discounts;
        if (dealerId != null) {
            // Facade Pattern - Dealer varlık kontrolü
            dealerService.getDealerById(dealerId);
            discounts = discountRepository.findValidDiscountsForProductAndDealer(
                    productId, dealerId, EntityStatus.ACTIVE);
        } else {
            discounts = discountRepository.findValidDiscountsForProduct(productId, EntityStatus.ACTIVE);
        }

        return discounts.stream()
                .map(discountMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<DiscountResponse> getDiscountsForDealer(Long dealerId) {
        logger.info("Fetching all applicable discounts for dealer: " + dealerId);

        // Dealer kontrolü
        dealerService.getDealerById(dealerId);

        // ÖNCELİKLE TEST: Mevcut metodu kullan
        List<Discount> discounts = discountRepository.findValidDiscountsForDealer(
                dealerId, EntityStatus.ACTIVE, LocalDateTime.now());

        // Debug için log ekle
        logger.info("Found " + discounts.size() + " discounts for dealer " + dealerId);
        for (Discount discount : discounts) {
            logger.info("Discount: " + discount.getName() +
                    ", Products: " + discount.getApplicableProducts().size() +
                    ", Dealers: " + discount.getApplicableDealers().size());
        }

        return discounts.stream()
                .filter(discount -> {
                    // Toplam limit kontrolü
                    if (discount.getUsageLimit() != null) {
                        boolean hasUsageLeft = discount.getUsageCount() < discount.getUsageLimit();
                        if (!hasUsageLeft) {
                            logger.info("Filtering out discount " + discount.getName() + " - usage limit reached");
                        }
                        return hasUsageLeft;
                    }
                    return true;
                })
                .filter(discount -> {
                    // Dealer bazlı limit kontrolü
                    if (discount.getUsageLimitPerCustomer() == null) {
                        return true; // sınırsız
                    }
                    long usedByDealer = discountUsageRepository.countByDiscountAndDealer(
                            discount.getId(), dealerId, VALID_USAGE_STATUSES);
                    boolean canUse = usedByDealer < discount.getUsageLimitPerCustomer();
                    if (!canUse) {
                        logger.info("Filtering out discount " + discount.getName() + " - dealer usage limit reached");
                    }
                    return canUse;
                })
                .map(discountMapper::toDto)
                .collect(Collectors.toList());
    }


    public Page<DiscountResponse> searchDiscounts(String searchTerm, int page, int size,
                                                  String sortBy, String sortDirection) {
        logger.info("Searching discounts with term: " + searchTerm);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Discount> discounts = discountRepository.searchDiscounts(searchTerm, pageable);
        return discounts.map(discountMapper::toDto);
    }

    public List<DiscountResponse> getExpiredDiscounts() {
        logger.info("Fetching expired discounts");
        List<Discount> expiredDiscounts = discountRepository.findExpiredDiscounts(EntityStatus.ACTIVE);
        return expiredDiscounts.stream()
                .map(discountMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<DiscountResponse> getUpcomingDiscounts() {
        logger.info("Fetching upcoming discounts");
        List<Discount> upcomingDiscounts = discountRepository.findUpcomingDiscounts(EntityStatus.ACTIVE);
        return upcomingDiscounts.stream()
                .map(discountMapper::toDto)
                .collect(Collectors.toList());
    }

    // ==================== WRITE İŞLEMLERİ ====================

    @Transactional
    public DiscountResponse createDiscount(DiscountRequest request) {
        logger.info("Creating new discount: " + request.name());

        // Validation
        request.validate();

        // Entity oluştur
        Discount discount = discountMapper.toEntity(request);
        discount.setStatus(EntityStatus.ACTIVE);

        // Default değerler
        if (discount.getIsActive() == null) {
            discount.setIsActive(true);
        }

        // Usage limiti set et
        if (request.usageLimit() != null) {
            discount.setUsageLimit(request.usageLimit());
        }
        if (request.usageLimitPerCustomer() != null) {
            discount.setUsageLimitPerCustomer(request.usageLimitPerCustomer());
        }

        // İlişkili ürünleri set et
        if (request.productIds() != null && !request.productIds().isEmpty()) {
            Set<Product> products = validateAndGetProducts(request.productIds());
            discount.setApplicableProducts(products);
        }

        // İlişkili bayileri set et
        if (request.dealerIds() != null && !request.dealerIds().isEmpty()) {
            Set<Dealer> dealers = validateAndGetDealers(request.dealerIds());
            discount.setApplicableDealers(dealers);
        }

        // Çakışan indirimleri kontrol et
        validateDiscountConflicts(discount);

        Discount savedDiscount = discountRepository.save(discount);

        // ✅ YENİ - Event publish et
        try {
            DiscountCreatedEvent event = new DiscountCreatedEvent(savedDiscount);
            eventPublisher.publishEvent(event);
            logger.info("Discount created event published for: " + savedDiscount.getName());
        } catch (Exception e) {
            logger.warning("Failed to publish discount created event: " + e.getMessage());
            // Event hatası discount oluşturmayı engellemez
        }

        logger.info("Discount created successfully with id: " + savedDiscount.getId() +
                ", usage limit: " + savedDiscount.getUsageLimit() +
                ", per customer limit: " + savedDiscount.getUsageLimitPerCustomer());

        return discountMapper.toDto(savedDiscount);
    }

    @Transactional
    public DiscountResponse updateDiscount(Long id, DiscountRequest request) {
        logger.info("Updating discount with id: " + id);

        // Validation
        request.validate();

        Discount existingDiscount = discountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Discount not found with id: " + id));

        // ✅ YENİ - Değişiklik takibi için önceki halini kaydet
        Discount previousDiscount = createDiscountCopy(existingDiscount);
        boolean isActivationChanged = !Objects.equals(existingDiscount.getIsActive(), request.isActive());
        boolean isDateChanged = !Objects.equals(existingDiscount.getStartDate(), request.startDate()) ||
                !Objects.equals(existingDiscount.getEndDate(), request.endDate());

        // Temel bilgileri güncelle
        existingDiscount.setName(request.name());
        existingDiscount.setDescription(request.description());
        existingDiscount.setDiscountType(DiscountType.valueOf(request.discountType()));
        existingDiscount.setDiscountValue(request.discountValue());
        existingDiscount.setStartDate(request.startDate());
        existingDiscount.setEndDate(request.endDate());
        existingDiscount.setMinimumOrderAmount(request.minimumOrderAmount());
        existingDiscount.setMaximumDiscountAmount(request.maximumDiscountAmount());

        // Usage limit güncellemeleri
        existingDiscount.setUsageLimit(request.usageLimit());
        existingDiscount.setUsageLimitPerCustomer(request.usageLimitPerCustomer());

        if (request.isActive() != null) {
            existingDiscount.setIsActive(request.isActive());
        }

        // İlişkili ürünleri güncelle
        if (request.productIds() != null) {
            if (request.productIds().isEmpty()) {
                existingDiscount.setApplicableProducts(new HashSet<>());
            } else {
                Set<Product> products = validateAndGetProducts(request.productIds());
                existingDiscount.setApplicableProducts(products);
            }
        }

        // İlişkili bayileri güncelle
        if (request.dealerIds() != null) {
            if (request.dealerIds().isEmpty()) {
                existingDiscount.setApplicableDealers(new HashSet<>());
            } else {
                Set<Dealer> dealers = validateAndGetDealers(request.dealerIds());
                existingDiscount.setApplicableDealers(dealers);
            }
        }

        // Çakışan indirimleri kontrol et (mevcut indirim hariç)
        validateDiscountConflicts(existingDiscount, id);

        Discount updatedDiscount = discountRepository.save(existingDiscount);

        // ✅ YENİ - Update event publish et (sadece önemli değişikliklerde)
        try {
            if (isActivationChanged || isDateChanged) {
                DiscountUpdatedEvent event = new DiscountUpdatedEvent(
                        updatedDiscount,
                        previousDiscount,
                        isActivationChanged,
                        isDateChanged
                );
                eventPublisher.publishEvent(event);
                logger.info("Discount updated event published for: " + updatedDiscount.getName());
            }
        } catch (Exception e) {
            logger.warning("Failed to publish discount updated event: " + e.getMessage());
        }

        logger.info("Discount updated successfully with new usage limits: " +
                updatedDiscount.getUsageLimit() + "/" + updatedDiscount.getUsageLimitPerCustomer());

        return discountMapper.toDto(updatedDiscount);
    }

    /**
     * Helper method - Discount kopyalama (değişiklik takibi için)
     */
    private Discount createDiscountCopy(Discount original) {
        Discount copy = new Discount();
        copy.setId(original.getId());
        copy.setName(original.getName());
        copy.setDescription(original.getDescription());
        copy.setDiscountType(original.getDiscountType());
        copy.setDiscountValue(original.getDiscountValue());
        copy.setStartDate(original.getStartDate());
        copy.setEndDate(original.getEndDate());
        copy.setIsActive(original.getIsActive());
        copy.setMinimumOrderAmount(original.getMinimumOrderAmount());
        copy.setMaximumDiscountAmount(original.getMaximumDiscountAmount());
        copy.setUsageLimit(original.getUsageLimit());
        copy.setUsageLimitPerCustomer(original.getUsageLimitPerCustomer());
        // İlişkiler kopyalanmaz, sadece temel alanlar
        return copy;
    }

    @Transactional
    public void deleteDiscount(Long id) {
        logger.info("Deleting discount with id: " + id);

        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Discount not found with id: " + id));

        // Aktif siparişlerde kullanılıp kullanılmadığını kontrol et
        if (orderRepository.countByAppliedDiscountIdAndOrderStatusIn(id, VALID_USAGE_STATUSES) > 0) {
            throw new BadCredentialsException("Cannot delete discount that is being used in active orders");
        }

        discount.setStatus(EntityStatus.DELETED);
        discountRepository.save(discount);

        logger.info("Discount deleted successfully");
    }

    @Transactional
    public DiscountResponse restoreDiscount(Long id) {
        logger.info("Restoring discount with id: " + id);

        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Discount not found with id: " + id));

        discount.setStatus(EntityStatus.ACTIVE);
        Discount restoredDiscount = discountRepository.save(discount);

        return discountMapper.toDto(restoredDiscount);
    }

    // ==================== İNDİRİM HESAPLAMA İŞLEMLERİ ====================

    public DiscountCalculationResponse calculateDiscount(DiscountCalculationRequest request) {
        logger.info("Calculating discount for product: " + request.productId() +
                ", dealer: " + request.dealerId());

        // Facade Pattern - Varlık kontrolleri
        var product = productService.getProductSummary(request.productId());
        var dealer = dealerService.getDealerById(request.dealerId());

        // Toplam tutarı hesapla
        BigDecimal totalAmount = request.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));

        // Uygulanabilir indirimleri bul
        List<Discount> availableDiscounts = getAvailableDiscounts(request);

        // Her indirim için hesaplama yap
        List<ApplicableDiscountInfo> applicableDiscounts = availableDiscounts.stream()
                .map(discount -> calculateDiscountInfo(discount, request, totalAmount))
                .filter(ApplicableDiscountInfo::isApplicable)
                .sorted((d1, d2) -> d2.calculatedDiscountAmount().compareTo(d1.calculatedDiscountAmount()))
                .collect(Collectors.toList());

        // En iyi indirimi seç
        ApplicableDiscountInfo bestDiscount = applicableDiscounts.isEmpty() ?
                null : applicableDiscounts.get(0);

        BigDecimal totalDiscountAmount = bestDiscount != null ?
                bestDiscount.calculatedDiscountAmount() : BigDecimal.ZERO;

        BigDecimal finalAmount = totalAmount.subtract(totalDiscountAmount);
        BigDecimal discountPercentage = totalAmount.compareTo(BigDecimal.ZERO) > 0 ?
                totalDiscountAmount.divide(totalAmount, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;

        return new DiscountCalculationResponse(
                request.productId(),
                product.name(),
                request.dealerId(),
                dealer.name(),
                request.unitPrice(),
                request.quantity(),
                totalAmount,
                applicableDiscounts,
                bestDiscount,
                totalDiscountAmount,
                finalAmount,
                discountPercentage,
                totalDiscountAmount
        );
    }

    // ==================== YENİ USAGE TRACKING METODLARI ====================

    /**
     * İndirim kullanımını kaydet
     */
    @Transactional
    public void recordDiscountUsage(Discount discount, AppUser user, Dealer dealer, Order order,
                                    BigDecimal discountAmount) {
        logger.info("Recording discount usage: " + discount.getName() +
                " for user: " + user.getId() + ", order: " + order.getOrderNumber());

        // DiscountUsage kaydı oluştur
        DiscountUsage discountUsage = new DiscountUsage(
                discount, user, dealer, order, discountAmount,
                order.getTotalAmount(), order.getOrderStatus()
        );

        discountUsageRepository.save(discountUsage);

        // Discount'ın usage count'ını artır
        discount.incrementUsageCount();
        discountRepository.save(discount);

        logger.info("Discount usage recorded successfully. New usage count: " + discount.getUsageCount());
    }

    /**
     * Sipariş iptal/red durumunda usage kaydını sil
     */
    @Transactional
    public void removeDiscountUsage(Long orderId) {
        logger.info("Removing discount usage for order: " + orderId);

        Optional<DiscountUsage> usageOpt = discountUsageRepository.findByOrderId(orderId);
        if (usageOpt.isPresent()) {
            DiscountUsage usage = usageOpt.get();
            Discount discount = usage.getDiscount();

            // Usage count'ı azalt
            if (discount.getUsageCount() != null && discount.getUsageCount() > 0) {
                discount.setUsageCount(discount.getUsageCount() - 1);
                discountRepository.save(discount);
            }

            // Usage kaydını sil
            discountUsageRepository.delete(usage);

            logger.info("Discount usage removed successfully. New usage count: " + discount.getUsageCount());
        }
    }

    /**
     * Kullanıcının belirli indirimi kullanıp kullanmadığını kontrol et
     */
    public boolean hasUserUsedDiscount(Long discountId, Long userId) {
        Long usageCount = discountUsageRepository.countByDiscountAndUser(discountId, userId, VALID_USAGE_STATUSES);
        return usageCount > 0;
    }

    /**
     * Bayinin belirli indirimi kullanıp kullanmadığını kontrol et
     */
    public Long hasDealerUsedDiscount(Long discountId, Long dealerId) {
        Long usageCount = discountUsageRepository.countByDiscountAndDealer(discountId, dealerId, VALID_USAGE_STATUSES);
        return usageCount ;
    }

    /**
     * Kullanıcının belirli indirimi kullanma sayısını getir
     */
    public Long getUserDiscountUsageCount(Long discountId, Long userId) {
        return discountUsageRepository.countByDiscountAndUser(discountId, userId, VALID_USAGE_STATUSES);
    }

    /**
     * Bayinin belirli indirimi kullanma sayısını getir
     */
    public Long getDealerDiscountUsageCount(Long discountId, Long dealerId) {
        return discountUsageRepository.countByDiscountAndDealer(discountId, dealerId, VALID_USAGE_STATUSES);
    }

    /**
     * İndirim kullanılabilir mi kontrol et (tüm kısıtlamalar dahil)
     */
    public boolean canUseDiscount(Long discountId, Long userId, Long dealerId) {
        logger.info("Checking if discount " + discountId + " can be used by user: " + userId + ", dealer: " + dealerId);

        try {
            Discount discount = getDiscountEntityById(discountId);

            // 1. İndirim aktif ve geçerli mi?
            if (!discount.isValidNow()) {
                logger.info("Discount is not valid now");
                return false;
            }

            // 2. Toplam kullanım limiti kontrolü
            if (discount.getUsageLimit() != null) {
                Long totalUsage = discountUsageRepository.countByDiscount(discountId, VALID_USAGE_STATUSES);
                if (totalUsage >= discount.getUsageLimit()) {
                    logger.info("Discount usage limit reached: " + totalUsage + "/" + discount.getUsageLimit());
                    return false;
                }
            }

            // 3. Kullanıcı bazlı limit kontrolü
            if (discount.getUsageLimitPerCustomer() != null && userId != null) {
                Long userUsage = getUserDiscountUsageCount(discountId, userId);
                if (userUsage >= discount.getUsageLimitPerCustomer()) {
                    logger.info("User usage limit reached: " + userUsage + "/" + discount.getUsageLimitPerCustomer());
                    return false;
                }
            }

            // 4. Bayi bazlı limit kontrolü (eğer bayi bazlı kısıtlama varsa)
            // Bu business logic'e göre ayarlanabilir

            logger.info("Discount can be used");
            return true;

        } catch (Exception e) {
            logger.severe("Error checking discount availability: " + e.getMessage());
            return false;
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private Set<Product> validateAndGetProducts(List<Long> productIds) {
        Set<Product> products = new HashSet<>();
        for (Long productId : productIds) {
            // Facade Pattern - Product varlık kontrolü
            productService.getProductSummary(productId);
            Product product = new Product();
            product.setId(productId);
            products.add(product);
        }
        return products;
    }

    private Set<Dealer> validateAndGetDealers(List<Long> dealerIds) {
        Set<Dealer> dealers = new HashSet<>();
        for (Long dealerId : dealerIds) {
            // Facade Pattern - Dealer varlık kontrolü
            dealerService.getDealerById(dealerId);
            Dealer dealer = new Dealer();
            dealer.setId(dealerId);
            dealers.add(dealer);
        }
        return dealers;
    }

    private void validateDiscountConflicts(Discount discount) {
        validateDiscountConflicts(discount, null);
    }

    private void validateDiscountConflicts(Discount discount, Long excludeDiscountId) {
        // Aynı ürün ve bayi için çakışan aktif indirimleri kontrol et
        List<Discount> conflictingDiscounts = discountRepository.findConflictingDiscounts(
                discount.getStartDate(),
                discount.getEndDate(),
                EntityStatus.ACTIVE,
                excludeDiscountId
        );

        for (Discount conflicting : conflictingDiscounts) {
            // Ürün çakışması kontrolü
            boolean productConflict = discount.getApplicableProducts().stream()
                    .anyMatch(product -> conflicting.getApplicableProducts().contains(product));

            // Bayi çakışması kontrolü
            boolean dealerConflict = discount.getApplicableDealers().stream()
                    .anyMatch(dealer -> conflicting.getApplicableDealers().contains(dealer));

            if (productConflict && dealerConflict) {
                throw new BadCredentialsException(
                        "Discount conflicts with existing discount: " + conflicting.getName() +
                                " for the same product and dealer in overlapping time period");
            }
        }
    }

    private List<Discount> getAvailableDiscounts(DiscountCalculationRequest request) {
        List<Discount> discounts = discountRepository.findValidDiscountsForProductAndDealer(
                request.productId(), request.dealerId(), EntityStatus.ACTIVE);

        // ✅ YENİ - Usage limit kontrolü ekle
        discounts = discounts.stream()
                .filter(discount -> {
                    // Toplam limit kontrolü
                    if (discount.getUsageLimit() != null) {
                        Long totalUsage = discountUsageRepository.countByDiscount(discount.getId(), VALID_USAGE_STATUSES);
                        if (totalUsage >= discount.getUsageLimit()) {
                            logger.info("Filtering out discount " + discount.getName() + " - usage limit reached");
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // Include/exclude filtrelerini uygula
        if (request.includeDiscountIds() != null && !request.includeDiscountIds().isEmpty()) {
            discounts = discounts.stream()
                    .filter(d -> request.includeDiscountIds().contains(d.getId()))
                    .collect(Collectors.toList());
        }

        if (request.excludeDiscountIds() != null && !request.excludeDiscountIds().isEmpty()) {
            discounts = discounts.stream()
                    .filter(d -> !request.excludeDiscountIds().contains(d.getId()))
                    .collect(Collectors.toList());
        }

        return discounts;
    }

    private ApplicableDiscountInfo calculateDiscountInfo(Discount discount,
                                                         DiscountCalculationRequest request,
                                                         BigDecimal totalAmount) {
        // Minimum sipariş tutarı kontrolü
        boolean minimumOrderMet = discount.getMinimumOrderAmount() == null ||
                (request.totalOrderAmount() != null &&
                        request.totalOrderAmount().compareTo(discount.getMinimumOrderAmount()) >= 0) ||
                totalAmount.compareTo(discount.getMinimumOrderAmount()) >= 0;

        if (!minimumOrderMet) {
            return new ApplicableDiscountInfo(
                    discount.getId(),
                    discount.getName(),
                    discount.getDiscountType(),
                    discount.getDiscountValue(),
                    BigDecimal.ZERO,
                    request.unitPrice(),
                    false,
                    false,
                    false
            );
        }

        // İndirim tutarını hesapla
        BigDecimal discountAmount;
        if (discount.getDiscountType() == DiscountType.PERCENTAGE) {
            discountAmount = totalAmount.multiply(discount.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discountAmount = discount.getDiscountValue().multiply(BigDecimal.valueOf(request.quantity()));
        }

        // Maksimum indirim limiti kontrolü
        boolean maximumDiscountApplied = false;
        if (discount.getMaximumDiscountAmount() != null &&
                discountAmount.compareTo(discount.getMaximumDiscountAmount()) > 0) {
            discountAmount = discount.getMaximumDiscountAmount();
            maximumDiscountApplied = true;
        }

        // İndirimli birim fiyatı hesapla
        BigDecimal discountedUnitPrice = request.unitPrice().subtract(
                discountAmount.divide(BigDecimal.valueOf(request.quantity()), 2, RoundingMode.HALF_UP)
        );

        return new ApplicableDiscountInfo(
                discount.getId(),
                discount.getName(),
                discount.getDiscountType(),
                discount.getDiscountValue(),
                discountAmount,
                discountedUnitPrice,
                minimumOrderMet,
                maximumDiscountApplied,
                true
        );
    }

    // ==================== İSTATİSTİK VE YARDIMCI METODLAR ====================

    public Long getActiveDiscountCount() {
        return discountRepository.countActiveDiscounts(EntityStatus.ACTIVE);
    }

    public Long getDiscountCountForProduct(Long productId) {
        productService.getProductSummary(productId); // Varlık kontrolü
        return discountRepository.countDiscountsForProduct(productId, EntityStatus.ACTIVE);
    }

    public Long getDiscountCountForDealer(Long dealerId) {
        dealerService.getDealerById(dealerId); // Varlık kontrolü
        return discountRepository.countDiscountsForDealer(dealerId, EntityStatus.ACTIVE);
    }

    public List<DiscountSummary> getDiscountSummaries() {
        logger.info("Fetching discount summaries");
        List<Discount> discounts = discountRepository.findActiveDiscounts(EntityStatus.ACTIVE);
        return discounts.stream()
                .map(discountMapper::toSummary)
                .collect(Collectors.toList());
    }

    // ✅ YENİ - İndirim kullanım istatistikleri
    public Map<String, Object> getDiscountUsageStatistics(Long discountId) {
        logger.info("Fetching usage statistics for discount: " + discountId);

        Discount discount = getDiscountEntityById(discountId);
        Long totalUsage = discountUsageRepository.countByDiscount(discountId, VALID_USAGE_STATUSES);

        Map<String, Object> stats = new HashMap<>();
        stats.put("discountName", discount.getName());
        stats.put("totalUsage", totalUsage);
        stats.put("usageLimit", discount.getUsageLimit());
        stats.put("remainingUsage", discount.getUsageLimit() != null ?
                Math.max(0, discount.getUsageLimit() - totalUsage.intValue()) : null);
        stats.put("usagePercentage", discount.getUsageLimit() != null ?
                (totalUsage.doubleValue() / discount.getUsageLimit() * 100) : null);
        stats.put("usageLimitPerCustomer", discount.getUsageLimitPerCustomer());

        return stats;
    }
}
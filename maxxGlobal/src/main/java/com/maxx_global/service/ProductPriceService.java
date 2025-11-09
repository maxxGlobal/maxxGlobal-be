// ProductPriceService.java - Tam güncellenmiş servis

package com.maxx_global.service;

import com.maxx_global.dto.dealer.DealerResponse;
import com.maxx_global.dto.product.ProductSummary;
import com.maxx_global.dto.productPrice.*;
import com.maxx_global.entity.ProductPrice;
import com.maxx_global.entity.ProductVariant;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.ProductPriceRepository;
import com.maxx_global.repository.ProductVariantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductPriceService {

    private static final Logger logger = Logger.getLogger(ProductPriceService.class.getName());

    private final ProductPriceRepository productPriceRepository;
    private final ProductPriceMapper productPriceMapper;
    private final ProductService productService;
    private final DealerService dealerService;
    private final ProductVariantRepository productVariantRepository;

    public ProductPriceService(ProductPriceRepository productPriceRepository,
                               ProductPriceMapper productPriceMapper,
                               ProductService productService,
                               DealerService dealerService,
                               ProductVariantRepository productVariantRepository) {
        this.productPriceRepository = productPriceRepository;
        this.productPriceMapper = productPriceMapper;
        this.productService = productService;
        this.dealerService = dealerService;
        this.productVariantRepository = productVariantRepository;
    }

    // ==================== YENİ - GRUPLU FİYAT İŞLEMLERİ ====================

    /**
     * Tüm fiyatları gruplu olarak getir (ürün-dealer kombinasyonu bazında)
     */
    public Page<ProductPriceResponse> getAllPrices(int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching all prices grouped by product-dealer - page: " + page + ", size: " + size);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);

        // Optimize edilmiş query ile gruplu veri al
        List<ProductPrice> allPrices = productPriceRepository.findAllGroupedByProductDealer(EntityStatus.ACTIVE);

        // Ürün-Dealer kombinasyonuna göre grupla
        Map<String, List<ProductPrice>> groupedPrices = allPrices.stream()
                .collect(Collectors.groupingBy(price ->
                                PriceGroup.createGroupKey(
                                        price.getProductVariant() != null ? price.getProductVariant().getProduct().getId() : null,
                                        price.getDealer().getId()
                                ),
                        LinkedHashMap::new, // Sıralamayı koru
                        Collectors.toList()
                ));

        // PriceGroup'ları oluştur
        List<PriceGroup> priceGroups = groupedPrices.entrySet().stream()
                .map(entry -> {
                    List<ProductPrice> prices = entry.getValue();
                    ProductPrice firstPrice = prices.get(0);
                    return new PriceGroup(
                            firstPrice.getProductVariant() != null ? firstPrice.getProductVariant().getProduct().getId() : null,
                            firstPrice.getProductVariant() != null ? firstPrice.getProductVariant().getProduct().getName() : null,
                            firstPrice.getProductVariant() != null ? firstPrice.getProductVariant().getProduct().getCode() : null,
                            firstPrice.getDealer().getId(),
                            firstPrice.getDealer().getName(),
                            prices
                    );
                })
                .sorted(createPriceGroupComparator(sortBy, sortDirection))
                .collect(Collectors.toList());

        // Sayfalama uygula
        Pageable pageable = PageRequest.of(page, size);
        return createPagedResponse(priceGroups, pageable);
    }

    /**
     * Bayiye göre gruplu fiyatlar
     */
    public Page<ProductPriceResponse> getPricesByDealer(Long dealerId, int page, int size,
                                                        String sortBy, String sortDirection, boolean activeOnly) {
        logger.info("Fetching grouped prices for dealer: " + dealerId + ", activeOnly: " + activeOnly);

        // Dealer varlık kontrolü
        dealerService.getDealerById(dealerId);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        List<ProductPrice> prices;
        if (activeOnly) {
            // Aktif ve geçerli fiyatları al
            Page<ProductPrice> activePage = productPriceRepository.findActivePricesByDealer(
                    dealerId, EntityStatus.ACTIVE, PageRequest.of(0, Integer.MAX_VALUE));
            prices = activePage.getContent();
        } else {
            // Tüm dealer fiyatlarını al
            Page<ProductPrice> allPage = productPriceRepository.findByDealerIdAndStatus(
                    dealerId, EntityStatus.ACTIVE, PageRequest.of(0, Integer.MAX_VALUE));
            prices = allPage.getContent();
        }

        // Ürün bazında grupla
        Map<Long, List<ProductPrice>> groupedByProduct = prices.stream()
                .collect(Collectors.groupingBy(price ->
                        price.getProductVariant() != null ? price.getProductVariant().getProduct().getId() : null
                ));

        // PriceGroup'ları oluştur
        List<PriceGroup> priceGroups = groupedByProduct.entrySet().stream()
                .map(entry -> {
                    List<ProductPrice> productPrices = entry.getValue();
                    ProductPrice firstPrice = productPrices.get(0);
                    return new PriceGroup(
                            firstPrice.getProductVariant() != null ? firstPrice.getProductVariant().getProduct().getId() : null,
                            firstPrice.getProductVariant() != null ? firstPrice.getProductVariant().getProduct().getName() : null,
                            firstPrice.getProductVariant() != null ? firstPrice.getProductVariant().getProduct().getCode() : null,
                            firstPrice.getDealer().getId(),
                            firstPrice.getDealer().getName(),
                            productPrices
                    );
                })
                .sorted(createPriceGroupComparator(sortBy, sortDirection))
                .collect(Collectors.toList());

        return createPagedResponse(priceGroups, pageable);
    }

    /**
     * ID ile fiyat getir - Tek fiyat döner (gruplu değil)
     */
    public ProductPriceResponse getPriceById(Long id) {
        logger.info("Fetching single price with id: " + id);

        ProductPrice price = productPriceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Price not found with id: " + id));

        return productPriceMapper.toResponseSingle(price);
    }

    /**
     * Ürün bazlı fiyat getir (belirli bayi için - gruplu)
     */
    public ProductPriceResponse getProductPricesForDealer(Long productId, Long dealerId) {
        logger.info("Getting grouped prices for product: " + productId + ", dealer: " + dealerId);

        // Varlık kontrolleri
        productService.getProductSummary(productId);
         dealerService.getDealerById(dealerId);

        // Bu ürün-dealer kombinasyonu için tüm currency'lerdeki fiyatları al
        List<ProductPrice> prices = productPriceRepository.findByProductIdAndDealerIdAndStatus(
                productId, dealerId, EntityStatus.ACTIVE);

        ProductPrice pp= prices.stream().filter(p->p.getDealer().getPreferredCurrency().equals(p.getCurrency())).findFirst().orElse(null);

        return productPriceMapper.toResponseSingle(pp);
    }

    public DealerProductVariantPricesResponse getDealerProductVariantPrices(Long productId, Long dealerId) {
        logger.info("Getting variant price list for product: " + productId + ", dealer: " + dealerId);

        ProductSummary product = productService.getProductSummary(productId);
        DealerResponse dealer = dealerService.getDealerById(dealerId);

        List<ProductVariant> variants = productVariantRepository
                .findByProductIdAndStatusOrderBySizeAsc(productId, EntityStatus.ACTIVE);

        List<ProductPrice> prices = productPriceRepository.findByProductIdAndDealerIdAndStatus(
                productId, dealerId, EntityStatus.ACTIVE);

        Map<Long, ProductVariant> variantInfo = new LinkedHashMap<>();
        variants.forEach(variant -> variantInfo.put(variant.getId(), variant));

        Map<Long, Map<CurrencyType, BigDecimal>> priceMap = new LinkedHashMap<>();

        for (ProductPrice price : prices) {
            if (price == null || price.getProductVariant() == null || price.getCurrency() == null) {
                continue;
            }

            ProductVariant variant = price.getProductVariant();
            variantInfo.putIfAbsent(variant.getId(), variant);

            priceMap.computeIfAbsent(variant.getId(), id -> new EnumMap<>(CurrencyType.class))
                    .put(price.getCurrency(), price.getAmount());
        }

        List<VariantPriceDetail> variantDetails = variantInfo.values().stream()
                .map(variant -> {
                    Map<CurrencyType, BigDecimal> amountsByCurrency = priceMap.getOrDefault(variant.getId(), Collections.emptyMap());

                    List<PriceInfo> priceInfos = Arrays.stream(CurrencyType.values())
                            .map(currency -> new PriceInfo(currency, amountsByCurrency.get(currency)))
                            .collect(Collectors.toList());

                    return new VariantPriceDetail(
                            variant.getId(),
                            variant.getSku(),
                            variant.getSize(),
                            priceInfos
                    );
                })
                .collect(Collectors.toList());

        return new DealerProductVariantPricesResponse(
                product.id(),
                product.name(),
                product.code(),
                dealer.id(),
                dealer.name(),
                variantDetails
        );
    }

    public ProductPriceResponse getVariantPricesForDealer(Long variantId, Long dealerId) {
        logger.info("Getting grouped prices for product: " + variantId + ", dealer: " + dealerId);

        // Varlık kontrolleri
        productService.getVariant(variantId);
        dealerService.getDealerById(dealerId);

        // Bu ürün-dealer kombinasyonu için tüm currency'lerdeki fiyatları al
        List<ProductPrice> prices = productPriceRepository.findByProductVariantAndDealerIdAndStatus(
                variantId, dealerId, EntityStatus.ACTIVE);

        ProductPrice pp= prices.stream().filter(p->p.getDealer().getPreferredCurrency().equals(p.getCurrency())).findFirst().orElse(null);

        return productPriceMapper.toResponseSingle(pp);
    }

    // ==================== ARAMA İŞLEMLERİ ====================

    /**
     * Fiyat arama - Gruplu sonuçlar
     */
    public Page<ProductPriceResponse> searchPrices(Long dealerId, String searchTerm, int page, int size) {
        logger.info("Searching grouped prices for dealer: " + dealerId + ", term: " + searchTerm);

        // Dealer varlık kontrolü
        dealerService.getDealerById(dealerId);

        Pageable pageable = PageRequest.of(page, size);
        Page<ProductPrice> pricesPage = productPriceRepository.searchPricesByProductInfo(
                dealerId, searchTerm, EntityStatus.ACTIVE,
                PageRequest.of(0, Integer.MAX_VALUE)); // Tüm sonuçları al

        // Ürün bazında grupla
        Map<Long, List<ProductPrice>> groupedByProduct = pricesPage.getContent().stream()
                .collect(Collectors.groupingBy(price ->
                        price.getProductVariant() != null ? price.getProductVariant().getProduct().getId() : null
                ));

        List<PriceGroup> priceGroups = groupedByProduct.entrySet().stream()
                .map(entry -> {
                    List<ProductPrice> productPrices = entry.getValue();
                    ProductPrice firstPrice = productPrices.get(0);
                    return new PriceGroup(
                            firstPrice.getProductVariant() != null ? firstPrice.getProductVariant().getProduct().getId() : null,
                            firstPrice.getProductVariant() != null ? firstPrice.getProductVariant().getProduct().getName() : null,
                            firstPrice.getProductVariant() != null ? firstPrice.getProductVariant().getProduct().getCode() : null,
                            firstPrice.getDealer().getId(),
                            firstPrice.getDealer().getName(),
                            productPrices
                    );
                })
                .collect(Collectors.toList());

        return createPagedResponse(priceGroups, pageable);
    }

    // ==================== KARŞILAŞTIRMA İŞLEMLERİ ====================

    /**
     * Ürüne göre bayiler arası fiyat karşılaştırması
     */
    public DealerPriceComparison getProductPriceComparison(Long productId, String currency) {
        logger.info("Getting price comparison for product: " + productId);

        ProductSummary product = productService.getProductSummary(productId);

        List<ProductPrice> prices = productPriceRepository.findPricesForComparison(
                productId, CurrencyType.valueOf(currency), EntityStatus.ACTIVE);

        if (prices.isEmpty()) {
            throw new EntityNotFoundException("No prices found for product: " + productId);
        }

        List<DealerPriceInfo> dealerPrices = prices.stream()
                .map(price -> new DealerPriceInfo(
                        price.getDealer().getId(),
                        price.getDealer().getName(),
                        price.getAmount(),
                        price.isValidNow()
                ))
                .collect(Collectors.toList());

        return new DealerPriceComparison(
                productId,
                product.name(),
                CurrencyType.valueOf(currency),
                dealerPrices
        );
    }

    // ==================== CRUD İŞLEMLERİ ====================

    /**
     * Yeni fiyat oluştur
     */
    @Transactional
    public ProductPriceResponse createPrice(ProductPriceRequest request) {
        logger.info("Creating new price for product: " + request.productId() +
                ", dealer: " + request.dealerId() + ", currency: " + request.currency());

        // Validation
        request.validate();

        // Varlık kontrolleri
        ProductSummary product = productService.getProductSummary(request.productId());
        dealerService.getDealerById(request.dealerId());

        // Unique constraint kontrolü (aynı ürün-dealer-currency kombinasyonu)
        productPriceRepository.findByProductIdAndDealerIdAndCurrency(
                request.productId(), request.dealerId(), request.currency()
        ).ifPresent(existingPrice -> {
            throw new BadCredentialsException(
                    "Price already exists for product: " + product.name() +
                            ", dealer: " + request.dealerId() +
                            ", currency: " + request.currency());
        });

        ProductPrice price = productPriceMapper.toEntity(request);
        price.setStatus(EntityStatus.ACTIVE);

        if (price.getIsActive() == null) {
            price.setIsActive(true);
        }

        ProductPrice savedPrice = productPriceRepository.save(price);
        logger.info("Price created successfully with id: " + savedPrice.getId());

        // Tek fiyat response döner
        return productPriceMapper.toResponseSingle(savedPrice);
    }

    /**
     * Fiyat güncelle
     */
    @Transactional
    public ProductPriceResponse updatePrice(Long id, ProductPriceRequest request) {
        logger.info("Updating price with id: " + id);

        request.validate();

        ProductPrice existingPrice = productPriceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Price not found with id: " + id));

        // Farklı kombinasyon için unique constraint kontrolü
        Long existingProductId = existingPrice.getProductVariant() != null ?
                existingPrice.getProductVariant().getProduct().getId() : null;

        if ((existingProductId == null || !existingProductId.equals(request.productId())) ||
                !existingPrice.getDealer().getId().equals(request.dealerId()) ||
                !existingPrice.getCurrency().equals(request.currency()))  {

            productPriceRepository.findByProductIdAndDealerIdAndCurrency(
                    request.productId(), request.dealerId(), request.currency()
            ).ifPresent(conflictPrice -> {
                throw new BadCredentialsException("Price already exists for this new combination");
            });

            productService.getProductSummary(request.productId());
            dealerService.getDealerById(request.dealerId());
        }

        // Güncelleme
        existingPrice.setAmount(request.amount());
        existingPrice.setValidFrom(request.validFrom());
        existingPrice.setValidUntil(request.validUntil());
        existingPrice.setIsActive(request.isActive() != null ? request.isActive() : true);

        ProductPrice updatedPrice = productPriceRepository.save(existingPrice);
        logger.info("Price updated successfully");

        return productPriceMapper.toResponseSingle(updatedPrice);
    }

    /**
     * Toplu fiyat güncelleme
     */
    @Transactional
    public List<ProductPriceResponse> bulkUpdatePrices(BulkPriceUpdateRequest request) {
        logger.info("Bulk updating " + request.priceIds().size() + " prices");

        request.validate();

        List<ProductPrice> prices = productPriceRepository.findByIdsAndStatus(
                request.priceIds(), EntityStatus.ACTIVE);

        if (prices.size() != request.priceIds().size()) {
            throw new EntityNotFoundException("Some prices not found or not active");
        }

        prices.forEach(price -> updatePriceFromBulkRequest(price, request));
        List<ProductPrice> updatedPrices = productPriceRepository.saveAll(prices);

        // Tek fiyat response'ları döner
        return updatedPrices.stream()
                .map(productPriceMapper::toResponseSingle)
                .collect(Collectors.toList());
    }

    /**
     * Fiyat sil (soft delete)
     */
    @Transactional
    public void deletePrice(Long id) {
        logger.info("Deleting price with id: " + id);

        ProductPrice price = productPriceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Price not found with id: " + id));

        price.setStatus(EntityStatus.DELETED);
        productPriceRepository.save(price);

        logger.info("Price deleted successfully");
    }

    // ==================== BUSINESS LOGIC İŞLEMLERİ ====================

    /**
     * Süresi dolan fiyatları getir - Gruplu değil, tek tek
     */
    public List<ProductPriceResponse> getExpiredPrices() {
        logger.info("Fetching expired prices");

        List<ProductPrice> expiredPrices = productPriceRepository.findExpiredPrices(EntityStatus.ACTIVE);
        return expiredPrices.stream()
                .map(productPriceMapper::toResponseSingle)
                .collect(Collectors.toList());
    }

    /**
     * Aktif fiyat sayısı (istatistik için)
     */
    public Long getActivePriceCountByDealer(Long dealerId) {
        dealerService.getDealerById(dealerId);
        return productPriceRepository.countActivePricesByDealer(dealerId, EntityStatus.ACTIVE);
    }

    /**
     * Ürünün kaç bayide fiyatı var
     */
    public Long getDealerCountForProduct(Long productId) {
        productService.getProductSummary(productId);
        return productPriceRepository.countDealersWithPriceForProduct(productId, EntityStatus.ACTIVE);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * PriceGroup comparator oluştur
     */
    private Comparator<PriceGroup> createPriceGroupComparator(String sortBy, String sortDirection) {
        boolean isDesc = "desc".equalsIgnoreCase(sortDirection);

        return switch (sortBy.toLowerCase()) {
            case "name" -> isDesc ?
                    Comparator.comparing(PriceGroup::productName).reversed() :
                    Comparator.comparing(PriceGroup::productName);

            case "amount" -> isDesc ?
                    Comparator.comparing((PriceGroup g) -> g.getMainPrice().getAmount()).reversed() :
                    Comparator.comparing((PriceGroup g) -> g.getMainPrice().getAmount());

            case "dealer" -> isDesc ?
                    Comparator.comparing(PriceGroup::dealerName).reversed() :
                    Comparator.comparing(PriceGroup::dealerName);

            default -> isDesc ? // createdDate
                    Comparator.comparing((PriceGroup g) -> g.getMainPrice().getCreatedAt()).reversed() :
                    Comparator.comparing((PriceGroup g) -> g.getMainPrice().getCreatedAt());
        };
    }

    /**
     * Sayfalanmış response oluştur
     */
    private Page<ProductPriceResponse> createPagedResponse(List<PriceGroup> priceGroups, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), priceGroups.size());

        if (start >= priceGroups.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, priceGroups.size());
        }

        List<PriceGroup> pagedGroups = priceGroups.subList(start, end);
        List<ProductPriceResponse> responses = pagedGroups.stream()
                .map(productPriceMapper::toResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, priceGroups.size());
    }

    /**
     * Bulk update için tek bir fiyatı güncelle
     */
    private void updatePriceFromBulkRequest(ProductPrice price, BulkPriceUpdateRequest request) {
        if (request.newAmount() != null) {
            price.setAmount(request.newAmount());
        }

        if (request.percentageChange() != null) {
            BigDecimal currentAmount = price.getAmount();
            BigDecimal multiplier = BigDecimal.ONE.add(
                    request.percentageChange().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
            );
            BigDecimal newAmount = currentAmount.multiply(multiplier)
                    .setScale(2, RoundingMode.HALF_UP);
            price.setAmount(newAmount);
        }

        if (request.isActive() != null) {
            price.setIsActive(request.isActive());
        }

        if (request.validFrom() != null) {
            price.setValidFrom(request.validFrom());
        }

        if (request.validUntil() != null) {
            price.setValidUntil(request.validUntil());
        }
    }
}
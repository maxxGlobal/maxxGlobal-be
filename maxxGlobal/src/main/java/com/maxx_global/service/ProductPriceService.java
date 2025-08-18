package com.maxx_global.service;

import com.maxx_global.dto.product.ProductSummary;
import com.maxx_global.dto.productPrice.*;
import com.maxx_global.entity.ProductPrice;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.dto.productPrice.ProductPriceMapper;
import com.maxx_global.repository.ProductPriceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductPriceService {

    private static final Logger logger = Logger.getLogger(ProductPriceService.class.getName());

    private final ProductPriceRepository productPriceRepository;
    private final ProductPriceMapper productPriceMapper;

    // Facade Pattern - Diğer servisleri çağır
    private final ProductService productService;
    private final DealerService dealerService;

    public ProductPriceService(ProductPriceRepository productPriceRepository,
                               ProductPriceMapper productPriceMapper,
                               ProductService productService,
                               DealerService dealerService) {
        this.productPriceRepository = productPriceRepository;
        this.productPriceMapper = productPriceMapper;
        this.productService = productService;
        this.dealerService = dealerService;
    }

    // ==================== READ İŞLEMLERİ ====================

    // Tüm fiyatları getir (sayfalama ile)
    public Page<ProductPriceResponse> getAllPrices(int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching all prices - page: " + page + ", size: " + size);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductPrice> prices = productPriceRepository.findAll(pageable);
        return prices.map(productPriceMapper::toResponse);
    }

    // ID ile fiyat getir
    public ProductPriceResponse getPriceById(Long id) {
        logger.info("Fetching price with id: " + id);

        ProductPrice price = productPriceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Price not found with id: " + id));

        return productPriceMapper.toResponse(price);
    }

    // Bayiye göre fiyatlar
    public Page<ProductPriceResponse> getPricesByDealer(Long dealerId, int page, int size,
                                                        String sortBy, String sortDirection, boolean activeOnly) {
        logger.info("Fetching prices for dealer: " + dealerId + ", activeOnly: " + activeOnly);

        // Facade Pattern - Dealer varlık kontrolü
        dealerService.getDealerById(dealerId);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductPrice> prices;
        if (activeOnly) {
            prices = productPriceRepository.findActivePricesByDealer(dealerId, EntityStatus.ACTIVE, pageable);
        } else {
            prices = productPriceRepository.findByDealerIdAndStatus(dealerId, EntityStatus.ACTIVE, pageable);
        }

        return prices.map(productPriceMapper::toResponse);
    }

    // Ürüne göre bayiler arası fiyat karşılaştırması
    public DealerPriceComparison getProductPriceComparison(Long productId, String currency) {
        logger.info("Getting price comparison for product: " + productId);

        // Facade Pattern - Product varlık kontrolü
        ProductSummary product = productService.getProductSummary(productId);

        List<ProductPrice> prices = productPriceRepository.findPricesForComparison(
                productId,
                CurrencyType.valueOf(currency),
                EntityStatus.ACTIVE
        );

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
                product.name(), // ProductSummary'den al
                CurrencyType.valueOf(currency),
                dealerPrices
        );
    }

    // Ürün bazlı fiyat getir (belirli bayi ve currency için)
    public ProductPriceResponse getProductPrice(Long productId, Long dealerId, String currency) {
        logger.info("Getting price for product: " + productId + ", dealer: " + dealerId);

        // Facade Pattern - Varlık kontrolleri
        productService.getProductSummary(productId);
        dealerService.getDealerById(dealerId);

        ProductPrice price = productPriceRepository.findValidPrice(
                productId, dealerId, CurrencyType.valueOf(currency), EntityStatus.ACTIVE
        ).orElseThrow(() -> new EntityNotFoundException(
                "No valid price found for product: " + productId + ", dealer: " + dealerId));

        return productPriceMapper.toResponse(price);
    }

    // Fiyat arama
    public Page<ProductPriceResponse> searchPrices(Long dealerId, String searchTerm, int page, int size) {
        logger.info("Searching prices for dealer: " + dealerId + ", term: " + searchTerm);

        // Facade Pattern - Dealer varlık kontrolü
        dealerService.getDealerById(dealerId);

        Pageable pageable = PageRequest.of(page, size);
        Page<ProductPrice> prices = productPriceRepository.searchPricesByProductInfo(
                dealerId, searchTerm, EntityStatus.ACTIVE, pageable);

        return prices.map(productPriceMapper::toResponse);
    }

    // ==================== WRITE İŞLEMLERİ ====================

    // Yeni fiyat oluştur
    @Transactional
    public ProductPriceResponse createPrice(ProductPriceRequest request) {
        logger.info("Creating new price for product: " + request.productId() + ", dealer: " + request.dealerId());

        // Validation
        request.validate();

        // Facade Pattern - Varlık kontrolleri
        ProductSummary product = productService.getProductSummary(request.productId());
        dealerService.getDealerById(request.dealerId());

        // Unique constraint kontrolü
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

        // Default değerler
        if (price.getIsActive() == null) {
            price.setIsActive(true);
        }

        ProductPrice savedPrice = productPriceRepository.save(price);
        logger.info("Price created successfully with id: " + savedPrice.getId());

        return productPriceMapper.toResponse(savedPrice);
    }

    // Fiyat güncelle
    @Transactional
    public ProductPriceResponse updatePrice(Long id, ProductPriceRequest request) {
        logger.info("Updating price with id: " + id);

        // Validation
        request.validate();

        ProductPrice existingPrice = productPriceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Price not found with id: " + id));

        // Farklı kombinasyon için unique constraint kontrolü
        if (!existingPrice.getProduct().getId().equals(request.productId()) ||
                !existingPrice.getDealer().getId().equals(request.dealerId()) ||
                !existingPrice.getCurrency().equals(request.currency()))  {

            // Yeni kombinasyon için kontrol
            productPriceRepository.findByProductIdAndDealerIdAndCurrency(
                    request.productId(), request.dealerId(), request.currency()
            ).ifPresent(conflictPrice -> {
                throw new BadCredentialsException("Price already exists for this new combination");
            });

            // Facade Pattern - Yeni product/dealer kontrolleri
            productService.getProductSummary(request.productId());
            dealerService.getDealerById(request.dealerId());
        }

        // Güncelleme işlemi
        existingPrice.setAmount(request.amount());
        existingPrice.setValidFrom(request.validFrom());
        existingPrice.setValidUntil(request.validUntil());
        existingPrice.setIsActive(request.isActive() != null ? request.isActive() : true);

        ProductPrice updatedPrice = productPriceRepository.save(existingPrice);
        logger.info("Price updated successfully");

        return productPriceMapper.toResponse(updatedPrice);
    }

    // Toplu fiyat güncelleme
    @Transactional
    public List<ProductPriceResponse> bulkUpdatePrices(BulkPriceUpdateRequest request) {
        logger.info("Bulk updating " + request.priceIds().size() + " prices");

        // Validation
        request.validate();

        List<ProductPrice> prices = productPriceRepository.findByIdsAndStatus(
                request.priceIds(), EntityStatus.ACTIVE);

        if (prices.size() != request.priceIds().size()) {
            throw new EntityNotFoundException("Some prices not found or not active. Found: " +
                    prices.size() + ", Expected: " + request.priceIds().size());
        }

        prices.forEach(price -> updatePriceFromBulkRequest(price, request));

        List<ProductPrice> updatedPrices = productPriceRepository.saveAll(prices);
        logger.info("Bulk update completed successfully for " + updatedPrices.size() + " prices");

        return updatedPrices.stream()
                .map(productPriceMapper::toResponse)
                .collect(Collectors.toList());
    }

    // Fiyat sil (soft delete)
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

    // Süresi dolan fiyatları getir
    public List<ProductPriceResponse> getExpiredPrices() {
        logger.info("Fetching expired prices");

        List<ProductPrice> expiredPrices = productPriceRepository.findExpiredPrices(EntityStatus.ACTIVE);
        return expiredPrices.stream()
                .map(productPriceMapper::toResponse)
                .collect(Collectors.toList());
    }

    // Aktif fiyat sayısı (istatistik için)
    public Long getActivePriceCountByDealer(Long dealerId) {
        dealerService.getDealerById(dealerId); // Varlık kontrolü
        return productPriceRepository.countActivePricesByDealer(dealerId, EntityStatus.ACTIVE);
    }

    // Ürünün kaç bayide fiyatı var
    public Long getDealerCountForProduct(Long productId) {
        productService.getProductSummary(productId); // Varlık kontrolü
        return productPriceRepository.countDealersWithPriceForProduct(productId, EntityStatus.ACTIVE);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    // Bulk update için tek bir fiyatı güncelle
    private void updatePriceFromBulkRequest(ProductPrice price, BulkPriceUpdateRequest request) {
        // Yeni fiyat miktarı
        if (request.newAmount() != null) {
            price.setAmount(request.newAmount());
        }

        // Yüzde artış/azalış
        if (request.percentageChange() != null) {
            BigDecimal currentAmount = price.getAmount();
            BigDecimal multiplier = BigDecimal.ONE.add(
                    request.percentageChange().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
            );
            BigDecimal newAmount = currentAmount.multiply(multiplier)
                    .setScale(2, RoundingMode.HALF_UP);
            price.setAmount(newAmount);
        }

        // Aktiflik durumu
        if (request.isActive() != null) {
            price.setIsActive(request.isActive());
        }

        // Geçerlilik tarihleri
        if (request.validFrom() != null) {
            price.setValidFrom(request.validFrom());
        }
        if (request.validUntil() != null) {
            price.setValidUntil(request.validUntil());
        }
    }
}
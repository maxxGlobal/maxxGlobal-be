package com.maxx_global.service;

import com.maxx_global.dto.product.*;
import com.maxx_global.dto.productPrice.ProductPriceSummary;
import com.maxx_global.entity.Category;
import com.maxx_global.entity.Product;
import com.maxx_global.entity.ProductPrice;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.ProductPriceRepository;
import com.maxx_global.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final Logger logger = Logger.getLogger(ProductService.class.getName());

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final ProductMapper productMapper;

    // Facade Pattern - Diğer servisleri çağır
    private final CategoryService categoryService;
    private final DealerService dealerService;

    public ProductService(ProductRepository productRepository,
                          ProductPriceRepository productPriceRepository,
                          ProductMapper productMapper,
                          CategoryService categoryService,
                          DealerService dealerService) {
        this.productRepository = productRepository;
        this.productPriceRepository = productPriceRepository;
        this.productMapper = productMapper;
        this.categoryService = categoryService;
        this.dealerService = dealerService;
    }

    // ==================== BASIC PRODUCT OPERATIONS ====================

    // Tüm ürünleri getir (sayfalama ile) - Dealer bilgisi olmadan
    public Page<ProductSummary> getAllProducts(int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching all products - page: " + page + ", size: " + size +
                ", sortBy: " + sortBy + ", direction: " + sortDirection);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE, pageable);
        return products.map(productMapper::toSummary);
    }

    // Aktif ürünleri getir - Summary format
    public List<ProductSummary> getActiveProducts() {
        logger.info("Fetching active products");
        List<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);
        return products.stream()
                .map(productMapper::toSummary)
                .collect(Collectors.toList());
    }

    // ID ile ürün getir - Dealer bilgisi olmadan (detay bilgisi)
    public ProductResponse getProductById(Long id) {
        logger.info("Fetching product with id: " + id);
        Product product = productRepository.findByIdWithImages(id, EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));
        return productMapper.toDto(product);
    }

    // ==================== DEALER-SPECIFIC OPERATIONS ====================

    // Dealer bilgisi ile tüm ürünleri getir (fiyat bilgisi dahil)
    public Page<ProductListItemResponse> getAllProductsWithDealer(ProductWithDealerInfoRequest request,
                                                                  int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching products with dealer info - dealerId: " + request.dealerId());

        // Dealer varlık kontrolü
        dealerService.getDealerById(request.dealerId());

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE, pageable);

        List<ProductListItemResponse> productListItems = products.getContent().stream()
                .map(product -> mapToProductListItem(product, request.dealerId(), request.currency()))
                .collect(Collectors.toList());

        return new PageImpl<>(productListItems, pageable, products.getTotalElements());
    }

    // Dealer bilgisi ile ürün detayı getir (fiyat bilgisi dahil)
    public ProductWithPriceResponse getProductByIdWithDealer(Long id, ProductWithDealerInfoRequest request) {
        logger.info("Fetching product with dealer info - productId: " + id + ", dealerId: " + request.dealerId());

        // Varlık kontrolleri
        Product product = productRepository.findByIdWithImages(id, EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));
        dealerService.getDealerById(request.dealerId());

        return mapToProductWithPrice(product, request.dealerId(), request.currency());
    }

    // Dealer için ürün arama (fiyat bilgisi dahil)
    public Page<ProductListItemResponse> searchProductsWithDealer(ProductDealerSearchRequest request,
                                                                  int page, int size, String sortBy, String sortDirection) {
        logger.info("Searching products with dealer info - dealerId: " + request.dealerId() +
                ", searchTerm: " + request.searchTerm());

        // Dealer varlık kontrolü
        dealerService.getDealerById(request.dealerId());

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Search logic with filters
        Page<Product> products = searchProductsWithFilters(request, pageable);

        List<ProductListItemResponse> productListItems = products.getContent().stream()
                .map(product -> mapToProductListItem(product, request.dealerId(), request.currency()))
                .filter(item -> applyDealerSpecificFilters(item, request))
                .collect(Collectors.toList());

        return new PageImpl<>(productListItems, pageable, products.getTotalElements());
    }

    // Kategoriye göre ürünler - Dealer bilgisi ile
    public Page<ProductListItemResponse> getProductsByCategoryWithDealer(Long categoryId,
                                                                         ProductWithDealerInfoRequest dealerRequest,
                                                                         int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching products by category with dealer info - categoryId: " + categoryId +
                ", dealerId: " + dealerRequest.dealerId());

        // Varlık kontrolleri
        categoryService.getCategoryById(categoryId);
        dealerService.getDealerById(dealerRequest.dealerId());

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findByCategoryIdAndStatusOrderByNameAsc(
                categoryId, EntityStatus.ACTIVE, pageable);

        List<ProductListItemResponse> productListItems = products.getContent().stream()
                .map(product -> mapToProductListItem(product, dealerRequest.dealerId(), dealerRequest.currency()))
                .collect(Collectors.toList());

        return new PageImpl<>(productListItems, pageable, products.getTotalElements());
    }

    // ==================== STANDARD OPERATIONS (WITHOUT DEALER) ====================

    // Genel arama - Summary format
    public Page<ProductSummary> searchProducts(String searchTerm, int page, int size,
                                               String sortBy, String sortDirection) {
        logger.info("Searching products with term: " + searchTerm);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.searchProducts(searchTerm, EntityStatus.ACTIVE, pageable);
        return products.map(productMapper::toSummary);
    }

    // Kategoriye göre ürünler - Summary format
    public Page<ProductSummary> getProductsByCategory(Long categoryId, int page, int size,
                                                      String sortBy, String sortDirection) {
        logger.info("Fetching products for category: " + categoryId);

        categoryService.getCategoryById(categoryId);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findByCategoryIdAndStatusOrderByNameAsc(
                categoryId, EntityStatus.ACTIVE, pageable);
        return products.map(productMapper::toSummary);
    }

    // Stokta olan ürünler - Summary format
    public Page<ProductSummary> getInStockProducts(int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching in-stock products");

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findInStockProducts(EntityStatus.ACTIVE, pageable);
        return products.map(productMapper::toSummary);
    }

    // Gelişmiş arama - Summary format
    // ProductService.java - advancedSearch metodunu düzelt

    public Page<ProductSummary> advancedSearch(ProductSearchCriteria criteria, int page, int size,
                                               String sortBy, String sortDirection) {
        logger.info("Advanced search with criteria");

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Kategoriler listesinden ilkini al (şimdilik tek kategori destekliyoruz)
        Long categoryId = null;
        if (criteria.categoryIds() != null && !criteria.categoryIds().isEmpty()) {
            categoryId = criteria.categoryIds().get(0);
        }

        // Malzemeler listesinden ilkini al (şimdilik tek malzeme destekliyoruz)
        String material = null;
        if (criteria.materials() != null && !criteria.materials().isEmpty()) {
            material = criteria.materials().get(0);
        }

        // Default değerleri belirle
        Boolean inStockOnly = criteria.inStockOnly() != null ? criteria.inStockOnly() : false;
        Boolean includeExpired = criteria.includeExpired() != null ? criteria.includeExpired() : true;

        Page<Product> products = productRepository.findByAdvancedCriteria(
                criteria.searchTerm(),
                categoryId,
                material,
                criteria.sterile(),
                criteria.implantable(),
                criteria.ceMarking(),
                criteria.fdaApproved(),
                criteria.minWeight(),
                criteria.maxWeight(),
                criteria.expiryDateFrom(),
                criteria.expiryDateTo(),
                criteria.minStockQuantity(),
                criteria.maxStockQuantity(),
                inStockOnly,
                includeExpired,
                EntityStatus.ACTIVE,
                pageable
        );

        return products.map(productMapper::toSummary);
    }

    // ==================== UTILITY OPERATIONS ====================

    public ProductSummary getProductSummary(Long id) {
        logger.info("Fetching product summary with id: " + id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));
        return productMapper.toSummary(product);
    }

    // ==================== BUSINESS OPERATIONS ====================

    // Süresi dolan ürünler
    public List<ProductSummary> getExpiredProducts() {
        logger.info("Fetching expired products");
        List<Product> expiredProducts = productRepository.findExpiredProducts(EntityStatus.ACTIVE);
        return expiredProducts.stream()
                .map(productMapper::toSummary)
                .collect(Collectors.toList());
    }

    // Yakında süresi dolacak ürünler
    public List<ProductSummary> getProductsExpiringInDays(int days) {
        logger.info("Fetching products expiring in " + days + " days");
        LocalDate futureDate = LocalDate.now().plusDays(days);
        List<Product> expiringProducts = productRepository.findProductsExpiringBefore(futureDate, EntityStatus.ACTIVE);
        return expiringProducts.stream()
                .map(productMapper::toSummary)
                .collect(Collectors.toList());
    }

    // Düşük stok ürünleri
    public List<ProductSummary> getLowStockProducts(Integer threshold) {
        logger.info("Fetching low stock products with threshold: " + threshold);
        List<Product> lowStockProducts = productRepository.findLowStockProducts(threshold, EntityStatus.ACTIVE);
        return lowStockProducts.stream()
                .map(productMapper::toSummary)
                .collect(Collectors.toList());
    }

    // Rastgele ürünler (ana sayfa için)
    public List<ProductSummary> getRandomProducts(int limit) {
        logger.info("Fetching " + limit + " random products");
        List<Product> randomProducts = productRepository.findRandomProducts(EntityStatus.ACTIVE, limit);
        return randomProducts.stream()
                .map(productMapper::toSummary)
                .collect(Collectors.toList());
    }

    // ==================== CRUD OPERATIONS ====================

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        logger.info("Creating new product: " + request.name());

        request.validate();
        categoryService.getCategoryById(request.categoryId());

        if (productRepository.existsByCodeAndStatus(request.code(), EntityStatus.ACTIVE)) {
            throw new BadCredentialsException("Product code already exists: " + request.code());
        }

        Product product = productMapper.toEntity(request);
        product.setStatus(EntityStatus.ACTIVE);

        Category category = new Category();
        category.setId(request.categoryId());
        product.setCategory(category);

        setDefaultValues(product);

        Product savedProduct = productRepository.save(product);
        logger.info("Product created successfully with id: " + savedProduct.getId());

        return productMapper.toDto(savedProduct);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        logger.info("Updating product with id: " + id);

        request.validate();

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));

        if (productRepository.existsByCodeAndStatusAndIdNot(request.code(), EntityStatus.ACTIVE, id)) {
            throw new BadCredentialsException("Product code already exists: " + request.code());
        }

        categoryService.getCategoryById(request.categoryId());
        productMapper.updateEntity(existingProduct, request);

        Product updatedProduct = productRepository.save(existingProduct);
        logger.info("Product updated successfully with id: " + updatedProduct.getId());

        return productMapper.toDto(updatedProduct);
    }

    @Transactional
    public ProductResponse updateStock(Long id, Integer stockQuantity) {
        logger.info("Updating stock for product: " + id + ", new stock: " + stockQuantity);

        if (stockQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));

        product.setStockQuantity(stockQuantity);
        Product updatedProduct = productRepository.save(product);

        return productMapper.toDto(updatedProduct);
    }

    @Transactional
    public void deleteProduct(Long id) {
        logger.info("Deleting product with id: " + id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));

        product.setStatus(EntityStatus.DELETED);
        productRepository.save(product);

        logger.info("Product deleted successfully with id: " + id);
    }

    @Transactional
    public ProductResponse restoreProduct(Long id) {
        logger.info("Restoring product with id: " + id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));

        product.setStatus(EntityStatus.ACTIVE);
        Product restoredProduct = productRepository.save(product);

        return productMapper.toDto(restoredProduct);
    }

    // ==================== FILTER AND STATISTICS ====================

    public List<String> getDistinctMaterials() {
        return productRepository.findDistinctMaterials(EntityStatus.ACTIVE);
    }

    public List<String> getDistinctUnits() {
        return productRepository.findDistinctUnits(EntityStatus.ACTIVE);
    }

    public List<String> getDistinctMedicalDeviceClasses() {
        return productRepository.findDistinctMedicalDeviceClasses(EntityStatus.ACTIVE);
    }

    public ProductStatistics getProductStatistics() {
        logger.info("Fetching product statistics");

        Long totalProducts = productRepository.countByStatus(EntityStatus.ACTIVE);
        Long inStockProducts = productRepository.countInStockProducts(EntityStatus.ACTIVE);
        Long outOfStockProducts = productRepository.countOutOfStockProducts(EntityStatus.ACTIVE);
        Long expiredProducts = productRepository.countExpiredProducts(EntityStatus.ACTIVE);

        return new ProductStatistics(totalProducts, inStockProducts, outOfStockProducts, expiredProducts);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private ProductListItemResponse mapToProductListItem(Product product, Long dealerId, CurrencyType currency) {
        // Default fiyat bilgilerini al
        Optional<ProductPrice> defaultPrice = productPriceRepository.findValidPrice(
                product.getId(), dealerId, currency, EntityStatus.ACTIVE);

        return new ProductListItemResponse(
                product.getId(),
                product.getName(),
                product.getCode(),
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getImages().stream()
                        .filter(img -> img.getIsPrimary())
                        .map(img -> img.getImageUrl())
                        .findFirst()
                        .orElse(null),
                product.getStockQuantity(),
                product.getUnit(),
                product.isInStock(),
                product.isExpired(),
                defaultPrice.map(ProductPrice::getAmount).orElse(null),
                currency.name(),
                defaultPrice.map(ProductPrice::isValidNow).orElse(false),
                product.getExpiryDate()
        );
    }

    private ProductWithPriceResponse mapToProductWithPrice(Product product, Long dealerId, CurrencyType currency) {
        // Bu dealer için tüm fiyat tiplerini al
        List<ProductPrice> dealerPrices = productPriceRepository.findByProductIdAndDealerIdAndStatus(
                product.getId(), dealerId, EntityStatus.ACTIVE);

        List<ProductPriceSummary> priceSummaries = dealerPrices.stream()
                .map(price -> new ProductPriceSummary(
                        price.getId(),
                        price.getProduct().getName(),
                        price.getDealer().getName(),
                        price.getCurrency(),
                        price.getAmount(),
                        price.isValidNow()
                ))
                .collect(Collectors.toList());

        // Default fiyatı bul (DEALER price type)
        BigDecimal defaultPrice = dealerPrices.stream()
                .filter(ProductPrice::isValidNow)
                .map(ProductPrice::getAmount)
                .findFirst()
                .orElse(null);

        return new ProductWithPriceResponse(
                product.getId(),
                product.getName(),
                product.getCode(),
                product.getDescription(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getMaterial(),
                product.getSize(),
                product.getSterile(),
                product.getImplantable(),
                product.getStockQuantity(),
                product.getMinimumOrderQuantity(),
                product.getMaximumOrderQuantity(),
                product.getExpiryDate(),
                product.getUnit(),
                product.getImages().stream()
                        .map(img -> new com.maxx_global.dto.productImage.ProductImageInfo(
                                img.getId(), img.getImageUrl(), img.getIsPrimary()))
                        .collect(Collectors.toList()),
                product.getImages().stream()
                        .filter(img -> img.getIsPrimary())
                        .map(img -> img.getImageUrl())
                        .findFirst()
                        .orElse(null),
                product.isInStock(),
                product.isExpired(),
                priceSummaries,
                defaultPrice,
                currency.name(),
                product.getCreatedAt(),
                product.getStatus().name()
        );
    }

    private Page<Product> searchProductsWithFilters(ProductDealerSearchRequest request, Pageable pageable) {
        // Advanced search criteria oluştur
        return productRepository.findByAdvancedCriteria(
                request.searchTerm(),
                request.categoryIds() != null && !request.categoryIds().isEmpty() ? request.categoryIds().get(0) : null,
                null, // material
                null, // sterile
                null, // implantable
                null, // ceMarking
                null, // fdaApproved
                null, // minWeight
                null, // maxWeight
                null, // expiryDateFrom
                null, // expiryDateTo
                null, // minStock
                null, // maxStock
                request.inStockOnly(),
                true, // includeExpired
                EntityStatus.ACTIVE,
                pageable
        );
    }

    private boolean applyDealerSpecificFilters(ProductListItemResponse item, ProductDealerSearchRequest request) {
        // Fiyat filtresi
        if (request.withPriceOnly() && item.dealerPrice() == null) {
            return false;
        }

        // Fiyat aralığı filtresi
        if (request.minPrice() != null && item.dealerPrice() != null &&
                item.dealerPrice().compareTo(request.minPrice()) < 0) {
            return false;
        }

        if (request.maxPrice() != null && item.dealerPrice() != null &&
                item.dealerPrice().compareTo(request.maxPrice()) > 0) {
            return false;
        }

        // Geçerli fiyat filtresi
        if (request.validPricesOnly() && !item.priceValid()) {
            return false;
        }

        return true;
    }

    private void setDefaultValues(Product product) {
        if (product.getStockQuantity() == null) {
            product.setStockQuantity(0);
        }
        if (product.getMinimumOrderQuantity() == null) {
            product.setMinimumOrderQuantity(1);
        }
        if (product.getMaximumOrderQuantity() == null) {
            product.setMaximumOrderQuantity(1000);
        }
    }


}
package com.maxx_global.service;

import com.maxx_global.dto.product.*;
import com.maxx_global.dto.productImage.ProductImageInfo;
import com.maxx_global.dto.productPrice.ProductPriceInfo;
import com.maxx_global.dto.productPrice.ProductPriceSummary;
import com.maxx_global.dto.productVariant.ProductVariantDTO;
import com.maxx_global.dto.productVariant.ProductVariantMapper;
import com.maxx_global.entity.*;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.StockMovementType;
import com.maxx_global.repository.CategoryRepository;
import com.maxx_global.repository.ProductPriceRepository;
import com.maxx_global.repository.ProductRepository;
import com.maxx_global.repository.ProductVariantRepository;
import com.maxx_global.repository.UserFavoriteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final Logger logger = Logger.getLogger(ProductService.class.getName());

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final ProductMapper productMapper;
    private final ProductVariantMapper productVariantMapper;
    private final ProductVariantRepository productVariantRepository;
    private final AppUserService appUserService;
    private final StockTrackerService stockTrackerService;

    // Facade Pattern - Diğer servisleri çağır
    private final CategoryService categoryService;
    private final DealerService dealerService;
    private final UserFavoriteRepository userFavoriteRepository;
    private final FileStorageService fileStorageService;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository,
                          ProductPriceRepository productPriceRepository,
                          ProductMapper productMapper,
                          ProductVariantMapper productVariantMapper,
                          ProductVariantRepository productVariantRepository,
                          AppUserService appUserService,
                          StockTrackerService stockTrackerService,
                          CategoryService categoryService,
                          DealerService dealerService,
                          UserFavoriteRepository userFavoriteRepository,
                          FileStorageService fileStorageService,
                          CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.productPriceRepository = productPriceRepository;
        this.productMapper = productMapper;
        this.productVariantMapper = productVariantMapper;
        this.productVariantRepository = productVariantRepository;
        this.appUserService = appUserService;
        this.stockTrackerService = stockTrackerService;
        this.categoryService = categoryService;
        this.dealerService = dealerService;
        this.userFavoriteRepository = userFavoriteRepository;
        this.fileStorageService = fileStorageService;
        this.categoryRepository = categoryRepository;
    }

    // ProductService.java dosyasına eklenecek yeni method

    // Basit ürün listesi (dropdown vs. için)
    // Basit ürün listesi (dropdown vs. için)
    public List<ProductSimple> getSimpleProducts() {
        logger.info("Fetching simple product list");
        List<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);
        return products.stream()
                .map(product -> {
                    // Primary image'i güvenli şekilde al
                    String primaryImageUrl = null;
                    if (product.getImages() != null && !product.getImages().isEmpty()) {
                        primaryImageUrl = product.getImages().stream()
                                .filter(img -> img.getIsPrimary() != null && img.getIsPrimary())
                                .findFirst()
                                .map(ProductImage::getImageUrl)
                                .orElseGet(() ->
                                        // Primary yoksa ilk resmi al
                                        product.getImages().stream()
                                                .findFirst()
                                                .map(ProductImage::getImageUrl)
                                                .orElse(null)
                                );
                    }

                    List<ProductSimpleVariant> variants = Optional.ofNullable(product.getVariants())
                            .orElse(Collections.emptySet())
                            .stream()
                            .filter(variant -> variant.getStatus() == null || EntityStatus.ACTIVE.equals(variant.getStatus()))
                            .sorted(Comparator
                                    .comparing(ProductVariant::getIsDefault, Comparator.nullsLast(Comparator.reverseOrder()))
                                    .thenComparing(ProductVariant::getId, Comparator.nullsLast(Long::compareTo)))
                            .map(variant -> new ProductSimpleVariant(
                                    variant.getId(),
                                    variant.getSize(),
                                    variant.getSku(),
                                    variant.getStockQuantity(),
                                    variant.getIsDefault()
                            ))
                            .collect(Collectors.toList());

                    return new ProductSimple(
                            product.getId(),
                            product.getName(),
                            product.getCode(),
                            primaryImageUrl,
                            variants
                    );
                })
                .collect(Collectors.toList());
    }

    // ==================== BASIC PRODUCT OPERATIONS ====================

    // Tüm ürünleri getir (sayfalama ile) - Dealer bilgisi olmadan
    public Page<ProductSummary> getAllProducts(int page, int size, String sortBy, String sortDirection, Authentication authentication) {

        logger.info("Fetching all products - page: " + page + ", size: " + size +
                ", sortBy: " + sortBy + ", direction: " + sortDirection);

        // Mevcut kullanıcıyı al
        AppUser currentUser = appUserService.getCurrentUser(authentication);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findAll(pageable);

        // Favori kontrolü için tek sorguda al
        Set<Long> favoriteProductIds = getUserFavoriteProductIds(currentUser.getId());

        // Map ve isFavorite + prices set et
        return getProductSummariesWithPrices(favoriteProductIds, currentUser, pageable, products);
    }

    // Aktif ürünleri getir - Summary format
    public List<ProductSummary> getActiveProducts(Authentication authentication) {
        logger.info("Fetching active products");

        // Mevcut kullanıcıyı al
        AppUser currentUser = appUserService.getCurrentUser(authentication);

        // Kullanıcının favori ürün ID'lerini al (tek sorguda - performans için)
        Set<Long> favoriteProductIds = userFavoriteRepository.findUserFavoriteProductIds(
                        currentUser.getId(), EntityStatus.ACTIVE)
                .stream().collect(Collectors.toSet());

        List<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);

        return products.stream()
                .map(product -> {
                    ProductSummary summary = productMapper.toSummary(product);

                    // Fiyat bilgilerini al
                   // List<ProductPriceInfo> priceInfos = getPriceInfosForUser(product.getId(), currentUser);

                    return new ProductSummary(
                            summary.id(), summary.name(), summary.code(), summary.categoryName(),
                            summary.primaryImageUrl(), summary.stockQuantity(), summary.unit(),
                            summary.isActive(), summary.isInStock(), summary.status(),
                            favoriteProductIds.contains(product.getId()) // isFavorite
                    );
                })
                .collect(Collectors.toList());
    }

    // ID ile ürün getir - Dealer bilgisi olmadan (detay bilgisi)
    public ProductResponse getProductById(Long id, Authentication authentication) {

        AppUser currentUser = appUserService.getCurrentUser(authentication);

        logger.info("Fetching product with id: " + id);
        Product product = productRepository.findByIdWithImages(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));

        // Favori kontrolü
        boolean isFavorite = userFavoriteRepository.findByUserIdAndProductIdAndStatus(
                currentUser.getId(), product.getId(), EntityStatus.ACTIVE).isPresent();

        ProductResponse response = productMapper.toDto(product);

        // ✅ Variant bilgilerini map et
        List<ProductVariantDTO> variants = getVariantsForProduct(product, currentUser);

        // Response'u güncellenmiş bilgilerle oluştur
        return new ProductResponse(
                response.id(), response.name(), response.code(), response.description(),
                response.categoryId(), response.categoryName(), response.material(), response.size(),
                variants, // ✅ Variant bilgileri
                response.diameter(), response.angle(), response.sterile(), response.singleUse(),
                response.implantable(), response.ceMarking(), response.fdaApproved(),
                response.medicalDeviceClass(), response.regulatoryNumber(), response.weightGrams(),
                response.dimensions(), response.color(), response.surfaceTreatment(),
                response.serialNumber(), response.manufacturerCode(), response.manufacturingDate(),
                response.expiryDate(), response.shelfLifeMonths(), response.unit(), response.barcode(),
                response.lotNumber(), response.stockQuantity(), response.minimumOrderQuantity(),
                response.maximumOrderQuantity(), response.images(), response.primaryImageUrl(),
                response.isActive(), response.isInStock(), response.isExpired(),
                response.createdDate(), response.updatedDate(), response.status(),
                isFavorite // isFavorite
        );
    }

//    private List<ProductPriceInfo> getPriceInfosForUser(Long productId, AppUser user) {
//        // Eğer kullanıcının dealer'ı varsa fiyat bilgilerini getir
//        boolean hasProductPricePermission = user.getRoles().stream()
//                .flatMap(role -> role.getPermissions().stream())
//                .anyMatch(permission -> "PRICE_READ".equals(permission.getName()));
//        if (user.getDealer() != null && hasProductPricePermission) {
//            logger.info("Getting price info for product: " + productId + ", dealer: " + user.getDealer().getId() +
//                    ", preferred currency: " + user.getDealer().getPreferredCurrency());
//
//            // Sadece dealer'ın preferred currency'sindeki fiyatı al
//            Optional<ProductPrice> priceOptional = productPriceRepository.findValidPrice(
//                    productId, user.getDealer().getId(), user.getDealer().getPreferredCurrency(), EntityStatus.ACTIVE);
//
//            if (priceOptional.isPresent()) {
//                ProductPrice price = priceOptional.get();
//                return List.of(new ProductPriceInfo(
//                        price.getId(),
//                        price.getCurrency(),
//                        price.getAmount()
//                ));
//            } else {
//                // Bu dealer için bu currency'de fiyat yoksa boş liste döndür
//                logger.info("No price found for product: " + productId + ", dealer: " + user.getDealer().getId() +
//                        ", currency: " + user.getDealer().getPreferredCurrency());
//                return List.of();
//            }
//        }
//
//        // Admin kullanıcısı veya dealer'ı olmayan kullanıcı için null döndür
//        return null;
//    }

    // ProductService.java - Eklenecek method

    /**
     * Birden fazla ürünü ID'lere göre getirir
     * @param productIds Ürün ID'leri listesi
     * @param authentication Kullanıcı bilgisi
     * @return ProductSummary listesi (fiyat bilgisi ile birlikte)
     */
    public List<ProductSummary> getBulkProducts(List<Long> productIds, Authentication authentication) {
        logger.info("Fetching bulk products - IDs: " + productIds);

        // Request validation
        BulkProductRequest request = new BulkProductRequest(productIds);
        request.validate();

        // Mevcut kullanıcıyı al
        AppUser currentUser = appUserService.getCurrentUser(authentication);

        // Kullanıcının favori ürün ID'lerini al (tek sorguda - performans için)
        Set<Long> favoriteProductIds = userFavoriteRepository.findUserFavoriteProductIds(
                        currentUser.getId(), EntityStatus.ACTIVE)
                .stream().collect(Collectors.toSet());

        // Ürünleri getir (tek sorguda - IN clause ile)
        List<Product> products = productRepository.findAllById(productIds);

        // Bulunamayan ID'leri kontrol et
        Set<Long> foundIds = products.stream()
                .map(Product::getId)
                .collect(Collectors.toSet());

        List<Long> notFoundIds = productIds.stream()
                .filter(id -> !foundIds.contains(id))
                .collect(Collectors.toList());

        if (!notFoundIds.isEmpty()) {
            logger.warning("Some products not found: " + notFoundIds);
            // Bulunamayan ID'ler için warning log, ancak hata fırlatma
            // Bulunan ürünlerle devam et
        }

        // Sadece aktif ürünleri filtrele
        List<Product> activeProducts = products.stream()
                .filter(product -> product.getStatus() == EntityStatus.ACTIVE)
                .collect(Collectors.toList());

        logger.info("Found " + activeProducts.size() + " active products out of " + productIds.size() + " requested");

        // ProductSummary'lere dönüştür (fiyat bilgileri ile birlikte)
        return activeProducts.stream()
                .map(product -> {
                    ProductSummary summary = productMapper.toSummary(product);

                    // Fiyat bilgilerini al
                    //List<ProductPriceInfo> priceInfos = getPriceInfosForUser(product.getId(), currentUser);

                    return new ProductSummary(
                            summary.id(), summary.name(), summary.code(), summary.categoryName(),
                            summary.primaryImageUrl(), summary.stockQuantity(), summary.unit(),
                            summary.isActive(), summary.isInStock(), summary.status(),
                            favoriteProductIds.contains(product.getId())
                    );
                })
                .collect(Collectors.toList());
    }

    private Page<ProductSummary> getProductSummariesWithPrices(Set<Long> favoriteProductIds, AppUser currentUser,
                                                               Pageable pageable, Page<Product> products) {

        List<ProductSummary> summaries = products.getContent().stream()
                .map(product -> {
                    ProductSummary summary = productMapper.toSummary(product);

                    // Fiyat bilgilerini al
                   // List<ProductPriceInfo> priceInfos = getPriceInfosForUser(product.getId(), currentUser);

                    return new ProductSummary(
                            summary.id(), summary.name(), summary.code(), summary.categoryName(),
                            summary.primaryImageUrl(), summary.stockQuantity(), summary.unit(),
                            summary.isActive(), summary.isInStock(), summary.status(),
                            favoriteProductIds.contains(product.getId())
                    );
                })
                .collect(Collectors.toList());

        return new PageImpl<>(summaries, pageable, products.getTotalElements());
    }

    // ==================== DEALER-SPECIFIC OPERATIONS ====================

    // Dealer bilgisi ile tüm ürünleri getir (fiyat bilgisi dahil)
    public Page<ProductListItemResponse> getAllProductsWithDealer(ProductWithDealerInfoRequest request,
                                                                  int page, int size, String sortBy, String sortDirection, Authentication authentication) {
        logger.info("Fetching products with dealer info - dealerId: " + request.dealerId());

        // Dealer varlık kontrolü
        dealerService.getDealerById(request.dealerId());

        // Mevcut kullanıcıyı al
        AppUser currentUser = appUserService.getCurrentUser(authentication);

        // Kullanıcının favori ürün ID'lerini al (tek sorguda - performans için)
        Set<Long> favoriteProductIds = userFavoriteRepository.findUserFavoriteProductIds(
                        currentUser.getId(), EntityStatus.ACTIVE)
                .stream().collect(Collectors.toSet());

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findByStatus(EntityStatus.ACTIVE, pageable);

        return getProductListItemResponses(request, favoriteProductIds, pageable, products);
    }

    // Dealer bilgisi ile ürün detayı getir (fiyat bilgisi dahil)
    public ProductWithPriceResponse getProductByIdWithDealer(Long id, ProductWithDealerInfoRequest request, Authentication authentication) {
        logger.info("Fetching product with dealer info - productId: " + id + ", dealerId: " + request.dealerId());

        // Varlık kontrolleri
        Product product = productRepository.findByIdWithImages(id, EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));
        dealerService.getDealerById(request.dealerId());

        // Mevcut kullanıcıyı al
        AppUser currentUser = appUserService.getCurrentUser(authentication);

        // Favori kontrolü
        boolean isFavorite = userFavoriteRepository.findByUserIdAndProductIdAndStatus(
                currentUser.getId(), product.getId(), EntityStatus.ACTIVE).isPresent();

        ProductWithPriceResponse response = mapToProductWithPrice(product, request.dealerId(), request.currency());

        return new ProductWithPriceResponse(
                response.id(), response.name(), response.code(), response.description(),
                response.categoryId(), response.categoryName(), response.material(), response.size(),
                response.sterile(), response.implantable(), response.stockQuantity(),
                response.minimumOrderQuantity(), response.maximumOrderQuantity(), response.expiryDate(),
                response.unit(), response.images(), response.primaryImageUrl(), response.isInStock(),
                response.isExpired(), response.dealerPrices(), response.defaultPrice(),
                response.defaultCurrency(), response.createdDate(), response.status(),
                isFavorite // isFavorite
        );
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
                                                                         int page, int size, String sortBy, String sortDirection, Authentication authentication) {
        logger.info("Fetching products by category with dealer info - categoryId: " + categoryId +
                ", dealerId: " + dealerRequest.dealerId());

        // Varlık kontrolleri
        categoryService.getCategoryById(categoryId);
        dealerService.getDealerById(dealerRequest.dealerId());

        // Mevcut kullanıcıyı al
        AppUser currentUser = appUserService.getCurrentUser(authentication);

        // Kullanıcının favori ürün ID'lerini al (tek sorguda - performans için)
        Set<Long> favoriteProductIds = userFavoriteRepository.findUserFavoriteProductIds(
                        currentUser.getId(), EntityStatus.ACTIVE)
                .stream().collect(Collectors.toSet());

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Kategorinin parent mi leaf mi olduğunu kontrol et
        List<Category> subCategories = categoryRepository.findByParentCategoryIdAndStatusOrderByNameAsc(
                categoryId, EntityStatus.ACTIVE);

        Page<Product> products;

        if (subCategories.isEmpty()) {
            // LEAF CATEGORY - Sadece bu kategorideki ürünleri getir
            logger.info("Category " + categoryId + " is leaf category - fetching direct products with dealer info");
            products = productRepository.findByCategoryIdAndStatusOrderByNameAsc(
                    categoryId, EntityStatus.ACTIVE, pageable);
        } else {
            // PARENT CATEGORY - Tüm alt kategori ürünlerini getir
            logger.info("Category " + categoryId + " is parent category - fetching child products with dealer info");

            List<Long> allChildCategoryIds = getAllChildCategoryIds(categoryId);

            if (allChildCategoryIds.isEmpty()) {
                return Page.empty(pageable);
            }

            products = productRepository.findByCategoryIdInAndStatus(
                    allChildCategoryIds, EntityStatus.ACTIVE, pageable);
        }

        return getProductListItemResponses(dealerRequest, favoriteProductIds, pageable, products);
    }

    private Page<ProductListItemResponse> getProductListItemResponses(ProductWithDealerInfoRequest dealerRequest, Set<Long> favoriteProductIds, Pageable pageable, Page<Product> products) {
        List<ProductListItemResponse> productListItems = products.getContent().stream()
                .map(product -> {
                    ProductListItemResponse item = mapToProductListItem(product, dealerRequest.dealerId(), dealerRequest.currency());
                    // isFavorite alanını set et
                    return new ProductListItemResponse(
                            item.id(), item.name(), item.code(), item.categoryName(), item.primaryImageUrl(),
                            item.stockQuantity(), item.unit(), item.isInStock(), item.isExpired(),
                            item.dealerPrice(), item.currency(), item.priceValid(), item.expiryDate(),
                            favoriteProductIds.contains(product.getId()) // isFavorite
                    );
                })
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

    public List<Product> getProductsByCategory(Long categoryId) {
        logger.info("Fetching products for category: " + categoryId);

        // Category varlık kontrolü
        categoryService.getCategoryById(categoryId);

        // Repository'den kategoriyle ilgili aktif ürünleri getir
        return productRepository.findByCategory_IdAndStatus(categoryId, EntityStatus.ACTIVE);
    }

    // Kategoriye göre ürünler - Summary format
    public Page<ProductSummary> getProductsByCategory(Long categoryId, int page, int size,
                                                      String sortBy, String sortDirection, Authentication authentication) {
        logger.info("Fetching products for category: " + categoryId);

        categoryService.getCategoryById(categoryId);

        // Mevcut kullanıcıyı al
        AppUser currentUser = appUserService.getCurrentUser(authentication);

        // Kullanıcının favori ürün ID'lerini al
        Set<Long> favoriteProductIds = userFavoriteRepository.findUserFavoriteProductIds(
                        currentUser.getId(), EntityStatus.ACTIVE)
                .stream().collect(Collectors.toSet());

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Kategorinin parent mi leaf mi olduğunu kontrol et
        List<Category> subCategories = categoryRepository.findByParentCategoryIdAndStatusOrderByNameAsc(
                categoryId, EntityStatus.ACTIVE);

        Page<Product> products;

        if (subCategories.isEmpty()) {
            // LEAF CATEGORY - Sadece bu kategorideki ürünleri getir
            logger.info("Category " + categoryId + " is leaf category - fetching direct products");
            products = productRepository.findByCategoryIdAndStatusOrderByNameAsc(
                    categoryId, EntityStatus.ACTIVE, pageable);

            logger.info("Found " + products.getTotalElements() + " products in leaf category: " + categoryId);
        } else {
            // PARENT CATEGORY - Tüm alt kategori ürünlerini getir
            logger.info("Category " + categoryId + " is parent category with " + subCategories.size() + " children - fetching child products");

            // Tüm alt kategori ID'lerini al (recursive)
            List<Long> allChildCategoryIds = getAllChildCategoryIds(categoryId);

            if (allChildCategoryIds.isEmpty()) {
                // Alt kategori yoksa boş sonuç döndür
                logger.info("No valid child categories found for parent category: " + categoryId);
                return Page.empty(pageable);
            }

            // Alt kategorilerdeki ürünleri getir
            products = productRepository.findByCategoryIdInAndStatus(
                    allChildCategoryIds, EntityStatus.ACTIVE, pageable);

            logger.info("Found " + products.getTotalElements() + " products in " +
                    allChildCategoryIds.size() + " child categories of parent: " + categoryId);
        }

        return getProductSummariesWithPrices(favoriteProductIds, currentUser, pageable, products);
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

    public ProductVariant getVariant(Long id) {
        logger.info("Fetching product summary with id: " + id);
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));
        return Objects.isNull(variant) ? null : variant ;
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
    public ProductResponse addProductImages(Long productId, List<MultipartFile> images, Integer primaryImageIndex) {
        logger.info("Adding " + images.size() + " images to product: " + productId);

        // Ürünü bul
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Ürün bulunamadı: " + productId));

        // Mevcut resim sayısını kontrol et
        int currentImageCount = product.getImages() != null ? product.getImages().size() : 0;
        if (currentImageCount + images.size() > 10) {
            throw new IllegalArgumentException("Toplam resim sayısı 10'u geçemez. Mevcut: " + currentImageCount +
                    ", Yeni: " + images.size());
        }

        try {
            // Resimleri fiziksel olarak kaydet
            List<String> imageUrls = fileStorageService.saveProductImages(
                    images, productId, primaryImageIndex);

            // ProductImage entity'lerini oluştur
            Set<ProductImage> newProductImages = createProductImageEntities(
                    product, imageUrls, primaryImageIndex);

            // Mevcut resimler varsa primary flag'leri kaldır (sadece yeni primary varsa)
            if (primaryImageIndex != null && product.getImages() != null) {
                product.getImages().forEach(img -> img.setIsPrimary(false));
            }

            // Yeni resimleri ekle
            if (product.getImages() == null) {
                product.setImages(new HashSet<>());
            }
            product.getImages().addAll(newProductImages);

            // Kaydet
            Product savedProduct = productRepository.save(product);
            logger.info("Successfully added " + images.size() + " images to product: " + productId);

            return productMapper.toDto(savedProduct);

        } catch (Exception e) {
            logger.severe("Error adding images to product: " + e.getMessage());
            throw new RuntimeException("Resim ekleme sırasında hata oluştu: " + e.getMessage());
        }
    }

    /**
     * Ana resmi değiştir
     */
    @Transactional
    public ProductResponse setPrimaryImage(Long productId, Long imageId) {
        logger.info("Setting primary image for product: " + productId + ", imageId: " + imageId);

        Product product = productRepository.findByIdWithImages(productId, EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Ürün bulunamadı: " + productId));

        // Tüm resimlerin primary flag'ini kaldır
        product.getImages().forEach(img -> img.setIsPrimary(false));

        // Belirtilen resmi primary yap
        ProductImage targetImage = product.getImages().stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Resim bulunamadı: " + imageId));

        targetImage.setIsPrimary(true);

        Product savedProduct = productRepository.save(product);
        logger.info("Primary image set successfully");

        return productMapper.toDto(savedProduct);
    }

    /**
     * Ürün resmini sil
     */
    @Transactional
    public ProductResponse deleteProductImage(Long productId, Long imageId) {
        logger.info("Deleting image: " + imageId + " from product: " + productId);

        Product product = productRepository.findByIdWithImages(productId, EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Ürün bulunamadı: " + productId));

        // Silinecek resmi bul
        ProductImage imageToDelete = product.getImages().stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Resim bulunamadı: " + imageId));

        // Fiziksel dosyayı sil
        try {
            fileStorageService.deleteImageFile(imageToDelete.getImageUrl());
        } catch (Exception e) {
            logger.warning("Could not delete physical file: " + e.getMessage());
        }

        // Entity'den kaldır
        product.getImages().remove(imageToDelete);

        // Eğer primary resim silindiyse, ilk resmi primary yap
        if (imageToDelete.getIsPrimary() && !product.getImages().isEmpty()) {
            product.getImages().iterator().next().setIsPrimary(true);
            logger.info("Set new primary image after deletion");
        }

        Product savedProduct = productRepository.save(product);
        logger.info("Image deleted successfully");

        return productMapper.toDto(savedProduct);
    }

    /**
     * Ürün resimlerini listele
     */
    @Transactional(readOnly = true)
    public List<ProductImageInfo> getProductImages(Long productId) {
        logger.info("Fetching images for product: " + productId);

        Product product = productRepository.findByIdWithImages(productId, EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Ürün bulunamadı: " + productId));

        return product.getImages().stream()
                .map(img -> new ProductImageInfo(
                        img.getId(),
                        img.getImageUrl(),
                        img.getIsPrimary()
                ))
                .sorted((img1, img2) -> {
                    // Primary resmi en üste koy
                    if (img1.isPrimary() && !img2.isPrimary()) return -1;
                    if (!img1.isPrimary() && img2.isPrimary()) return 1;
                    return img1.id().compareTo(img2.id());
                })
                .collect(Collectors.toList());
    }

    /**
     * ProductImage entity'lerini oluştur
     */
    private Set<ProductImage> createProductImageEntities(Product product, List<String> imageUrls, Integer primaryImageIndex) {
        Set<ProductImage> productImages = new HashSet<>();

        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImage productImage = new ProductImage();
            productImage.setProduct(product);
            productImage.setImageUrl(imageUrls.get(i));
            productImage.setIsPrimary(primaryImageIndex != null && primaryImageIndex == i);
            productImage.setStatus(EntityStatus.ACTIVE);

            productImages.add(productImage);

            logger.info("Created ProductImage entity - URL: " + imageUrls.get(i) +
                    ", isPrimary: " + productImage.getIsPrimary());
        }

        // Eğer primary image belirtilmemişse, ilk resmi primary yap
        if (primaryImageIndex == null && !productImages.isEmpty()) {
            productImages.iterator().next().setIsPrimary(true);
            logger.info("Set first image as primary (no primary index specified)");
        }

        return productImages;
    }

    /**
     * CreateProduct metodunu basitleştir (resim olmadan)
     */
    // ProductService.java içindeki create ve update method'larına eklenecek validasyon:

    @Transactional
    public ProductResponse createProduct(ProductRequest request, Authentication authentication) {
        logger.info("Creating new product with variant support: " + request.name());

        // Validasyon
        request.validate();
        validateLeafCategory(request.categoryId());

        // Ürün kodu benzersizlik kontrolü
        if (productRepository.existsByCodeAndStatus(request.code(), EntityStatus.ACTIVE)) {
            throw new BadCredentialsException("Ürün kodu zaten kullanılıyor: " + request.code());
        }

        // Product entity'sini oluştur
        Product product = productMapper.toEntity(request);
        product.setStatus(EntityStatus.ACTIVE);

        // Category set et
        Category category = new Category();
        category.setId(request.categoryId());
        product.setCategory(category);

        // Default değerleri ayarla
        setDefaultValues(product);

        product.setExpiryDate(LocalDate.now().plusYears(50));
        product.setShelfLifeMonths(300);

        // Product'ı kaydet
        Product savedProduct = productRepository.save(product);

        // ✅ YENİ: Variant'ları oluştur ve kaydet
        if (request.variants() != null && !request.variants().isEmpty()) {
            logger.info("Creating " + request.variants().size() + " variants for product: " + savedProduct.getCode());

            AppUser currentUser = appUserService.getCurrentUser(authentication);

            for (var variantRequest : request.variants()) {
                // Variant entity'sini oluştur (ID set etme - Hibernate otomatik atayacak)
                ProductVariant variant = productVariantMapper.toEntity(variantRequest);
                variant.setProduct(savedProduct);
                variant.setStatus(EntityStatus.ACTIVE);

                // Variant'ı kaydet ve flush et (ID'nin oluşmasını sağla)
                ProductVariant savedVariant = productVariantRepository.saveAndFlush(variant);

                // ⚠️ Fiyatlar ProductPriceExcelService üzerinden atanacak
                // Kayıt sırasında fiyat oluşturulmayacak

                // ✅ Variant için stok takibi
                if (savedVariant.getStockQuantity() != null && savedVariant.getStockQuantity() > 0) {
                    try {
                        stockTrackerService.trackInitialStockForVariant(
                                savedVariant,
                                savedVariant.getStockQuantity(),
                                currentUser
                        );
                        logger.info("Initial stock tracked for variant: " + savedVariant.getSku() +
                                " (Stock: " + savedVariant.getStockQuantity() + ")");
                    } catch (Exception e) {
                        logger.warning("Could not track initial stock for variant " + savedVariant.getSku() +
                                ": " + e.getMessage());
                    }
                }

                logger.info("Created variant: " + savedVariant.getId() + " - " + savedVariant.getSize() +
                        " (SKU: " + savedVariant.getSku() + ", Stock: " + savedVariant.getStockQuantity() + ")");
            }
        } else {
            // ⚠️ DEPRECATED: Backward compatibility - eski product-level stock tracking
            Integer initialStock = savedProduct.getStockQuantity();
            if (initialStock != null && initialStock > 0) {
                try {
                    AppUser currentUser = appUserService.getCurrentUser(authentication);
                    stockTrackerService.trackInitialStock(savedProduct, initialStock, currentUser);

                    logger.info("Initial stock tracked for product (deprecated): " + savedProduct.getCode() +
                            " (Initial stock: " + initialStock + ")");
                } catch (Exception e) {
                    logger.warning("Could not track initial stock for product " + savedProduct.getCode() +
                            ": " + e.getMessage());
                }
            }
        }

        logger.info("Product created successfully with id: " + savedProduct.getId());

        // Response döndürürken variant'ları da dahil et
        ProductResponse response = productMapper.toDto(savedProduct);
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        List<ProductVariantDTO> variants = getVariantsForProduct(savedProduct, currentUser);

        return new ProductResponse(
                response.id(), response.name(), response.code(), response.description(),
                response.categoryId(), response.categoryName(), response.material(), response.size(),
                variants, // ✅ Variant bilgileri
                response.diameter(), response.angle(), response.sterile(), response.singleUse(),
                response.implantable(), response.ceMarking(), response.fdaApproved(),
                response.medicalDeviceClass(), response.regulatoryNumber(), response.weightGrams(),
                response.dimensions(), response.color(), response.surfaceTreatment(),
                response.serialNumber(), response.manufacturerCode(), response.manufacturingDate(),
                response.expiryDate(), response.shelfLifeMonths(), response.unit(), response.barcode(),
                response.lotNumber(), response.stockQuantity(), response.minimumOrderQuantity(),
                response.maximumOrderQuantity(), response.images(), response.primaryImageUrl(),
                response.isActive(), response.isInStock(), response.isExpired(),
                response.createdDate(), response.updatedDate(), response.status(),
                false // isFavorite - yeni ürün henüz favorilerde değil
        );
    }


    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request, Authentication authentication) {
        logger.info("Updating product with variant support - id: " + id);

        // Request validation
        request.validate();

        // Mevcut product'ı getir
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));

        // Ürün kodu benzersizlik kontrolü (kendi id'si hariç)
        if (productRepository.existsByCodeAndStatusAndIdNot(request.code(), EntityStatus.ACTIVE, id)) {
            throw new BadCredentialsException("Product code already exists: " + request.code());
        }

        if (!existingProduct.getCategory().getId().equals(request.categoryId())) {
            validateLeafCategory(request.categoryId());
        }

        // Yeni kategori varlık kontrolü
        Category newCategory = categoryService.getCategoryEntityById(request.categoryId());

        // Mapper ile field'ları güncelle (category hariç)
        productMapper.updateEntity(existingProduct, request);

        // Category'i manuel olarak set et
        existingProduct.setCategory(newCategory);

        // Product'ı kaydet
        Product updatedProduct = productRepository.save(existingProduct);

        // ✅ YENİ: Variant'ları güncelle
        if (request.variants() != null && !request.variants().isEmpty()) {
            logger.info("Updating variants for product: " + updatedProduct.getCode());

            AppUser currentUser = appUserService.getCurrentUser(authentication);

            // Mevcut variant'ları al
            List<ProductVariant> existingVariants = updatedProduct.getVariants() != null ?
                    new ArrayList<>(updatedProduct.getVariants()) : new ArrayList<>();

            // Request'teki variant ID'lerini topla
            Set<Long> requestVariantIds = request.variants().stream()
                    .map(variant -> variant.id())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Mevcut variant ID'lerini topla
            Set<Long> existingVariantIds = existingVariants.stream()
                    .map(ProductVariant::getId)
                    .collect(Collectors.toSet());

            // Request'te olmayan variant'ları pasif yap (soft delete)
            existingVariants.stream()
                    .filter(variant -> variant.getId() != null && !requestVariantIds.contains(variant.getId()))
                    .forEach(variant -> {
                        variant.setStatus(EntityStatus.DELETED);
                        productVariantRepository.save(variant);
                        logger.info("Deleted variant: " + variant.getId() + " - " + variant.getSize());
                    });

            // Request'teki her variant'ı işle (yeni veya güncelleme)
            for (var variantRequest : request.variants()) {
                if (variantRequest.id() != null && existingVariantIds.contains(variantRequest.id())) {
                    // GÜNCELLEME: Mevcut variant'ı güncelle
                    ProductVariant existingVariant = existingVariants.stream()
                            .filter(v -> v.getId().equals(variantRequest.id()))
                            .findFirst()
                            .orElseThrow(() -> new EntityNotFoundException("Variant not found: " + variantRequest.id()));

                    Integer oldStock = existingVariant.getStockQuantity();
                    productVariantMapper.updateEntity(existingVariant, variantRequest);
                    ProductVariant savedVariant = productVariantRepository.save(existingVariant);

                    // ⚠️ Fiyatlar ProductPriceExcelService üzerinden atanacak
                    // Kayıt/güncelleme sırasında fiyat işlemi yapılmayacak

                    // Stok değişikliği kontrolü
                    Integer newStock = savedVariant.getStockQuantity();
                    if (!Objects.equals(oldStock, newStock)) {
                        logger.info("Stock changed for variant " + savedVariant.getSku() +
                                ": " + oldStock + " -> " + newStock);
                        // TODO: Variant için stok tracking eklenebilir
                    }

                    logger.info("Updated variant: " + savedVariant.getId() + " - " + savedVariant.getSize());
                } else {
                    // YENİ: Yeni variant oluştur (ID set etme - Hibernate otomatik atayacak)
                    ProductVariant newVariant = productVariantMapper.toEntity(variantRequest);
                    newVariant.setProduct(updatedProduct);
                    newVariant.setStatus(EntityStatus.ACTIVE);

                    ProductVariant savedVariant = productVariantRepository.saveAndFlush(newVariant);

                    // ⚠️ Fiyatlar ProductPriceExcelService üzerinden atanacak
                    // Kayıt sırasında fiyat oluşturulmayacak

                    // Yeni variant için stok takibi
                    if (savedVariant.getStockQuantity() != null && savedVariant.getStockQuantity() > 0) {
                        try {
                            stockTrackerService.trackInitialStockForVariant(
                                    savedVariant,
                                    savedVariant.getStockQuantity(),
                                    currentUser
                            );
                            logger.info("Initial stock tracked for new variant: " + savedVariant.getSku() +
                                    " (Stock: " + savedVariant.getStockQuantity() + ")");
                        } catch (Exception e) {
                            logger.warning("Could not track initial stock for variant " + savedVariant.getSku() +
                                    ": " + e.getMessage());
                        }
                    }

                    logger.info("Created new variant: " + savedVariant.getId() + " - " + savedVariant.getSize());
                }
            }
        } else {
            // ⚠️ DEPRECATED: Backward compatibility - eski product-level stock tracking
            Integer oldStock = existingProduct.getStockQuantity();
            Integer newStock = request.stockQuantity();

            if (oldStock != null && newStock != null && !oldStock.equals(newStock)) {
                logger.info("Stock changed for product " + updatedProduct.getCode() +
                        ": " + oldStock + " -> " + newStock);

                StockMovementType movementType = newStock > oldStock ?
                        StockMovementType.ADJUSTMENT_IN : StockMovementType.ADJUSTMENT_OUT;

                String reason = String.format("Ürün güncelleme - Stok değişikliği (%d -> %d)",
                        oldStock, newStock);

                stockTrackerService.trackStockChange(
                        updatedProduct,
                        oldStock,
                        newStock,
                        movementType,
                        reason,
                        getCurrentUser(),
                        "PRODUCT_UPDATE",
                        updatedProduct.getId()
                );
            } else if (oldStock == null && newStock != null && newStock > 0) {
                logger.info("Initial stock set for product (deprecated) " + updatedProduct.getCode() + ": " + newStock);

                stockTrackerService.trackStockChange(
                        updatedProduct,
                        0,
                        newStock,
                        StockMovementType.INITIAL_STOCK,
                        "Ürün güncellemesi - İlk stok girişi: " + newStock,
                        getCurrentUser(),
                        "PRODUCT_UPDATE",
                        updatedProduct.getId()
                );
            }
        }

        logger.info("Product updated successfully with id: " + updatedProduct.getId());

        // Response döndürürken variant'ları da dahil et
        ProductResponse response = productMapper.toDto(updatedProduct);
        AppUser currentUser = appUserService.getCurrentUser(authentication);
        List<ProductVariantDTO> variants = getVariantsForProduct(updatedProduct, currentUser);

        return new ProductResponse(
                response.id(), response.name(), response.code(), response.description(),
                response.categoryId(), response.categoryName(), response.material(), response.size(),
                variants, // ✅ Variant bilgileri
                response.diameter(), response.angle(), response.sterile(), response.singleUse(),
                response.implantable(), response.ceMarking(), response.fdaApproved(),
                response.medicalDeviceClass(), response.regulatoryNumber(), response.weightGrams(),
                response.dimensions(), response.color(), response.surfaceTreatment(),
                response.serialNumber(), response.manufacturerCode(), response.manufacturingDate(),
                response.expiryDate(), response.shelfLifeMonths(), response.unit(), response.barcode(),
                response.lotNumber(), response.stockQuantity(), response.minimumOrderQuantity(),
                response.maximumOrderQuantity(), response.images(), response.primaryImageUrl(),
                response.isActive(), response.isInStock(), response.isExpired(),
                response.createdDate(), response.updatedDate(), response.status(),
                false // isFavorite - update için false (gerçek değer controller'dan gelecek)
        );
    }

    private AppUser getCurrentUser() {
        // Bu metod SecurityContextHolder'dan mevcut kullanıcıyı alacak
        // Şimdilik null dönelim, sonra implement ederiz
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                // AppUserService kullanarak kullanıcıyı al
                return appUserService.getCurrentUser(authentication);
            }
        } catch (Exception e) {
            logger.warning("Could not get current user for Excel import: " + e.getMessage());
        }
        return null; // System user olarak kaydedilecek
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
                .orElseThrow(() -> new EntityNotFoundException("Ürün bulanamdı: " + id));

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
                        .filter(ProductImage::getIsPrimary)
                        .map(ProductImage::getImageUrl)
                        .findFirst()
                        .orElse(null),
                product.getStockQuantity(),
                product.getUnit(),
                product.isInStock(),
                product.isExpired(),
                defaultPrice.map(ProductPrice::getAmount).orElse(null),
                currency.name(),
                defaultPrice.map(ProductPrice::isValidNow).orElse(false),
                product.getExpiryDate(),
                false
        );
    }

    private ProductWithPriceResponse mapToProductWithPrice(Product product, Long dealerId, CurrencyType currency) {
        // Bu dealer için tüm fiyat tiplerini al
        List<ProductPrice> dealerPrices = productPriceRepository.findByProductIdAndDealerIdAndStatus(
                product.getId(), dealerId, EntityStatus.ACTIVE);

        List<ProductPriceSummary> priceSummaries = dealerPrices.stream()
                .map(price -> new ProductPriceSummary(
                        price.getId(),
                        price.getProductVariant() != null ? price.getProductVariant().getDisplayName() : product.getName(),
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
                        .filter(ProductImage::getIsPrimary)
                        .map(ProductImage::getImageUrl)
                        .findFirst()
                        .orElse(null),
                product.isInStock(),
                product.isExpired(),
                priceSummaries,
                defaultPrice,
                currency.name(),
                product.getCreatedAt(),
                product.getStatus().name(),
                false
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

    public Page<ProductSummary> searchByField(String fieldName, String searchValue, boolean exactMatch,
                                              int page, int size, String sortBy, String sortDirection, Authentication authentication) {
        logger.info("Field search - field: " + fieldName + ", value: " + searchValue +
                ", exact: " + exactMatch);

        // Field name validation
        if (!isValidSearchField(fieldName)) {
            throw new IllegalArgumentException("Geçersiz field adı: " + fieldName +
                    ". Geçerli field'lar: " + getValidFieldNames());
        }

        // Mevcut kullanıcıyı al
        AppUser currentUser = appUserService.getCurrentUser(authentication);

        // Kullanıcının favori ürün ID'lerini al
        Set<Long> favoriteProductIds = userFavoriteRepository.findUserFavoriteProductIds(
                        currentUser.getId(), EntityStatus.ACTIVE)
                .stream().collect(Collectors.toSet());

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = executeFieldSearch(fieldName, searchValue, exactMatch, pageable);

        return getProductSummariesWithPrices(favoriteProductIds, currentUser, pageable, products);
    }

    public Page<ProductSummary> getPopularProducts(int page, int size, String sortBy, String sortDirection,
                                                   int daysPeriod, Authentication authentication) {
        logger.info("Fetching popular products - page: " + page + ", size: " + size +
                ", period: " + daysPeriod + " days");

        // Mevcut kullanıcıyı al
        AppUser currentUser = appUserService.getCurrentUser(authentication);

        // Kullanıcının favori ürün ID'lerini al
        Set<Long> favoriteProductIds = getUserFavoriteProductIds(currentUser.getId());

        // orderCount Product entity'sinde olmadığı için varsayılan sıralama kullan
        String actualSortBy = sortBy.equals("orderCount") ? "name" : sortBy;
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), actualSortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        LocalDateTime fromDate = LocalDateTime.now().minusDays(daysPeriod);

        try {
            // Order entity'si varsa sipariş sayısına göre sırala
            Page<Object[]> popularResults = productRepository.findPopularProducts(
                    EntityStatus.ACTIVE, fromDate, pageable);

            List<ProductSummary> summaries = popularResults.getContent().stream()
                    .map(result -> {
                        Product product = (Product) result[0];
                        // orderCount'u log için kullanabiliriz veya gelecekte DTO'ya ekleyebiliriz
                        Long orderCount = result[1] != null ? (Long) result[1] : 0L;
                        logger.info("Product: " + product.getName() + " - Order count: " + orderCount);

                        ProductSummary summary = productMapper.toSummary(product);

                        // Fiyat bilgilerini al
                       // List<ProductPriceInfo> priceInfos = getPriceInfosForUser(product.getId(), currentUser);

                        return new ProductSummary(
                                summary.id(), summary.name(), summary.code(), summary.categoryName(),
                                summary.primaryImageUrl(), summary.stockQuantity(), summary.unit(),
                                summary.isActive(), summary.isInStock(), summary.status(),
                                favoriteProductIds.contains(product.getId())
                        );
                    })
                    .collect(Collectors.toList());

            logger.info("Found " + popularResults.getTotalElements() + " popular products");
            return new PageImpl<>(summaries, pageable, popularResults.getTotalElements());

        } catch (Exception e) {
            // Order entity yoksa stok miktarına göre sırala
            logger.warning("Could not fetch by order count, falling back to stock-based popularity: " + e.getMessage());

            Page<Product> products = productRepository.findPopularProductsByStock(EntityStatus.ACTIVE, pageable);

            return getProductSummariesWithPrices(favoriteProductIds, currentUser, pageable, products);
        }
    }

    private Page<Product> executeFieldSearch(String fieldName, String searchValue,
                                             boolean exactMatch, Pageable pageable) {
        EntityStatus status = EntityStatus.ACTIVE;

        return switch (fieldName.toLowerCase()) {
            case "code" -> exactMatch ?
                    productRepository.findByCodeExact(searchValue, status, pageable) :
                    productRepository.findByCodePartial(searchValue, status, pageable);

            case "name" -> exactMatch ?
                    productRepository.findByNameExact(searchValue, status, pageable) :
                    productRepository.findByNamePartial(searchValue, status, pageable);

            case "material" -> exactMatch ?
                    productRepository.findByMaterialExact(searchValue, status, pageable) :
                    productRepository.findByMaterialPartial(searchValue, status, pageable);

            case "barcode" -> productRepository.findByBarcodeExact(searchValue, status, pageable);

            case "lotnumber" -> productRepository.findByLotNumberExact(searchValue, status, pageable);

            case "serialnumber" -> productRepository.findBySerialNumberExact(searchValue, status, pageable);

            case "stockquantity" -> {
                try {
                    Integer stockValue = Integer.parseInt(searchValue);
                    yield productRepository.findByStockQuantityExact(stockValue, status, pageable);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Stock quantity numeric değer olmalıdır: " + searchValue);
                }
            }

            case "sterile" -> {
                Boolean sterileValue = Boolean.parseBoolean(searchValue);
                yield productRepository.findBySterileExact(sterileValue, status, pageable);
            }

            case "implantable" -> {
                Boolean implantableValue = Boolean.parseBoolean(searchValue);
                yield productRepository.findByImplantableExact(implantableValue, status, pageable);
            }

            default -> throw new IllegalArgumentException("Desteklenmeyen field: " + fieldName);
        };
    }

    private boolean isValidSearchField(String fieldName) {
        Set<String> validFields = Set.of(
                "code", "name", "material", "barcode", "lotnumber", "serialnumber",
                "stockquantity", "sterile", "implantable"
        );
        return validFields.contains(fieldName.toLowerCase());
    }

    private String getValidFieldNames() {
        return "code, name, material, barcode, lotNumber, serialNumber, stockQuantity, sterile, implantable";
    }

    public List<ProductSearchField> getSearchableFields() {
        return List.of(
                new ProductSearchField("code", "Ürün Kodu", "STRING", true, "TI-001"),
                new ProductSearchField("name", "Ürün Adı", "STRING", true, "Titanyum İmplant"),
                new ProductSearchField("material", "Malzeme", "STRING", true, "Titanyum"),
                new ProductSearchField("barcode", "Barkod", "STRING", false, "1234567890123"),
                new ProductSearchField("lotNumber", "Lot Numarası", "STRING", false, "LOT-2024-001"),
                new ProductSearchField("serialNumber", "Seri Numarası", "STRING", false, "SN-2024-001"),
                new ProductSearchField("stockQuantity", "Stok Miktarı", "INTEGER", false, "100"),
                new ProductSearchField("sterile", "Steril mi?", "BOOLEAN", false, "true"),
                new ProductSearchField("implantable", "İmplant mı?", "BOOLEAN", false, "true")
        );
    }

    /**
     * Resmi olmayan ürünleri getir
     */
    public Page<ProductSummary> getProductsWithoutImages(int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching products without images - page: " + page + ", size: " + size);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findProductsWithoutImages(EntityStatus.ACTIVE, pageable);

        logger.info("Found " + products.getTotalElements() + " products without images");
        return products.map(productMapper::toSummary);
    }

    /**
     * Resmi olmayan ürün sayısını getir (istatistik için)
     */
    public Long countProductsWithoutImages() {
        return productRepository.countProductsWithoutImages(EntityStatus.ACTIVE);
    }

    private boolean checkIsFavorite(Long productId, Long userId) {
        return userId != null && userFavoriteRepository.findByUserIdAndProductIdAndStatus(
                userId, productId, EntityStatus.ACTIVE).isPresent();
    }

    // Helper metod - toplu kontrol için:
    private Set<Long> getUserFavoriteProductIds(Long userId) {
        if (userId == null) return Set.of();
        return userFavoriteRepository.findUserFavoriteProductIds(userId, EntityStatus.ACTIVE)
                .stream().collect(Collectors.toSet());
    }

    public ProductStatistics getProductStatistics() {
        logger.info("Fetching product statistics");

        Long totalProducts = productRepository.countByStatus(EntityStatus.ACTIVE);
        Long inStockProducts = productRepository.countInStockProducts(EntityStatus.ACTIVE);
        Long outOfStockProducts = productRepository.countOutOfStockProducts(EntityStatus.ACTIVE);
        Long expiredProducts = productRepository.countExpiredProducts(EntityStatus.ACTIVE);
        Long productsWithoutImages = productRepository.countProductsWithoutImages(EntityStatus.ACTIVE);

        return new ProductStatistics(
                totalProducts,
                inStockProducts,
                outOfStockProducts,
                expiredProducts,
                productsWithoutImages
        );
    }

    /**
     * Belirtilen parent kategorinin tüm alt kategori ID'lerini recursive olarak getirir
     */
    private List<Long> getAllChildCategoryIds(Long parentCategoryId) {
        List<Long> allChildIds = new ArrayList<>();
        collectChildCategoryIds(parentCategoryId, allChildIds);

        logger.info("Found " + allChildIds.size() + " child category IDs for parent: " + parentCategoryId);
        return allChildIds;
    }

    /**
     * Recursive olarak alt kategori ID'lerini toplar
     */
    private void collectChildCategoryIds(Long parentId, List<Long> allChildIds) {
        // Bu parent'ın direkt child'larını al
        List<Category> directChildren = categoryRepository.findByParentCategoryIdAndStatusOrderByNameAsc(
                parentId, EntityStatus.ACTIVE);

        for (Category child : directChildren) {
            // Child'ın ID'sini ekle
            allChildIds.add(child.getId());

            // Bu child'ın da child'ları varsa recursive çağır
            collectChildCategoryIds(child.getId(), allChildIds);
        }
    }

    /**
     * Kategorinin leaf kategori olup olmadığını kontrol eder
     * Parent kategorilere ürün eklenemez, sadece leaf kategorilere ürün eklenebilir
     */
    private void validateLeafCategory(Long categoryId) {
        logger.info("Validating if category is leaf category: " + categoryId);

        Category category = categoryService.getCategoryEntityById(categoryId);

        // Category'nin child'ları var mı kontrol et
        List<Category> children = categoryRepository.findByParentCategoryIdAndStatusOrderByNameAsc(
                categoryId, EntityStatus.ACTIVE);

        if (!children.isEmpty()) {
            throw new BadCredentialsException(
                    "Ana kategorilere ürün eklenemez. Lütfen alt kategori seçiniz. " +
                            "Seçilen kategori: " + category.getName() + " (" + children.size() + " alt kategori mevcut)"
            );
        }

        logger.info("Category validation passed - it's a leaf category");
    }

    /**
     * Ürün variant'larını kullanıcıya göre filtrele ve map et
     * Eğer kullanıcının dealer'ı varsa, sadece o dealer'ın fiyatlarını göster
     */
    private List<ProductVariantDTO> getVariantsForProduct(Product product, AppUser user) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return Collections.emptyList();
        }

        Long dealerId = null;
        boolean includePrices = false;

        if (user != null) {
            boolean isAdminUser = isAdminUser(user);

            if (!isAdminUser && user.getDealer() != null) {
                boolean hasProductPricePermission = user.getRoles().stream()
                        .flatMap(role -> role.getPermissions().stream())
                        .anyMatch(permission -> "PRICE_READ".equals(permission.getName()));

                if (hasProductPricePermission) {
                    dealerId = user.getDealer().getId();
                    includePrices = true;
                }
            }
        }

        final boolean finalIncludePrices = includePrices;
        final Long finalDealerId = includePrices ? dealerId : null;
        final CurrencyType finalCurrency = includePrices && user.getDealer() != null
                ? user.getDealer().getPreferredCurrency()
                : null;

        return product.getVariants().stream()
                .map(variant -> productVariantMapper.toDto(variant, finalIncludePrices, finalDealerId, finalCurrency))
                .collect(Collectors.toList());
    }

    private boolean isAdminUser(AppUser user) {
        if (user == null || user.getRoles() == null || user.getRoles().isEmpty()) {
            return false;
        }

        Set<String> adminRoles = Set.of("ADMIN", "SYSTEM_ADMIN", "SUPER_ADMIN");

        return user.getRoles().stream()
                .map(Role::getName)
                .filter(Objects::nonNull)
                .anyMatch(adminRoles::contains);
    }
}

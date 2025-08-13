package com.maxx_global.service;

import com.maxx_global.dto.product.*;
import com.maxx_global.entity.Category;
import com.maxx_global.entity.Product;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final Logger logger = Logger.getLogger(ProductService.class.getName());

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    // Facade Pattern - Diğer servisleri çağır
    private final CategoryService categoryService;

    public ProductService(ProductRepository productRepository,
                          ProductMapper productMapper,
                          CategoryService categoryService) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.categoryService = categoryService;
    }

    // Tüm ürünleri getir (sayfalama ile)
    public Page<ProductResponse> getAllProducts(int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching all products - page: " + page + ", size: " + size +
                ", sortBy: " + sortBy + ", direction: " + sortDirection);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE, pageable);
        return products.map(productMapper::toDto);
    }

    // Aktif ürünleri getir
    public List<ProductResponse> getActiveProducts() {
        logger.info("Fetching active products");
        List<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);
        return products.stream()
                .map(productMapper::toDto)
                .collect(Collectors.toList());
    }

    // Ürün özetleri (dropdown için)
    public List<ProductSummary> getProductSummaries() {
        logger.info("Fetching product summaries");
        List<Product> products = productRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);
        return products.stream()
                .map(productMapper::toSummary)
                .collect(Collectors.toList());
    }

    // Stokta olan ürünler
    public Page<ProductResponse> getInStockProducts(int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching in-stock products");

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findInStockProducts(EntityStatus.ACTIVE, pageable);
        return products.map(productMapper::toDto);
    }

    // ID ile ürün getir
    public ProductResponse getProductById(Long id) {
        logger.info("Fetching product with id: " + id);
        Product product = productRepository.findByIdWithImages(id, EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));
        return productMapper.toDto(product);
    }

    // ProductSummary getir (ProductPrice service için)
    public ProductSummary getProductSummary(Long id) {
        logger.info("Fetching product summary with id: " + id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));
        return productMapper.toSummary(product);
    }

    // Kategoriye göre ürünler
    public Page<ProductResponse> getProductsByCategory(Long categoryId, int page, int size,
                                                       String sortBy, String sortDirection) {
        logger.info("Fetching products for category: " + categoryId);

        // Facade Pattern - CategoryService üzerinden kontrol
        categoryService.getCategoryById(categoryId); // Varlık kontrolü

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findByCategoryIdAndStatusOrderByNameAsc(
                categoryId, EntityStatus.ACTIVE, pageable);
        return products.map(productMapper::toDto);
    }

    // Genel arama
    public Page<ProductResponse> searchProducts(String searchTerm, int page, int size,
                                                String sortBy, String sortDirection) {
        logger.info("Searching products with term: " + searchTerm);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.searchProducts(searchTerm, EntityStatus.ACTIVE, pageable);
        return products.map(productMapper::toDto);
    }

    // Gelişmiş arama
    public Page<ProductResponse> advancedSearch(ProductSearchCriteria criteria, int page, int size,
                                                String sortBy, String sortDirection) {
        logger.info("Advanced search with criteria");

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findByAdvancedCriteria(
                criteria.searchTerm(),
                criteria.categoryIds() != null && !criteria.categoryIds().isEmpty() ? criteria.categoryIds().get(0) : null,
                criteria.materials() != null && !criteria.materials().isEmpty() ? criteria.materials().get(0) : null,
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
                criteria.inStockOnly() != null ? criteria.inStockOnly() : false,
                criteria.includeExpired() != null ? criteria.includeExpired() : true,
                EntityStatus.ACTIVE,
                pageable
        );

        return products.map(productMapper::toDto);
    }

    // Yeni ürün oluştur
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        logger.info("Creating new product: " + request.name());

        // Custom validation
        request.validate();

        // Facade Pattern - CategoryService üzerinden kontrol
        categoryService.getCategoryById(request.categoryId());

        // Ürün kodu benzersizlik kontrolü
        if (productRepository.existsByCodeAndStatus(request.code(), EntityStatus.ACTIVE)) {
            throw new BadCredentialsException("Product code already exists: " + request.code());
        }

        Product product = productMapper.toEntity(request);
        product.setStatus(EntityStatus.ACTIVE);

        // ✅ CATEGORY SET ET - Bu eksikti!
        Category category = new Category();
        category.setId(request.categoryId());
        product.setCategory(category);

        // Default değerler
        if (product.getStockQuantity() == null) {
            product.setStockQuantity(0);
        }
        if (product.getMinimumOrderQuantity() == null) {
            product.setMinimumOrderQuantity(1);
        }
        if (product.getMaximumOrderQuantity() == null) {
            product.setMaximumOrderQuantity(1000);
        }

        Product savedProduct = productRepository.save(product);
        logger.info("Product created successfully with id: " + savedProduct.getId());

        return productMapper.toDto(savedProduct);
    }

    // Ürün güncelle
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        logger.info("Updating product with id: " + id);

        request.validate();

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));

        // Ürün kodu benzersizlik kontrolü (mevcut ürün hariç)
        if (productRepository.existsByCodeAndStatusAndIdNot(request.code(), EntityStatus.ACTIVE, id)) {
            throw new BadCredentialsException("Product code already exists: " + request.code());
        }

        // Facade Pattern - CategoryService üzerinden kontrol
        categoryService.getCategoryById(request.categoryId());

        // Güncelleme işlemi
        productMapper.updateEntity(existingProduct, request);

        Product updatedProduct = productRepository.save(existingProduct);
        logger.info("Product updated successfully with id: " + updatedProduct.getId());

        return productMapper.toDto(updatedProduct);
    }

    // Stok güncelle
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
        logger.info("Stock updated successfully");

        return productMapper.toDto(updatedProduct);
    }

    // Ürün sil (soft delete)
    @Transactional
    public void deleteProduct(Long id) {
        logger.info("Deleting product with id: " + id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));

        // İş kuralı: Fiyatı olan ürün silinemez (opsiyonel kontrol)
        // Bu kontrolü ProductPrice service'den yapabiliriz

        product.setStatus(EntityStatus.DELETED);
        productRepository.save(product);

        logger.info("Product deleted successfully with id: " + id);
    }

    // Ürün geri yükle
    @Transactional
    public ProductResponse restoreProduct(Long id) {
        logger.info("Restoring product with id: " + id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));

        product.setStatus(EntityStatus.ACTIVE);

        Product restoredProduct = productRepository.save(product);
        logger.info("Product restored successfully with id: " + id);

        return productMapper.toDto(restoredProduct);
    }

    // Süresi dolan ürünler
    public List<ProductResponse> getExpiredProducts() {
        logger.info("Fetching expired products");
        List<Product> expiredProducts = productRepository.findExpiredProducts(EntityStatus.ACTIVE);
        return expiredProducts.stream()
                .map(productMapper::toDto)
                .collect(Collectors.toList());
    }

    // Yakında süresi dolacak ürünler
    public List<ProductResponse> getProductsExpiringInDays(int days) {
        logger.info("Fetching products expiring in " + days + " days");
        LocalDate futureDate = LocalDate.now().plusDays(days);
        List<Product> expiringProducts = productRepository.findProductsExpiringBefore(futureDate, EntityStatus.ACTIVE);
        return expiringProducts.stream()
                .map(productMapper::toDto)
                .collect(Collectors.toList());
    }

    // Düşük stok ürünleri
    public List<ProductResponse> getLowStockProducts(Integer threshold) {
        logger.info("Fetching low stock products with threshold: " + threshold);
        List<Product> lowStockProducts = productRepository.findLowStockProducts(threshold, EntityStatus.ACTIVE);
        return lowStockProducts.stream()
                .map(productMapper::toDto)
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

    // Filter dropdown verileri
    public List<String> getDistinctMaterials() {
        return productRepository.findDistinctMaterials(EntityStatus.ACTIVE);
    }

    public List<String> getDistinctUnits() {
        return productRepository.findDistinctUnits(EntityStatus.ACTIVE);
    }

    public List<String> getDistinctMedicalDeviceClasses() {
        return productRepository.findDistinctMedicalDeviceClasses(EntityStatus.ACTIVE);
    }

    // İstatistikler
    public ProductStatistics getProductStatistics() {
        logger.info("Fetching product statistics");

        Long totalProducts = productRepository.countByStatus(EntityStatus.ACTIVE);
        Long inStockProducts = productRepository.countInStockProducts(EntityStatus.ACTIVE);
        Long outOfStockProducts = productRepository.countOutOfStockProducts(EntityStatus.ACTIVE);
        Long expiredProducts = productRepository.countExpiredProducts(EntityStatus.ACTIVE);

        return new ProductStatistics(totalProducts, inStockProducts, outOfStockProducts, expiredProducts);
    }

    // İstatistik için yardımcı record
    public record ProductStatistics(
            Long totalProducts,
            Long inStockProducts,
            Long outOfStockProducts,
            Long expiredProducts
    ) {}
}
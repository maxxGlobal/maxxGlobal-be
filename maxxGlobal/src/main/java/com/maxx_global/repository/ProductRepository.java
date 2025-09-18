package com.maxx_global.repository;

import com.maxx_global.entity.Product;
import com.maxx_global.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Aktif ürünleri getir
    Page<Product> findByStatus(EntityStatus status, Pageable pageable);

    List<Product> findByStatusOrderByNameAsc(EntityStatus status);

    @Query("SELECT p FROM Product p WHERE p.status IN :statuses ORDER BY p.name ASC")
    List<Product> findByStatusesOrderByNameAsc(List<EntityStatus> statuses);

    // Kategoriye göre ürünler
    Page<Product> findByCategoryIdAndStatusOrderByNameAsc(Long categoryId, EntityStatus status, Pageable pageable);
    List<Product> findByCategoryIdAndStatusOrderByNameAsc(Long categoryId, EntityStatus status);

    // Ürün kodu ile arama (unique kontrolü için)
    Optional<Product> findByCodeAndStatus(String code, EntityStatus status);
    boolean existsByCodeAndStatus(String code, EntityStatus status);
    boolean existsByCodeAndStatusAndIdNot(String code, EntityStatus status, Long id);

    // Genel arama (name, code, description)
    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(p.code) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY p.name ASC")
    Page<Product> searchProducts(@Param("searchTerm") String searchTerm,
                                 @Param("status") EntityStatus status,
                                 Pageable pageable);

    // Stok kontrolü ile ürünler
    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND p.stockQuantity > 0 ORDER BY p.name ASC")
    Page<Product> findInStockProducts(@Param("status") EntityStatus status, Pageable pageable);

    // Süresi dolan ürünler
    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND p.expiryDate < CURRENT_DATE ORDER BY p.expiryDate ASC")
    List<Product> findExpiredProducts(@Param("status") EntityStatus status);

    // Yakında süresi dolacak ürünler
    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND p.expiryDate BETWEEN CURRENT_DATE AND :date " +
            "ORDER BY p.expiryDate ASC")
    List<Product> findProductsExpiringBefore(@Param("date") LocalDate date,
                                             @Param("status") EntityStatus status);

    Page<Product> findByCategoryIdInAndStatus(List<Long> categoryIds, EntityStatus status, Pageable pageable);

    // Düşük stok ürünleri
    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND p.stockQuantity <= :threshold ORDER BY p.stockQuantity ASC")
    List<Product> findLowStockProducts(@Param("threshold") Integer threshold,
                                       @Param("status") EntityStatus status);

    // Malzeme bazında arama
    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND LOWER(p.material) LIKE LOWER(CONCAT('%', :material, '%')) " +
            "ORDER BY p.name ASC")
    Page<Product> findByMaterial(@Param("material") String material,
                                 @Param("status") EntityStatus status,
                                 Pageable pageable);

    // Özellik bazında filtreleme
    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND (:sterile IS NULL OR p.sterile = :sterile) " +
            "AND (:implantable IS NULL OR p.implantable = :implantable) " +
            "AND (:ceMarking IS NULL OR p.ceMarking = :ceMarking) " +
            "AND (:fdaApproved IS NULL OR p.fdaApproved = :fdaApproved) " +
            "ORDER BY p.name ASC")
    Page<Product> findByFeatures(@Param("sterile") Boolean sterile,
                                 @Param("implantable") Boolean implantable,
                                 @Param("ceMarking") Boolean ceMarking,
                                 @Param("fdaApproved") Boolean fdaApproved,
                                 @Param("status") EntityStatus status,
                                 Pageable pageable);

    // Ağırlık aralığında arama
    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND (:minWeight IS NULL OR p.weightGrams >= :minWeight) " +
            "AND (:maxWeight IS NULL OR p.weightGrams <= :maxWeight) " +
            "ORDER BY p.name ASC")
    Page<Product> findByWeightRange(@Param("minWeight") BigDecimal minWeight,
                                    @Param("maxWeight") BigDecimal maxWeight,
                                    @Param("status") EntityStatus status,
                                    Pageable pageable);

    // Gelişmiş arama (çoklu kriter)
    // ProductRepository.java - Sadece problematik metodu değiştiriyoruz

    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND (:searchTerm IS NULL OR :searchTerm = '' OR " +
            "    LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "    LOWER(p.code) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "    LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:material IS NULL OR :material = '' OR LOWER(p.material) LIKE LOWER(CONCAT('%', :material, '%'))) " +
            "AND (:sterile IS NULL OR p.sterile = :sterile) " +
            "AND (:implantable IS NULL OR p.implantable = :implantable) " +
            "AND (:ceMarking IS NULL OR p.ceMarking = :ceMarking) " +
            "AND (:fdaApproved IS NULL OR p.fdaApproved = :fdaApproved) " +
            "AND (:minWeight IS NULL OR p.weightGrams >= :minWeight) " +
            "AND (:maxWeight IS NULL OR p.weightGrams <= :maxWeight) " +
            "AND (:expiryDateFrom IS NULL OR p.expiryDate >= :expiryDateFrom) " +
            "AND (:expiryDateTo IS NULL OR p.expiryDate <= :expiryDateTo) " +
            "AND (:minStock IS NULL OR p.stockQuantity >= :minStock) " +
            "AND (:maxStock IS NULL OR p.stockQuantity <= :maxStock) " +
            "AND (COALESCE(:inStockOnly, false) = false OR p.stockQuantity > 0) " +
            "AND (COALESCE(:includeExpired, true) = true OR p.expiryDate IS NULL OR p.expiryDate >= CURRENT_DATE) " +
            "ORDER BY p.name ASC")
    Page<Product> findByAdvancedCriteria(@Param("searchTerm") String searchTerm,
                                         @Param("categoryId") Long categoryId,
                                         @Param("material") String material,
                                         @Param("sterile") Boolean sterile,
                                         @Param("implantable") Boolean implantable,
                                         @Param("ceMarking") Boolean ceMarking,
                                         @Param("fdaApproved") Boolean fdaApproved,
                                         @Param("minWeight") BigDecimal minWeight,
                                         @Param("maxWeight") BigDecimal maxWeight,
                                         @Param("expiryDateFrom") LocalDate expiryDateFrom,
                                         @Param("expiryDateTo") LocalDate expiryDateTo,
                                         @Param("minStock") Integer minStock,
                                         @Param("maxStock") Integer maxStock,
                                         @Param("inStockOnly") Boolean inStockOnly,
                                         @Param("includeExpired") Boolean includeExpired,
                                         @Param("status") EntityStatus status,
                                         Pageable pageable);

    // İstatistikler
    @Query("SELECT COUNT(p) FROM Product p WHERE p.status = :status")
    Long countByStatus(@Param("status") EntityStatus status);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.status = :status AND p.stockQuantity > 0")
    Long countInStockProducts(@Param("status") EntityStatus status);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.status = :status AND p.stockQuantity = 0")
    Long countOutOfStockProducts(@Param("status") EntityStatus status);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.status = :status " +
            "AND p.expiryDate < CURRENT_DATE")
    Long countExpiredProducts(@Param("status") EntityStatus status);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.category.id = :categoryId AND p.status = :status")
    Long countByCategoryAndStatus(@Param("categoryId") Long categoryId, @Param("status") EntityStatus status);

    // Benzersiz değer listeleri (filter dropdown'ları için)
    @Query("SELECT DISTINCT p.material FROM Product p WHERE p.status = :status " +
            "AND p.material IS NOT NULL ORDER BY p.material ASC")
    List<String> findDistinctMaterials(@Param("status") EntityStatus status);

    @Query("SELECT DISTINCT p.unit FROM Product p WHERE p.status = :status " +
            "AND p.unit IS NOT NULL ORDER BY p.unit ASC")
    List<String> findDistinctUnits(@Param("status") EntityStatus status);

    @Query("SELECT DISTINCT p.medicalDeviceClass FROM Product p WHERE p.status = :status " +
            "AND p.medicalDeviceClass IS NOT NULL ORDER BY p.medicalDeviceClass ASC")
    List<String> findDistinctMedicalDeviceClasses(@Param("status") EntityStatus status);

    // Resim bilgisi ile birlikte getir (LEFT JOIN FETCH)
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.images WHERE p.id = :id AND p.status = :status")
    Optional<Product> findByIdWithImages(@Param("id") Long id, @Param("status") EntityStatus status);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.images WHERE p.id = :id")
    Optional<Product> findByIdWithImages(@Param("id") Long id);

    @Query(value = "SELECT * FROM products WHERE status = :#{#status.name()} " +
            "AND stock_quantity > 0 ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Product> findRandomProducts(@Param("status") EntityStatus status, @Param("limit") int limit);

    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND LOWER(p.code) = LOWER(:value) ORDER BY p.name ASC")
    Page<Product> findByCodeExact(@Param("value") String value,
                                  @Param("status") EntityStatus status,
                                  Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND LOWER(p.code) LIKE LOWER(CONCAT('%', :value, '%')) ORDER BY p.name ASC")
    Page<Product> findByCodePartial(@Param("value") String value,
                                    @Param("status") EntityStatus status,
                                    Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND LOWER(p.name) = LOWER(:value) ORDER BY p.name ASC")
    Page<Product> findByNameExact(@Param("value") String value,
                                  @Param("status") EntityStatus status,
                                  Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND LOWER(p.name) LIKE LOWER(CONCAT('%', :value, '%')) ORDER BY p.name ASC")
    Page<Product> findByNamePartial(@Param("value") String value,
                                    @Param("status") EntityStatus status,
                                    Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND LOWER(p.material) = LOWER(:value) ORDER BY p.name ASC")
    Page<Product> findByMaterialExact(@Param("value") String value,
                                      @Param("status") EntityStatus status,
                                      Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND LOWER(p.material) LIKE LOWER(CONCAT('%', :value, '%')) ORDER BY p.name ASC")
    Page<Product> findByMaterialPartial(@Param("value") String value,
                                        @Param("status") EntityStatus status,
                                        Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND LOWER(p.barcode) = LOWER(:value) ORDER BY p.name ASC")
    Page<Product> findByBarcodeExact(@Param("value") String value,
                                     @Param("status") EntityStatus status,
                                     Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND LOWER(p.lotNumber) = LOWER(:value) ORDER BY p.name ASC")
    Page<Product> findByLotNumberExact(@Param("value") String value,
                                       @Param("status") EntityStatus status,
                                       Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND LOWER(p.serialNumber) = LOWER(:value) ORDER BY p.name ASC")
    Page<Product> findBySerialNumberExact(@Param("value") String value,
                                          @Param("status") EntityStatus status,
                                          Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND p.stockQuantity = :value ORDER BY p.name ASC")
    Page<Product> findByStockQuantityExact(@Param("value") Integer value,
                                           @Param("status") EntityStatus status,
                                           Pageable pageable);

    // Boolean field'lar için
    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND p.sterile = :value ORDER BY p.name ASC")
    Page<Product> findBySterileExact(@Param("value") Boolean value,
                                     @Param("status") EntityStatus status,
                                     Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND p.implantable = :value ORDER BY p.name ASC")
    Page<Product> findByImplantableExact(@Param("value") Boolean value,
                                         @Param("status") EntityStatus status,
                                         Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status " +
            "AND (p.images IS EMPTY OR SIZE(p.images) = 0) " +
            "ORDER BY p.name ASC")
    Page<Product> findProductsWithoutImages(@Param("status") EntityStatus status, Pageable pageable);

    /**
     * Resmi olmayan ürün sayısını getir (istatistik için)
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.status = :status " +
            "AND (p.images IS EMPTY OR SIZE(p.images) = 0)")
    Long countProductsWithoutImages(@Param("status") EntityStatus status);

    @Query("SELECT p, COALESCE(COUNT(oi), 0) as orderCount " +
            "FROM Product p " +
            "LEFT JOIN OrderItem oi ON oi.product.id = p.id " +
            "LEFT JOIN oi.order o " +
            "WHERE p.status = :status " +
            "AND p.stockQuantity > 0 " +
            "AND (o.orderDate >= :fromDate OR o.orderDate IS NULL) " +
            "GROUP BY p.id " +
            "ORDER BY COUNT(oi) DESC, p.name ASC")
    Page<Object[]> findPopularProducts(@Param("status") EntityStatus status,
                                       @Param("fromDate") LocalDateTime fromDate,
                                       Pageable pageable);

    // Fallback query - Order entity yoksa bu kullanılır
    @Query("SELECT p FROM Product p " +
            "WHERE p.status = :status " +
            "AND p.stockQuantity > 0 " +
            "ORDER BY p.stockQuantity DESC, p.name ASC")
    Page<Product> findPopularProductsByStock(@Param("status") EntityStatus status, Pageable pageable);

}
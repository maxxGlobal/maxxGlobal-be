package com.maxx_global.repository;

import com.maxx_global.entity.ProductVariant;
import com.maxx_global.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    // ==================== BASIC QUERIES ====================

    /**
     * Bir ürünün tüm varyantlarını getir
     */
    List<ProductVariant> findByProductIdAndStatusOrderBySizeAsc(Long productId, EntityStatus status);

    /**
     * Bir ürünün tüm varyantlarını getir (sayfalama ile)
     */
    Page<ProductVariant> findByProductIdAndStatus(Long productId, EntityStatus status, Pageable pageable);

    /**
     * SKU ile variant bul
     */
    Optional<ProductVariant> findBySkuAndStatus(String sku, EntityStatus status);

    /**
     * SKU varlık kontrolü
     */
    boolean existsBySkuAndStatus(String sku, EntityStatus status);

    /**
     * SKU varlık kontrolü (kendi ID'si hariç - güncelleme için)
     */
    boolean existsBySkuAndStatusAndIdNot(String sku, EntityStatus status, Long id);

    /**
     * Bir ürünün default variant'ını bul
     */
    Optional<ProductVariant> findByProductIdAndIsDefaultTrueAndStatus(Long productId, EntityStatus status);

    // ==================== STOCK QUERIES ====================

    /**
     * Stokta olan varyantları getir
     */
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.status = :status " +
            "AND pv.stockQuantity > 0 ORDER BY pv.product.name ASC, pv.size ASC")
    List<ProductVariant> findInStockVariants(@Param("status") EntityStatus status);

    /**
     * Bir ürünün stokta olan varyantlarını getir
     */
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId " +
            "AND pv.status = :status AND pv.stockQuantity > 0 " +
            "ORDER BY pv.size ASC")
    List<ProductVariant> findInStockVariantsByProduct(@Param("productId") Long productId,
                                                      @Param("status") EntityStatus status);

    /**
     * Düşük stok varyantlarını getir
     */
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.status = :status " +
            "AND pv.stockQuantity > 0 AND pv.stockQuantity <= :threshold " +
            "ORDER BY pv.stockQuantity ASC, pv.product.name ASC")
    List<ProductVariant> findLowStockVariants(@Param("threshold") Integer threshold,
                                              @Param("status") EntityStatus status);

    /**
     * Stok tükenen varyantları getir
     */
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.status = :status " +
            "AND pv.stockQuantity = 0 ORDER BY pv.product.name ASC, pv.size ASC")
    List<ProductVariant> findOutOfStockVariants(@Param("status") EntityStatus status);

    // ==================== SIZE QUERIES ====================

    /**
     * Belirli bir size'daki tüm varyantları getir
     */
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.status = :status " +
            "AND LOWER(pv.size) = LOWER(:size) ORDER BY pv.product.name ASC")
    List<ProductVariant> findBySize(@Param("size") String size, @Param("status") EntityStatus status);

    /**
     * Bir ürünün belirli size'ındaki varyantını bul
     */
    Optional<ProductVariant> findByProductIdAndSizeAndStatus(Long productId, String size, EntityStatus status);

    /**
     * Benzersiz size listesi getir (filter için)
     */
    @Query("SELECT DISTINCT pv.size FROM ProductVariant pv WHERE pv.status = :status " +
            "ORDER BY pv.size ASC")
    List<String> findDistinctSizes(@Param("status") EntityStatus status);

    /**
     * Bir ürünün kaç varyantı var
     */
    Long countByProductIdAndStatus(Long productId, EntityStatus status);

    // ==================== BARCODE QUERIES ====================

    /**
     * Barkod ile variant bul
     */
    Optional<ProductVariant> findByBarcodeAndStatus(String barcode, EntityStatus status);

    /**
     * Barkod varlık kontrolü
     */
    boolean existsByBarcodeAndStatus(String barcode, EntityStatus status);

    // ==================== ADVANCED QUERIES ====================

    /**
     * Birden fazla variant'ı ID listesine göre getir
     */
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.id IN :variantIds " +
            "AND pv.status = :status ORDER BY pv.product.name ASC, pv.size ASC")
    List<ProductVariant> findByIdsAndStatus(@Param("variantIds") List<Long> variantIds,
                                            @Param("status") EntityStatus status);

    /**
     * Birden fazla SKU'ya göre variant getir
     */
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.sku IN :skus " +
            "AND pv.status = :status ORDER BY pv.product.name ASC, pv.size ASC")
    List<ProductVariant> findBySkusAndStatus(@Param("skus") List<String> skus,
                                             @Param("status") EntityStatus status);

    /**
     * Kategoriye göre varyantları getir
     */
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.category.id = :categoryId " +
            "AND pv.status = :status ORDER BY pv.product.name ASC, pv.size ASC")
    Page<ProductVariant> findByCategoryId(@Param("categoryId") Long categoryId,
                                          @Param("status") EntityStatus status,
                                          Pageable pageable);

    /**
     * Arama - ürün adı, kodu, SKU'da arama
     */
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.status = :status " +
            "AND (LOWER(pv.product.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(pv.product.code) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(pv.sku) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(pv.size) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY pv.product.name ASC, pv.size ASC")
    Page<ProductVariant> searchVariants(@Param("searchTerm") String searchTerm,
                                        @Param("status") EntityStatus status,
                                        Pageable pageable);

    // ==================== STATISTICS ====================

    /**
     * Toplam varyant sayısı
     */
    Long countByStatus(EntityStatus status);

    /**
     * Stokta olan varyant sayısı
     */
    @Query("SELECT COUNT(pv) FROM ProductVariant pv WHERE pv.status = :status " +
            "AND pv.stockQuantity > 0")
    Long countInStockVariants(@Param("status") EntityStatus status);

    /**
     * Stok tükenen varyant sayısı
     */
    @Query("SELECT COUNT(pv) FROM ProductVariant pv WHERE pv.status = :status " +
            "AND pv.stockQuantity = 0")
    Long countOutOfStockVariants(@Param("status") EntityStatus status);

    /**
     * Bir ürünün toplam stok miktarı (tüm varyantların toplamı)
     */
    @Query("SELECT COALESCE(SUM(pv.stockQuantity), 0) FROM ProductVariant pv " +
            "WHERE pv.product.id = :productId AND pv.status = :status")
    Integer getTotalStockByProduct(@Param("productId") Long productId,
                                   @Param("status") EntityStatus status);

    // ==================== BATCH OPERATIONS ====================

    /**
     * Bir ürünün tüm varyantlarının default flag'ini false yap
     */
    @Query("UPDATE ProductVariant pv SET pv.isDefault = false " +
            "WHERE pv.product.id = :productId AND pv.status = :status")
    void clearDefaultFlagForProduct(@Param("productId") Long productId,
                                    @Param("status") EntityStatus status);

    /**
     * Belirli bir ürünün varyantlarını toplu sil (soft delete)
     */
    @Query("UPDATE ProductVariant pv SET pv.status = 'DELETED' " +
            "WHERE pv.product.id = :productId")
    void softDeleteByProductId(@Param("productId") Long productId);
}
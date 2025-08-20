package com.maxx_global.repository;

import com.maxx_global.entity.ProductPrice;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {

    // Ürün ve bayiye göre fiyat getir
    Optional<ProductPrice> findByProductIdAndDealerIdAndCurrency(
            Long productId, Long dealerId, CurrencyType currency);

    // Ürünün tüm fiyatları (bayiye göre)
    List<ProductPrice> findByProductIdAndDealerIdAndStatus(
            Long productId, Long dealerId, EntityStatus status);

    // Bayinin tüm fiyatları (sayfalama ile)
    Page<ProductPrice> findByDealerIdAndStatus(
            Long dealerId, EntityStatus status, Pageable pageable);

    // Ürünün tüm fiyatları (tüm bayiler)
    List<ProductPrice> findByProductIdAndStatus(
            Long productId, EntityStatus status);

    // Aktif fiyatları getir (geçerlilik tarihi kontrolü ile)
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.product.id = :productId " +
            "AND pp.dealer.id = :dealerId AND pp.currency = :currency " +
            "AND pp.status = :status " +
            "AND pp.isActive = TRUE " +
            "AND (pp.validFrom IS NULL OR pp.validFrom <= CURRENT_TIMESTAMP) " +
            "AND (pp.validUntil IS NULL OR pp.validUntil >= CURRENT_TIMESTAMP)")
    Optional<ProductPrice> findValidPrice(@Param("productId") Long productId,
                                          @Param("dealerId") Long dealerId,
                                          @Param("currency") CurrencyType currency,
                                          @Param("status") EntityStatus status);

    // Bayinin aktif fiyatları
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.dealer.id = :dealerId " +
            "AND pp.status = :status AND pp.isActive = true " +
            "AND (pp.validFrom IS NULL OR pp.validFrom <= CURRENT_TIMESTAMP) " +
            "AND (pp.validUntil IS NULL OR pp.validUntil >= CURRENT_TIMESTAMP) " +
            "ORDER BY pp.product.name ASC")
    Page<ProductPrice> findActivePricesByDealer(@Param("dealerId") Long dealerId,
                                                @Param("status") EntityStatus status,
                                                Pageable pageable);

    // Ürünün bayiler arası fiyat karşılaştırması
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.product.id = :productId " +
            "AND pp.currency = :currency " +
            "AND pp.status = :status AND pp.isActive = true " +
            "ORDER BY pp.amount ASC")
    List<ProductPrice> findPricesForComparison(@Param("productId") Long productId,
                                               @Param("currency") CurrencyType currency,
                                               @Param("status") EntityStatus status);

    // Kategoriye göre fiyatlar
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.product.category.id = :categoryId " +
            "AND pp.dealer.id = :dealerId AND pp.status = :status " +
            "ORDER BY pp.product.name ASC")
    Page<ProductPrice> findByProductCategoryAndDealer(@Param("categoryId") Long categoryId,
                                                      @Param("dealerId") Long dealerId,
                                                      @Param("status") EntityStatus status,
                                                      Pageable pageable);

    // Fiyat arama (ürün adı, kodu ile)
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.dealer.id = :dealerId " +
            "AND pp.status = :status " +
            "AND (LOWER(pp.product.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(pp.product.code) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY pp.product.name ASC")
    Page<ProductPrice> searchPricesByProductInfo(@Param("dealerId") Long dealerId,
                                                 @Param("searchTerm") String searchTerm,
                                                 @Param("status") EntityStatus status,
                                                 Pageable pageable);

    // Süresi dolan fiyatlar
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.validUntil < CURRENT_TIMESTAMP " +
            "AND pp.status = :status ORDER BY pp.validUntil ASC")
    List<ProductPrice> findExpiredPrices(@Param("status") EntityStatus status);

    // Yakında süresI dolacak fiyatlar (kampanyalar için)
    @Query(value = "SELECT * FROM product_prices pp WHERE pp.valid_until BETWEEN NOW() " +
            "AND DATE_ADD(NOW(), INTERVAL :days DAY) " +
            "AND pp.status = :#{#status.name()} ORDER BY pp.valid_until ASC",
            nativeQuery = true)
    List<ProductPrice> findPricesExpiringInDays(@Param("days") int days,
                                                @Param("status") EntityStatus status);

    // Toplu güncelleme için ID'lere göre getir
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.id IN :priceIds AND pp.status = :status")
    List<ProductPrice> findByIdsAndStatus(@Param("priceIds") List<Long> priceIds,
                                          @Param("status") EntityStatus status);

    // Istatistik - bayinin toplam fiyat sayısı
    @Query("SELECT COUNT(pp) FROM ProductPrice pp WHERE pp.dealer.id = :dealerId " +
            "AND pp.status = :status AND pp.isActive = true")
    Long countActivePricesByDealer(@Param("dealerId") Long dealerId,
                                   @Param("status") EntityStatus status);

    // Istatistik - ürünün kaç bayide fiyatı var
    @Query("SELECT COUNT(DISTINCT pp.dealer.id) FROM ProductPrice pp " +
            "WHERE pp.product.id = :productId AND pp.status = :status AND pp.isActive = true")
    Long countDealersWithPriceForProduct(@Param("productId") Long productId,
                                         @Param("status") EntityStatus status);

    /**
     * Batch insert için optimized method
     */
    @Modifying
    @Query("UPDATE ProductPrice p SET p.amount = :amount, p.validFrom = :validFrom, " +
            "p.validUntil = :validUntil, p.isActive = :isActive WHERE p.id = :id")
    int updatePriceFields(@Param("id") Long id,
                          @Param("amount") BigDecimal amount,
                          @Param("validFrom") LocalDateTime validFrom,
                          @Param("validUntil") LocalDateTime validUntil,
                          @Param("isActive") Boolean isActive);

    /**
     * Dealer ve currency'e göre tüm fiyatları getir
     */
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.dealer.id = :dealerId " +
            "AND pp.currency = :currency AND pp.status = :status")
    List<ProductPrice> findByDealerAndCurrency(@Param("dealerId") Long dealerId,
                                               @Param("currency") CurrencyType currency,
                                               @Param("status") EntityStatus status);

    /**
     * Dealer için tüm currency'lerdeki fiyatları getir (Excel export için)
     */
    @Query("SELECT pp FROM ProductPrice pp " +
            "WHERE pp.dealer.id = :dealerId AND pp.status = :status " +
            "ORDER BY pp.product.name ASC, pp.currency ASC")
    List<ProductPrice> findAllByDealerOrderByProduct(@Param("dealerId") Long dealerId,
                                                     @Param("status") EntityStatus status);
}
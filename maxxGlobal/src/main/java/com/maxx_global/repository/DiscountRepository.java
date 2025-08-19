package com.maxx_global.repository;

import com.maxx_global.entity.Discount;
import com.maxx_global.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, Long> {

    // ==================== BASIC QUERIES ====================

    // Aktif indirimleri getir
    @Query("SELECT d FROM Discount d WHERE d.status = :status " +
            "AND d.startDate <= CURRENT_TIMESTAMP " +
            "AND d.endDate >= CURRENT_TIMESTAMP " +
            "ORDER BY d.name ASC")
    List<Discount> findActiveDiscounts(@Param("status") EntityStatus status);

    // İndirim arama (isim bazlı)
    @Query("SELECT d FROM Discount d WHERE d.status = :status " +
            "AND LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "ORDER BY d.name ASC")
    Page<Discount> searchDiscounts(@Param("searchTerm") String searchTerm,
                                   @Param("status") EntityStatus status,
                                   Pageable pageable);

    // ==================== PRODUCT BASED QUERIES ====================

    // Ürüne uygulanabilir aktif indirimleri getir
    @Query("SELECT DISTINCT d FROM Discount d " +
            "JOIN d.applicableProducts p " +
            "WHERE p.id = :productId " +
            "AND d.status = :status " +
            "AND d.startDate <= CURRENT_TIMESTAMP " +
            "AND d.endDate >= CURRENT_TIMESTAMP " +
            "ORDER BY d.discountValue DESC")
    List<Discount> findValidDiscountsForProduct(@Param("productId") Long productId,
                                                @Param("status") EntityStatus status);

    // Ürün ve bayiye uygulanabilir aktif indirimleri getir
    @Query("SELECT DISTINCT d FROM Discount d " +
            "LEFT JOIN d.applicableProducts p " +
            "LEFT JOIN d.applicableDealers dl " +
            "WHERE d.status = :status " +
            "AND d.startDate <= CURRENT_TIMESTAMP " +
            "AND d.endDate >= CURRENT_TIMESTAMP " +
            "AND (p.id = :productId OR d.applicableProducts IS EMPTY) " +
            "AND (dl.id = :dealerId OR d.applicableDealers IS EMPTY) " +
            "ORDER BY d.discountValue DESC")
    List<Discount> findValidDiscountsForProductAndDealer(@Param("productId") Long productId,
                                                         @Param("dealerId") Long dealerId,
                                                         @Param("status") EntityStatus status);

    // ==================== DEALER BASED QUERIES ====================

    // Bayiye uygulanabilir aktif indirimleri getir
    @Query("SELECT DISTINCT d FROM Discount d " +
            "LEFT JOIN d.applicableDealers dl " +
            "WHERE d.status = :status " +
            "AND d.startDate <= CURRENT_TIMESTAMP " +
            "AND d.endDate >= CURRENT_TIMESTAMP " +
            "AND (dl.id = :dealerId OR d.applicableDealers IS EMPTY) " +
            "ORDER BY d.discountValue DESC")
    List<Discount> findValidDiscountsForDealer(@Param("dealerId") Long dealerId,
                                               @Param("status") EntityStatus status);

    // ==================== TIME BASED QUERIES ====================

    // Süresi dolan indirimleri getir
    @Query("SELECT d FROM Discount d WHERE d.status = :status " +
            "AND d.endDate < CURRENT_TIMESTAMP " +
            "ORDER BY d.endDate DESC")
    List<Discount> findExpiredDiscounts(@Param("status") EntityStatus status);

    // Yakında başlayacak indirimleri getir
    @Query("SELECT d FROM Discount d WHERE d.status = :status " +
            "AND d.startDate > CURRENT_TIMESTAMP " +
            "ORDER BY d.startDate ASC")
    List<Discount> findUpcomingDiscounts(@Param("status") EntityStatus status);

    // Belirli tarih aralığında aktif olan indirimleri getir
    @Query("SELECT d FROM Discount d WHERE d.status = :status " +
            "AND d.startDate <= :endDate " +
            "AND d.endDate >= :startDate")
    List<Discount> findDiscountsInDateRange(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate,
                                            @Param("status") EntityStatus status);

    // Yakında süresı dolacak indirimleri getir (x gün içinde)
    @Query(value = "SELECT * FROM discounts d WHERE d.status = :#{#status.name()} " +
            "AND d.end_date BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL :days DAY) " +
            "ORDER BY d.end_date ASC",
            nativeQuery = true)
    List<Discount> findDiscountsExpiringInDays(@Param("days") int days,
                                               @Param("status") EntityStatus status);

    // ==================== CONFLICT DETECTION QUERIES ====================

    // Çakışan indirimleri bul (tarih aralığı ve ürün/bayi bazlı)
    @Query("SELECT d FROM Discount d WHERE d.status = :status " +
            "AND (:excludeId IS NULL OR d.id != :excludeId) " +
            "AND ((d.startDate <= :startDate AND d.endDate >= :startDate) " +
            "OR (d.startDate <= :endDate AND d.endDate >= :endDate) " +
            "OR (d.startDate >= :startDate AND d.endDate <= :endDate))")
    List<Discount> findConflictingDiscounts(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate,
                                            @Param("status") EntityStatus status,
                                            @Param("excludeId") Long excludeId);

    // ==================== BUSINESS LOGIC QUERIES ====================

    // İndirimin aktif siparişlerde kullanılıp kullanılmadığını kontrol et
    @Query("SELECT COUNT(o) > 0 FROM Order o WHERE o.appliedDiscount.id = :discountId " +
            "AND o.orderStatus NOT IN ('COMPLETED', 'CANCELLED')")
    boolean isDiscountInUse(@Param("discountId") Long discountId);

    // Kategoriye göre indirimleri getir
    @Query("SELECT DISTINCT d FROM Discount d " +
            "JOIN d.applicableProducts p " +
            "WHERE p.category.id = :categoryId " +
            "AND d.status = :status " +
            "AND d.startDate <= CURRENT_TIMESTAMP " +
            "AND d.endDate >= CURRENT_TIMESTAMP " +
            "ORDER BY d.discountValue DESC")
    List<Discount> findValidDiscountsForCategory(@Param("categoryId") Long categoryId,
                                                 @Param("status") EntityStatus status);

    // En yüksek indirim değerine sahip aktif indirimi getir
    @Query("SELECT d FROM Discount d WHERE d.status = :status " +
            "AND d.startDate <= CURRENT_TIMESTAMP " +
            "AND d.endDate >= CURRENT_TIMESTAMP " +
            "ORDER BY d.discountValue DESC")
    List<Discount> findHighestActiveDiscounts(@Param("status") EntityStatus status,
                                              Pageable pageable);

    // ==================== STATISTICS QUERIES ====================

    // Aktif indirim sayısı
    @Query("SELECT COUNT(d) FROM Discount d WHERE d.status = :status " +
            "AND d.startDate <= CURRENT_TIMESTAMP " +
            "AND d.endDate >= CURRENT_TIMESTAMP")
    Long countActiveDiscounts(@Param("status") EntityStatus status);

    // Belirli ürün için indirim sayısı
    @Query("SELECT COUNT(DISTINCT d) FROM Discount d " +
            "JOIN d.applicableProducts p " +
            "WHERE p.id = :productId " +
            "AND d.status = :status " +
            "AND d.startDate <= CURRENT_TIMESTAMP " +
            "AND d.endDate >= CURRENT_TIMESTAMP")
    Long countDiscountsForProduct(@Param("productId") Long productId,
                                  @Param("status") EntityStatus status);

    // Belirli bayi için indirim sayısı
    @Query("SELECT COUNT(DISTINCT d) FROM Discount d " +
            "LEFT JOIN d.applicableDealers dl " +
            "WHERE (dl.id = :dealerId OR d.applicableDealers IS EMPTY) " +
            "AND d.status = :status " +
            "AND d.startDate <= CURRENT_TIMESTAMP " +
            "AND d.endDate >= CURRENT_TIMESTAMP")
    Long countDiscountsForDealer(@Param("dealerId") Long dealerId,
                                 @Param("status") EntityStatus status);

    // Toplam indirim sayısı (status bazlı)
    @Query("SELECT COUNT(d) FROM Discount d WHERE d.status = :status")
    Long countByStatus(@Param("status") EntityStatus status);

    // Bu ay oluşturulan indirim sayısı
    @Query(value = "SELECT COUNT(*) FROM discounts d WHERE d.status = :#{#status.name()} " +
            "AND MONTH(d.created_at) = MONTH(NOW()) " +
            "AND YEAR(d.created_at) = YEAR(NOW())",
            nativeQuery = true)
    Long countDiscountsCreatedThisMonth(@Param("status") EntityStatus status);

    // ==================== ADVANCED QUERIES ====================

    // Gelişmiş arama (multiple criteria)
    @Query("SELECT DISTINCT d FROM Discount d " +
            "LEFT JOIN d.applicableProducts p " +
            "LEFT JOIN d.applicableDealers dl " +
            "WHERE d.status = :status " +
            "AND (:searchTerm IS NULL OR :searchTerm = '' OR " +
            "    LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "    LOWER(d.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (:discountType IS NULL OR d.discountType = :discountType) " +
            "AND (:productId IS NULL OR p.id = :productId) " +
            "AND (:dealerId IS NULL OR dl.id = :dealerId) " +
            "AND (:minValue IS NULL OR d.discountValue >= :minValue) " +
            "AND (:maxValue IS NULL OR d.discountValue <= :maxValue) " +
            "AND (:startDateFrom IS NULL OR d.startDate >= :startDateFrom) " +
            "AND (:startDateTo IS NULL OR d.startDate <= :startDateTo) " +
            "AND (:activeOnly IS NULL OR :activeOnly = false OR " +
            "    (d.startDate <= CURRENT_TIMESTAMP AND d.endDate >= CURRENT_TIMESTAMP)) " +
            "ORDER BY d.discountValue DESC")
    Page<Discount> findByAdvancedCriteria(@Param("searchTerm") String searchTerm,
                                          @Param("discountType") com.maxx_global.enums.DiscountType discountType,
                                          @Param("productId") Long productId,
                                          @Param("dealerId") Long dealerId,
                                          @Param("minValue") java.math.BigDecimal minValue,
                                          @Param("maxValue") java.math.BigDecimal maxValue,
                                          @Param("startDateFrom") LocalDateTime startDateFrom,
                                          @Param("startDateTo") LocalDateTime startDateTo,
                                          @Param("activeOnly") Boolean activeOnly,
                                          @Param("status") EntityStatus status,
                                          Pageable pageable);

    // En çok kullanılan indirimleri getir
    @Query("SELECT d, COUNT(o) as orderCount FROM Discount d " +
            "LEFT JOIN Order o ON o.appliedDiscount = d " +
            "WHERE d.status = :status " +
            "GROUP BY d " +
            "ORDER BY orderCount DESC")
    List<Object[]> findMostUsedDiscounts(@Param("status") EntityStatus status,
                                         Pageable pageable);

    // Belirli tutarın üzerindeki indirimleri getir
    @Query("SELECT d FROM Discount d WHERE d.status = :status " +
            "AND d.discountValue >= :minValue " +
            "AND d.startDate <= CURRENT_TIMESTAMP " +
            "AND d.endDate >= CURRENT_TIMESTAMP " +
            "ORDER BY d.discountValue DESC")
    List<Discount> findHighValueActiveDiscounts(@Param("minValue") java.math.BigDecimal minValue,
                                                @Param("status") EntityStatus status);

    // Hiç kullanılmamış indirimleri getir
    @Query("SELECT d FROM Discount d WHERE d.status = :status " +
            "AND NOT EXISTS (SELECT 1 FROM Order o WHERE o.appliedDiscount = d) " +
            "ORDER BY d.createdAt DESC")
    List<Discount> findUnusedDiscounts(@Param("status") EntityStatus status);

    // ==================== SIMPLE QUERIES ====================

    // İsme göre indirim arama (benzersizlik kontrolü için)
    boolean existsByNameIgnoreCaseAndStatus(String name, EntityStatus status);
    boolean existsByNameIgnoreCaseAndStatusAndIdNot(String name, EntityStatus status, Long id);

    // Tarih aralığı kontrolü
    @Query("SELECT COUNT(d) > 0 FROM Discount d WHERE d.status = :status " +
            "AND d.name = :name " +
            "AND (:excludeId IS NULL OR d.id != :excludeId) " +
            "AND ((d.startDate <= :startDate AND d.endDate >= :startDate) " +
            "OR (d.startDate <= :endDate AND d.endDate >= :endDate))")
    boolean existsOverlappingDiscount(@Param("name") String name,
                                      @Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate,
                                      @Param("status") EntityStatus status,
                                      @Param("excludeId") Long excludeId);

    // Status bazlı listeleme
    List<Discount> findByStatusOrderByCreatedAtDesc(EntityStatus status);
    List<Discount> findByStatusOrderByNameAsc(EntityStatus status);
}
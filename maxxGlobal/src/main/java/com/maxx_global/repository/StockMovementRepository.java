package com.maxx_global.repository;

import com.maxx_global.entity.StockMovement;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    // ==================== TEMEL SORGULAR ====================

    Page<StockMovement> findByProductIdAndStatus(Long productId, EntityStatus status, Pageable pageable);
    List<StockMovement> findByProductIdAndStatus(Long productId, EntityStatus status);
    List<StockMovement> findByProductIdAndStatusOrderByMovementDateDesc(Long productId, EntityStatus status);
    Page<StockMovement> findByStatus(EntityStatus status, Pageable pageable);

    // ==================== STOCKTRACKER SERVİSİ İÇİN GEREKLİ METODLAR ====================

    /**
     * En son stok hareketini getir
     */
    StockMovement findTopByProductIdAndStatusOrderByMovementDateDesc(Long productId, EntityStatus status);

    /**
     * Belirli referans için stok hareketlerini getir
     */
    List<StockMovement> findByReferenceTypeAndReferenceIdAndStatusOrderByMovementDateDesc(
            String referenceType, Long referenceId, EntityStatus status);

    /**
     * Belirli tarih aralığındaki stok hareketlerini getir
     */
    @Query("SELECT sm FROM StockMovement sm WHERE " +
            "sm.movementDate >= :startDate AND sm.movementDate <= :endDate AND " +
            "sm.status = :status ORDER BY sm.movementDate DESC")
    List<StockMovement> findByMovementDateBetweenAndStatus(@Param("startDate") LocalDateTime startDate,
                                                           @Param("endDate") LocalDateTime endDate,
                                                           @Param("status") EntityStatus status);

    /**
     * Belirli ürün ve tarih aralığında stok hareketlerini getir
     */
    @Query("SELECT sm FROM StockMovement sm WHERE " +
            "sm.product.id = :productId AND " +
            "sm.movementDate >= :startDate AND sm.movementDate <= :endDate AND " +
            "sm.status = :status ORDER BY sm.movementDate DESC")
    List<StockMovement> findByProductIdAndMovementDateBetween(@Param("productId") Long productId,
                                                              @Param("startDate") LocalDateTime startDate,
                                                              @Param("endDate") LocalDateTime endDate,
                                                              @Param("status") EntityStatus status);

    /**
     * Belirli hareket tiplerinde stok hareketlerini getir
     */
    List<StockMovement> findByMovementTypeInAndStatusOrderByMovementDateDesc(
            List<StockMovementType> movementTypes, EntityStatus status);

    /**
     * Belirli kullanıcının yaptığı stok hareketlerini getir
     */
    List<StockMovement> findByPerformedByAndStatusOrderByMovementDateDesc(Long performedBy, EntityStatus status);

    /**
     * Notlarda belirli metni içeren hareketleri getir (batch işlemleri için)
     */
    List<StockMovement> findByNotesContainingAndStatusOrderByMovementDateDesc(String searchText, EntityStatus status);

    /**
     * Belirli tarihten sonraki hareketleri getir
     */
    List<StockMovement> findByMovementDateAfterAndStatus(LocalDateTime afterDate, EntityStatus status);

    /**
     * Belirli tarih aralığında hareket sayısını getir
     */
    Long countByMovementDateBetweenAndStatus(LocalDateTime startDate, LocalDateTime endDate, EntityStatus status);

    /**
     * Belirli tarihten sonraki hareket sayısını getir
     */
    Long countByMovementDateAfterAndStatus(LocalDateTime afterDate, EntityStatus status);

    // ==================== ADVANCED FILTERING QUERIES ====================

    /**
     * Kompleks filtreleme için query
     */
    @Query(value = """
        SELECT * FROM stock_movements sm 
        WHERE (:movementType::varchar IS NULL OR sm.movement_type = CAST(:movementType AS varchar))
        AND (:productId IS NULL OR sm.product_id = :productId)
        AND (:startDate::timestamp IS NULL OR sm.movement_date >= CAST(:startDate AS timestamp))
        AND (:endDate::timestamp IS NULL OR sm.movement_date <= CAST(:endDate AS timestamp))
        AND sm.status = CAST(:status AS varchar)
        ORDER BY sm.movement_date DESC
        """, nativeQuery = true)
    Page<StockMovement> findWithFilters(@Param("movementType") StockMovementType movementType,
                                        @Param("productId") Long productId,
                                        @Param("startDate") String startDate,
                                        @Param("endDate") String endDate,
                                        @Param("status") String status,
                                        Pageable pageable);


    Page<StockMovement> findByMovementTypeAndStatus(StockMovementType movementType,
                                                    EntityStatus status,
                                                    Pageable pageable);


    Page<StockMovement> findByMovementDateBetweenAndStatus(LocalDateTime startDate,
                                                           LocalDateTime endDate,
                                                           EntityStatus status,
                                                           Pageable pageable);

    Page<StockMovement> findByMovementTypeAndProductIdAndStatus(StockMovementType movementType,
                                                                Long productId,
                                                                EntityStatus status,
                                                                Pageable pageable);

    Page<StockMovement> findByMovementTypeAndProductIdAndMovementDateBetweenAndStatus(
            StockMovementType movementType,
            Long productId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            EntityStatus status,
            Pageable pageable);


    /**
     * Arama sorgusu - ürün adı, kodu, notlarda arama
     */
    @Query("SELECT sm FROM StockMovement sm WHERE " +
            "(LOWER(sm.product.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(sm.product.code) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(sm.notes) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(sm.documentNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
            "sm.status = :status " +
            "ORDER BY sm.movementDate DESC")
    Page<StockMovement> searchMovements(@Param("searchTerm") String searchTerm,
                                        @Param("status") EntityStatus status,
                                        Pageable pageable);

    // ==================== RECENT MOVEMENTS ====================

    /**
     * Son X günde yapılan stok hareketlerini getir
     */
    @Query("SELECT sm FROM StockMovement sm WHERE " +
            "sm.movementDate >= :sinceDate AND sm.status = :status " +
            "ORDER BY sm.movementDate DESC")
    List<StockMovement> findRecentMovements(@Param("sinceDate") LocalDateTime sinceDate,
                                            @Param("status") EntityStatus status);

    // ==================== STATISTICS AND ANALYTICS ====================

    /**
     * Ürün için stok istatistikleri
     */
    @Query("SELECT " +
            "SUM(CASE WHEN sm.movementType IN :inTypes THEN sm.quantity ELSE 0 END) as totalIn, " +
            "SUM(CASE WHEN sm.movementType IN :outTypes THEN sm.quantity ELSE 0 END) as totalOut, " +
            "COUNT(sm) as totalMovements " +
            "FROM StockMovement sm WHERE sm.product.id = :productId AND sm.status = :status")
    Object[] getStockStatistics(@Param("productId") Long productId,
                                @Param("inTypes") List<StockMovementType> inTypes,
                                @Param("outTypes") List<StockMovementType> outTypes,
                                @Param("status") EntityStatus status);

    /**
     * Stok hareket sayısını hareket tipine göre grupla
     */
    @Query("SELECT sm.movementType, COUNT(sm), SUM(sm.quantity) " +
            "FROM StockMovement sm WHERE " +
            "sm.movementDate >= :startDate AND sm.status = :status " +
            "GROUP BY sm.movementType " +
            "ORDER BY COUNT(sm) DESC")
    List<Object[]> getMovementStatsByType(@Param("startDate") LocalDateTime startDate,
                                          @Param("status") EntityStatus status);

    /**
     * Günlük stok hareket özeti
     */
    @Query("SELECT DATE(sm.movementDate), COUNT(sm), " +
            "SUM(CASE WHEN sm.movementType IN :inTypes THEN sm.quantity ELSE 0 END), " +
            "SUM(CASE WHEN sm.movementType IN :outTypes THEN sm.quantity ELSE 0 END) " +
            "FROM StockMovement sm WHERE " +
            "sm.movementDate >= :startDate AND sm.status = :status " +
            "GROUP BY DATE(sm.movementDate) " +
            "ORDER BY DATE(sm.movementDate) DESC")
    List<Object[]> getDailyMovementSummary(@Param("startDate") LocalDateTime startDate,
                                           @Param("inTypes") List<StockMovementType> inTypes,
                                           @Param("outTypes") List<StockMovementType> outTypes,
                                           @Param("status") EntityStatus status);

    /**
     * Ürün bazında son hareket tarihini getir
     */
    @Query("SELECT sm.product.id, MAX(sm.movementDate) " +
            "FROM StockMovement sm WHERE sm.status = :status " +
            "GROUP BY sm.product.id")
    List<Object[]> findLastMovementDateByProduct(@Param("status") EntityStatus status);

    // ==================== SPECIAL OPERATIONS ====================

    /**
     * Excel işlemleri için stok hareketlerini getir
     */
//    @Query("SELECT sm FROM StockMovement sm WHERE " +
//            "sm.movementType IN ('EXCEL_IMPORT', 'EXCEL_UPDATE') AND " +
//            "sm.status = :status AND " +
//            "sm.movementDate >= :startDate " +
//            "ORDER BY sm.movementDate DESC")
//    List<StockMovement> findRecentExcelOperations(@Param("startDate") LocalDateTime startDate,
//                                                  @Param("status") EntityStatus status);

    /**
     * Otomatik oluşturulan stok hareketlerini getir
     */
    @Query("SELECT sm FROM StockMovement sm WHERE " +
            "sm.movementType IN :automaticTypes AND " +
            "sm.status = :status " +
            "ORDER BY sm.movementDate DESC")
    Page<StockMovement> findAutomaticMovements(@Param("automaticTypes") List<StockMovementType> automaticTypes,
                                               @Param("status") EntityStatus status,
                                               Pageable pageable);

    /**
     * Manuel oluşturulan stok hareketlerini getir
     */
    @Query("SELECT sm FROM StockMovement sm WHERE " +
            "sm.movementType NOT IN :automaticTypes AND " +
            "sm.status = :status " +
            "ORDER BY sm.movementDate DESC")
    Page<StockMovement> findManualMovements(@Param("automaticTypes") List<StockMovementType> automaticTypes,
                                            @Param("status") EntityStatus status,
                                            Pageable pageable);

    /**
     * Belirli sipariş için stok hareketlerini getir
     */
    @Query("SELECT sm FROM StockMovement sm WHERE " +
            "sm.referenceType = 'ORDER' AND sm.referenceId = :orderId AND " +
            "sm.status = :status ORDER BY sm.movementDate ASC")
    List<StockMovement> findByOrderId(@Param("orderId") Long orderId, @Param("status") EntityStatus status);

    /**
     * Problem tespit edilen stok hareketlerini bul (negatif stok durumları)
     */
    @Query("SELECT sm FROM StockMovement sm WHERE " +
            "sm.newStock < 0 AND sm.status = :status " +
            "ORDER BY sm.movementDate DESC")
    List<StockMovement> findNegativeStockMovements(@Param("status") EntityStatus status);

    /**
     * Belirli miktarın üzerinde hareket olan ürünleri bul
     */
    @Query("SELECT sm FROM StockMovement sm WHERE " +
            "sm.quantity >= :minQuantity AND sm.status = :status " +
            "ORDER BY sm.quantity DESC, sm.movementDate DESC")
    List<StockMovement> findLargeQuantityMovements(@Param("minQuantity") Integer minQuantity,
                                                   @Param("status") EntityStatus status);

    // ==================== CUSTOM BUSINESS LOGIC QUERIES ====================

    /**
     * Stok sayımı hareketlerini getir
     */
//    @Query("SELECT sm FROM StockMovement sm WHERE " +
//            "sm.movementType IN ('STOCK_COUNT', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT') AND " +
//            "sm.status = :status AND " +
//            "sm.movementDate >= :startDate AND sm.movementDate <= :endDate " +
//            "ORDER BY sm.movementDate DESC")
//    List<StockMovement> findStockCountMovements(@Param("startDate") LocalDateTime startDate,
//                                                @Param("endDate") LocalDateTime endDate,
//                                                @Param("status") EntityStatus status);

    /**
     * En aktif ürünleri bul (hareket sayısına göre)
     */
    @Query("SELECT sm.product.id, sm.product.name, sm.product.code, COUNT(sm), SUM(sm.quantity) " +
            "FROM StockMovement sm WHERE " +
            "sm.movementDate >= :startDate AND sm.status = :status " +
            "GROUP BY sm.product.id, sm.product.name, sm.product.code " +
            "ORDER BY COUNT(sm) DESC")
    List<Object[]> findMostActiveProducts(@Param("startDate") LocalDateTime startDate,
                                          @Param("status") EntityStatus status);

    /**
     * Hareket tipi bazında en son hareketleri getir
     */
    @Query(value = "SELECT sm.* FROM stock_movement sm " +
            "INNER JOIN (SELECT product_id, movement_type, MAX(movement_date) as max_date " +
            "FROM stock_movement WHERE status = :status " +
            "GROUP BY product_id, movement_type) latest " +
            "ON sm.product_id = latest.product_id " +
            "AND sm.movement_type = latest.movement_type " +
            "AND sm.movement_date = latest.max_date " +
            "WHERE sm.status = :status " +
            "ORDER BY sm.movement_date DESC",
            nativeQuery = true)
    List<StockMovement> findLatestMovementsByType(@Param("status") String status);

    /**
     * Batch işlemlerini getir (Excel, toplu güncelleme vb.)
     */
//    @Query("SELECT sm FROM StockMovement sm WHERE " +
//            "sm.movementType IN ('EXCEL_IMPORT', 'EXCEL_UPDATE', 'BULK_UPDATE') AND " +
//            "sm.status = :status " +
//            "ORDER BY sm.movementDate DESC")
//    List<StockMovement> findBatchOperations(@Param("status") EntityStatus status);

    Page<StockMovement> findByMovementDateBetweenAndProductIdAndStatus(LocalDateTime start, LocalDateTime end,Long productId, EntityStatus entityStatus, Pageable pageable);
}

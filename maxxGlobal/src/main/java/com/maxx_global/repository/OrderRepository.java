package com.maxx_global.repository;

import com.maxx_global.dto.order.OrderHistoryEntry;
import com.maxx_global.entity.Order;
import com.maxx_global.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    // OrderRepository.java - Eksik metodları ekle

    @Query("SELECT o FROM Order o WHERE " +
            "(:status IS NULL OR o.orderStatus = :status) AND " +
            "(:dealerId IS NULL OR o.user.dealer.id = :dealerId) AND " +
            "(:userId IS NULL OR o.user.id = :userId) " +
            "ORDER BY o.orderDate DESC")
    Page<Order> findOrdersWithFilters(@Param("status") OrderStatus status,
                                      @Param("dealerId") Long dealerId,
                                      @Param("userId") Long userId,
                                      Pageable pageable);


    @Query("SELECT o FROM Order o WHERE " +
            "LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(o.user.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(o.user.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(o.user.dealer.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Order> searchOrders(@Param("searchTerm") String searchTerm, Pageable pageable);

    Page<Order> findByUserIdAndOrderStatus(Long userId, OrderStatus orderStatus, Pageable pageable);

    // OrderRepository.java içine eklenecek metodlar:

    @Query("SELECT o FROM Order o WHERE o.user.dealer.id = :dealerId ORDER BY o.orderDate DESC")
    Page<Order> findByUserDealerId(@Param("dealerId") Long dealerId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.orderDate BETWEEN :start AND :end ORDER BY o.orderDate DESC")
    Page<Order> findOrdersInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.orderDate BETWEEN :start AND :end ORDER BY o.orderDate DESC")
    List<Order> findOrdersInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o FROM Order o WHERE o.orderDate BETWEEN :start AND :end AND o.orderStatus in :status ORDER BY o.orderDate DESC")
    List<Order> findOrdersInDateRangeWithStatus(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("status") List<OrderStatus> status);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.orderDate BETWEEN :start AND :end ORDER BY o.orderDate DESC")
    Page<Order> findByUserIdAndOrderDateBetween(@Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.orderDate BETWEEN :start AND :end ORDER BY o.orderDate DESC")
    List<Order> findByUserIdAndOrderDateBetween(@Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o FROM Order o WHERE o.orderDate BETWEEN :start AND :end ORDER BY o.orderDate DESC")
    List<Order> findByOrderDateBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);



    @Query("SELECT o FROM Order o WHERE o.user.dealer.id = :dealerId AND o.orderDate BETWEEN :start AND :end ORDER BY o.orderDate DESC")
    List<Order> findByUserDealerIdAndOrderDateBetween(@Param("dealerId") Long dealerId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Page<Order> findByOrderStatus(OrderStatus status, Pageable pageable);
    @Query("SELECT o FROM Order o WHERE " +
            "o.orderStatus = :status AND " +
            "o.updatedAt < :cutoffTime " +
            "ORDER BY o.updatedAt ASC")
    List<Order> findExpiredPendingApprovalOrders(
            @Param("cutoffTime") LocalDateTime cutoffTime,
            @Param("status") OrderStatus status);

    /**
     * Alternative method - daha basit kullanım için
     */
    @Query("SELECT o FROM Order o WHERE " +
            "o.orderStatus = 'EDITED_PENDING_APPROVAL' AND " +
            "o.updatedAt < :cutoffTime " +
            "ORDER BY o.updatedAt ASC")
    List<Order> findExpiredPendingApprovalOrders(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Belirli bir süre aralığında düzenlenen ve hala onay bekleyen siparişlerin sayısını döner
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE " +
            "o.orderStatus = 'EDITED_PENDING_APPROVAL' AND " +
            "o.updatedAt BETWEEN :startTime AND :endTime")
    Long countPendingApprovalOrdersInRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Otomatik iptal edilecek siparişlerin listesi (sadece ID ve temel bilgiler)
     */
    @Query("SELECT o.id, o.orderNumber, o.totalAmount, u.firstName, u.lastName, d.name " +
            "FROM Order o " +
            "JOIN o.user u " +
            "JOIN u.dealer d " +
            "WHERE o.orderStatus = 'EDITED_PENDING_APPROVAL' " +
            "AND o.updatedAt < :cutoffTime")
    List<Object[]> findExpiredOrdersSummary(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Belirli kullanıcının onay bekleyen düzenlenmiş siparişleri
     */
    @Query("SELECT o FROM Order o WHERE " +
            "o.orderStatus = 'EDITED_PENDING_APPROVAL' AND " +
            "o.user.id = :userId " +
            "ORDER BY o.updatedAt DESC")
    List<Order> findPendingApprovalOrdersByUser(@Param("userId") Long userId);

    /**
     * Son X saat içinde otomatik iptal edilen siparişlerin sayısı
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE " +
            "o.orderStatus = 'CANCELLED' AND " +
            "o.updatedAt > :sinceTime AND " +
            "o.adminNotes LIKE '%SİSTEM OTOMATIK İPTALİ%'")
    Long countAutoCancelledOrdersSince(@Param("sinceTime") LocalDateTime sinceTime);
// OrderRepository.java - Discount usage count method eklemesi

// Mevcut metodların altına bu metodu ekle:

    /**
     * Belirli bir indirimin kaç kez kullanıldığını sayar (sadece tamamlanmış siparişlerde)
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.appliedDiscount.id = :discountId AND o.orderStatus = :orderStatus")
    Long countByAppliedDiscountIdAndOrderStatus(@Param("discountId") Long discountId, @Param("orderStatus") OrderStatus orderStatus);

    /**
     * Belirli bir indirimin toplam kaç kez kullanıldığını sayar (tüm durumlar)
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.appliedDiscount.id = :discountId")
    Long countByAppliedDiscountId(@Param("discountId") Long discountId);

    /**
     * Belirli bir kullanıcının belirli bir indirimi kaç kez kullandığını sayar
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.appliedDiscount.id = :discountId AND o.user.id = :userId AND o.orderStatus = :orderStatus")
    Long countByAppliedDiscountIdAndUserIdAndOrderStatus(@Param("discountId") Long discountId, @Param("userId") Long userId, @Param("orderStatus") OrderStatus orderStatus);

    @Query("SELECT COUNT(o) > 0 FROM Order o WHERE o.appliedDiscount.id = :discountId " +
            "AND o.orderStatus NOT IN :status ")
    boolean isDiscountInUse(@Param("discountId") Long discountId, @Param("status") List<OrderStatus> status);

    // OrderRepository.java içine eklenecek yeni metodlar

    // İndirim kullanım sayısını kontrol etmek için
    @Query("SELECT COUNT(o) FROM Order o WHERE o.appliedDiscount.id = :discountId " +
            "AND o.orderStatus IN :validStatuses")
    Long countByAppliedDiscountIdAndOrderStatusIn(@Param("discountId") Long discountId,
                                                  @Param("validStatuses") List<OrderStatus> validStatuses);

    // Belirli kullanıcının belirli indirimi kaç kez kullandığını say
    @Query("SELECT COUNT(o) FROM Order o WHERE o.appliedDiscount.id = :discountId " +
            "AND o.user.id = :userId " +
            "AND o.orderStatus IN :validStatuses")
    Long countByAppliedDiscountIdAndUserIdAndOrderStatusIn(@Param("discountId") Long discountId,
                                                           @Param("userId") Long userId,
                                                           @Param("validStatuses") List<OrderStatus> validStatuses);

    // Belirli bayinin belirli indirimi kaç kez kullandığını say
    @Query("SELECT COUNT(o) FROM Order o WHERE o.appliedDiscount.id = :discountId " +
            "AND o.user.dealer.id = :dealerId " +
            "AND o.orderStatus IN :validStatuses")
    Long countByAppliedDiscountIdAndDealerIdAndOrderStatusIn(@Param("discountId") Long discountId,
                                                             @Param("dealerId") Long dealerId,
                                                             @Param("validStatuses") List<OrderStatus> validStatuses);
}

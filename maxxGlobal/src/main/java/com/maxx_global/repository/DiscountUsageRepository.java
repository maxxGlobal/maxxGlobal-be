package com.maxx_global.repository;

import com.maxx_global.entity.DiscountUsage;
import com.maxx_global.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiscountUsageRepository extends JpaRepository<DiscountUsage, Long> {

    // Belirli kullanıcı ve indirim kombinasyonu için kullanım sayısını getir
    @Query("SELECT COUNT(du) FROM DiscountUsage du " +
            "WHERE du.discount.id = :discountId " +
            "AND du.user.id = :userId " +
            "AND du.orderStatus IN :validStatuses")
    Long countByDiscountAndUser(@Param("discountId") Long discountId,
                                @Param("userId") Long userId,
                                @Param("validStatuses") List<OrderStatus> validStatuses);

    // Belirli bayi ve indirim kombinasyonu için kullanım sayısını getir
    @Query("SELECT COUNT(du) FROM DiscountUsage du " +
            "WHERE du.discount.id = :discountId " +
            "AND du.dealer.id = :dealerId " +
            "AND du.orderStatus IN :validStatuses")
    Long countByDiscountAndDealer(@Param("discountId") Long discountId,
                                  @Param("dealerId") Long dealerId,
                                  @Param("validStatuses") List<OrderStatus> validStatuses);

    // İndirim için toplam kullanım sayısını getir
    @Query("SELECT COUNT(du) FROM DiscountUsage du " +
            "WHERE du.discount.id = :discountId " +
            "AND du.orderStatus IN :validStatuses")
    Long countByDiscount(@Param("discountId") Long discountId,
                         @Param("validStatuses") List<OrderStatus> validStatuses);

    // Kullanıcının belirli indirimi kullanıp kullanmadığını kontrol et
    @Query("SELECT du FROM DiscountUsage du " +
            "WHERE du.discount.id = :discountId " +
            "AND du.user.id = :userId " +
            "AND du.orderStatus IN :validStatuses")
    List<DiscountUsage> findByDiscountAndUser(@Param("discountId") Long discountId,
                                              @Param("userId") Long userId,
                                              @Param("validStatuses") List<OrderStatus> validStatuses);

    // Bayinin belirli indirimi kullanıp kullanmadığını kontrol et
    @Query("SELECT du FROM DiscountUsage du " +
            "WHERE du.discount.id = :discountId " +
            "AND du.dealer.id = :dealerId " +
            "AND du.orderStatus IN :validStatuses")
    List<DiscountUsage> findByDiscountAndDealer(@Param("discountId") Long discountId,
                                                @Param("dealerId") Long dealerId,
                                                @Param("validStatuses") List<OrderStatus> validStatuses);

    // Order ID'ye göre usage kaydını bul
    @Query("SELECT du FROM DiscountUsage du WHERE du.order.id = :orderId")
    Optional<DiscountUsage> findByOrderId(@Param("orderId") Long orderId);

    // İndirim için kullanım geçmişini getir
    @Query("SELECT du FROM DiscountUsage du " +
            "WHERE du.discount.id = :discountId " +
            "ORDER BY du.usageDate DESC")
    List<DiscountUsage> findUsageHistoryByDiscount(@Param("discountId") Long discountId);

    // Kullanıcının indirim kullanım geçmişini getir
    @Query("SELECT du FROM DiscountUsage du " +
            "WHERE du.user.id = :userId " +
            "ORDER BY du.usageDate DESC")
    List<DiscountUsage> findUsageHistoryByUser(@Param("userId") Long userId);

    // Belirli tarih aralığında indirim kullanımlarını getir
    @Query("SELECT du FROM DiscountUsage du " +
            "WHERE du.discount.id = :discountId " +
            "AND du.usageDate BETWEEN :startDate AND :endDate " +
            "ORDER BY du.usageDate DESC")
    List<DiscountUsage> findUsageByDiscountAndDateRange(@Param("discountId") Long discountId,
                                                        @Param("startDate") LocalDateTime startDate,
                                                        @Param("endDate") LocalDateTime endDate);

    // Order status değiştiğinde usage kaydını güncelle
    @Query("UPDATE DiscountUsage du SET du.orderStatus = :newStatus " +
            "WHERE du.order.id = :orderId")
    void updateOrderStatus(@Param("orderId") Long orderId, @Param("newStatus") OrderStatus newStatus);

    // Cancelled/Rejected siparişlerin usage kayıtlarını sil
//    @Query("DELETE FROM DiscountUsage du " +
//            "WHERE du.order.id = :orderId " +
//            "AND du.orderStatus IN ('CANCELLED', 'REJECTED')")
//    void deleteByOrderIdAndCancelledOrRejectedStatus(@Param("orderId") Long orderId);
}
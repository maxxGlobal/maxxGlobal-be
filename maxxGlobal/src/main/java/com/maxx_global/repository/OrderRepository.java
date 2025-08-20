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

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.orderDate BETWEEN :start AND :end ORDER BY o.orderDate DESC")
    Page<Order> findByUserIdAndOrderDateBetween(@Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.orderDate BETWEEN :start AND :end ORDER BY o.orderDate DESC")
    List<Order> findByUserIdAndOrderDateBetween(@Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o FROM Order o WHERE o.user.dealer.id = :dealerId AND o.orderDate BETWEEN :start AND :end ORDER BY o.orderDate DESC")
    List<Order> findByUserDealerIdAndOrderDateBetween(@Param("dealerId") Long dealerId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Page<Order> findByOrderStatus(OrderStatus status, Pageable pageable);


}

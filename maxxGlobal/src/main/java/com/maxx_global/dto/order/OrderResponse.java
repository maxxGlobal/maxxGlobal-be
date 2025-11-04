package com.maxx_global.dto.order;

import com.maxx_global.dto.appUser.UserSummary;
import com.maxx_global.dto.discount.DiscountInfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber, // EKSIK ALAN - sipariş numarası
        String dealerName,
        Long dealerId,
        UserSummary createdBy,
        List<OrderItemSummary> items,
        LocalDateTime orderDate, // createdDate yerine orderDate
        String orderStatus,
        BigDecimal subtotal, // EKSIK ALAN
        BigDecimal discountAmount, // EKSIK ALAN
        BigDecimal totalAmount, // EKSIK ALAN
        String currency, // EKSIK ALAN
        String notes, // EKSIK ALAN - kullanıcı notu
        String adminNote, // Admin notu (varsa not, yoksa null)
        String status,
        DiscountInfo appliedDiscount,
        Boolean hasDiscount,
        BigDecimal savingsAmount
) {}
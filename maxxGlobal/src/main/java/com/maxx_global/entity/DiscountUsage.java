package com.maxx_global.entity;

import com.maxx_global.enums.OrderStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "discount_usage")
public class DiscountUsage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_id", nullable = false)
    private Discount discount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dealer_id", nullable = false)
    private Dealer dealer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "usage_date", nullable = false)
    private LocalDateTime usageDate;

    @Column(name = "discount_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "order_total", precision = 10, scale = 2, nullable = false)
    private BigDecimal orderTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus;

    @Column(name = "notes", length = 500)
    private String notes;

    // Constructors
    public DiscountUsage() {
        this.usageDate = LocalDateTime.now();
    }

    public DiscountUsage(Discount discount, AppUser user, Dealer dealer, Order order,
                         BigDecimal discountAmount, BigDecimal orderTotal, OrderStatus orderStatus) {
        this();
        this.discount = discount;
        this.user = user;
        this.dealer = dealer;
        this.order = order;
        this.discountAmount = discountAmount;
        this.orderTotal = orderTotal;
        this.orderStatus = orderStatus;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Discount getDiscount() {
        return discount;
    }

    public void setDiscount(Discount discount) {
        this.discount = discount;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public Dealer getDealer() {
        return dealer;
    }

    public void setDealer(Dealer dealer) {
        this.dealer = dealer;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public LocalDateTime getUsageDate() {
        return usageDate;
    }

    public void setUsageDate(LocalDateTime usageDate) {
        this.usageDate = usageDate;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getOrderTotal() {
        return orderTotal;
    }

    public void setOrderTotal(BigDecimal orderTotal) {
        this.orderTotal = orderTotal;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    // Helper methods
    public boolean isValidUsage() {
        return orderStatus == OrderStatus.COMPLETED ||
                orderStatus == OrderStatus.APPROVED ||
                orderStatus == OrderStatus.SHIPPED;
    }

    @Override
    public String toString() {
        return "DiscountUsage{" +
                "id=" + id +
                ", discountName=" + (discount != null ? discount.getName() : null) +
                ", userName=" + (user != null ? user.getFirstName() + " " + user.getLastName() : null) +
                ", dealerName=" + (dealer != null ? dealer.getName() : null) +
                ", orderNumber=" + (order != null ? order.getOrderNumber() : null) +
                ", usageDate=" + usageDate +
                ", discountAmount=" + discountAmount +
                ", orderStatus=" + orderStatus +
                '}';
    }
}
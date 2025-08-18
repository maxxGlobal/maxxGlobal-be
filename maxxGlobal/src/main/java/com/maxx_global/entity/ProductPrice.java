package com.maxx_global.entity;

import com.maxx_global.enums.CurrencyType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_prices", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "dealer_id", "currency", "price_type"})
})
public class ProductPrice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private CurrencyType currency;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dealer_id", nullable = false)
    private Dealer dealer;

    // Fiyatın geçerlilik tarihleri (opsiyonel - kampanyalar için)
    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    // Fiyat aktif mi? (hızlı enable/disable için)
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Default constructor
    public ProductPrice() {}

    // Constructor
    public ProductPrice(Product product, Dealer dealer, CurrencyType currency,
                         BigDecimal amount) {
        this.product = product;
        this.dealer = dealer;
        this.currency = currency;
        this.amount = amount;
        this.isActive = true;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CurrencyType getCurrency() {
        return currency;
    }

    public void setCurrency(CurrencyType currency) {
        this.currency = currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Dealer getDealer() {
        return dealer;
    }

    public void setDealer(Dealer dealer) {
        this.dealer = dealer;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    // Business methods
    public boolean isValidNow() {
        LocalDateTime now = LocalDateTime.now();
        return isActive &&
                (validFrom == null || validFrom.isBefore(now) || validFrom.equals(now)) &&
                (validUntil == null || validUntil.isAfter(now));
    }

    public boolean isExpired() {
        return validUntil != null && validUntil.isBefore(LocalDateTime.now());
    }

    public boolean isNotYetValid() {
        return validFrom != null && validFrom.isAfter(LocalDateTime.now());
    }

    @Override
    public String toString() {
        return "ProductPrice{" +
                "id=" + id +
                ", product=" + (product != null ? product.getName() : null) +
                ", dealer=" + (dealer != null ? dealer.getName() : null) +
                ", currency=" + currency +
                ", amount=" + amount +
                ", isActive=" + isActive +
                '}';
    }
}
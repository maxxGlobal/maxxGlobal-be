package com.maxx_global.entity;

import com.maxx_global.enums.CurrencyType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_prices", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_variant_id", "dealer_id", "currency"})
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

    // ⚠️ DEPRECATED - Eski ilişki (backward compatibility için)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @Deprecated
    private Product product;

    // ✅ YENİ - Artık fiyat variant'a bağlı
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id")
    private ProductVariant productVariant;

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

    // ✅ YENİ Constructor - ProductVariant ile
    public ProductPrice(ProductVariant productVariant, Dealer dealer, CurrencyType currency,
                        BigDecimal amount) {
        this.productVariant = productVariant;
        this.dealer = dealer;
        this.currency = currency;
        this.amount = amount;
        this.isActive = true;
    }

    // ⚠️ DEPRECATED Constructor - Product ile (backward compatibility)
    @Deprecated
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

    @Deprecated
    public Product getProduct() {
        return product;
    }

    @Deprecated
    public void setProduct(Product product) {
        this.product = product;
    }

    public ProductVariant getProductVariant() {
        return productVariant;
    }

    public void setProductVariant(ProductVariant productVariant) {
        this.productVariant = productVariant;
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

    /**
     * Fiyatın hangi ürüne ait olduğunu döndürür (variant üzerinden)
     * Backward compatibility için product field'ı da kontrol eder
     */
    public Product getRelatedProduct() {
        if (productVariant != null) {
            return productVariant.getProduct();
        }
        return product; // Fallback to old field
    }

    /**
     * Fiyatın hangi variant'a ait olduğunu döndürür
     */
    public ProductVariant getRelatedVariant() {
        return productVariant;
    }

    /**
     * Display name - fiyatın hangi ürün/variant için olduğunu gösterir
     */
    public String getDisplayName() {
        if (productVariant != null) {
            return productVariant.getDisplayName() + " - " + dealer.getName() + " (" + currency + ")";
        } else if (product != null) {
            return product.getName() + " - " + dealer.getName() + " (" + currency + ")";
        }
        return "Price #" + id;
    }

    @Override
    public String toString() {
        return "ProductPrice{" +
                "id=" + id +
                ", productVariant=" + (productVariant != null ? productVariant.getSku() : "null") +
                ", product=" + (product != null ? product.getName() : "null") +
                ", dealer=" + (dealer != null ? dealer.getName() : null) +
                ", currency=" + currency +
                ", amount=" + amount +
                ", isActive=" + isActive +
                '}';
    }
}
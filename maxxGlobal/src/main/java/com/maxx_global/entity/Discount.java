package com.maxx_global.entity;

import com.maxx_global.enums.DiscountType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "discounts")
public class Discount extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType; // PERCENTAGE, FIXED_AMOUNT

    @Column(name = "discount_value", nullable = false)
    private BigDecimal discountValue;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Minimum sipariş tutarı (şart olarak)
    @Column(name = "minimum_order_amount", precision = 10, scale = 2)
    private BigDecimal minimumOrderAmount;

    // Maksimum indirim tutarı (limit olarak)
    @Column(name = "maximum_discount_amount", precision = 10, scale = 2)
    private BigDecimal maximumDiscountAmount;

    // İndirim açıklaması
    @Column(name = "description", length = 500)
    private String description;

    // Kullanım sayısı limiti (opsiyonel)
    @Column(name = "usage_limit")
    private Integer usageLimit;

    // Şu ana kadar kullanım sayısı
    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    // Kişi başı kullanım limiti (opsiyonel)
    @Column(name = "usage_limit_per_customer")
    private Integer usageLimitPerCustomer;

    // İndirim kodu (kupon sistemi için - opsiyonel)
    @Column(name = "discount_code", unique = true)
    private String discountCode;

    // Otomatik uygulansın mı? (true: otomatik, false: kod ile)
    @Column(name = "auto_apply", nullable = false)
    private Boolean autoApply = true;

    // İndirim önceliği (çakışma durumunda hangisi uygulanacak)
    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    // Stackable mı? (diğer indirimlerle birlikte uygulanabilir mi?)
    @Column(name = "stackable", nullable = false)
    private Boolean stackable = false;

    // ==================== İLİŞKİLER ====================

    // Ürün bazlı indirimler
    @ManyToMany
    @JoinTable(
            name = "discount_products",
            joinColumns = @JoinColumn(name = "discount_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    private Set<Product> applicableProducts = new HashSet<>();

    // Bayi bazlı indirimler
    @ManyToMany
    @JoinTable(
            name = "discount_dealers",
            joinColumns = @JoinColumn(name = "discount_id"),
            inverseJoinColumns = @JoinColumn(name = "dealer_id")
    )
    private Set<Dealer> applicableDealers = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "discount_categories",
            joinColumns = @JoinColumn(name = "discount_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> applicableCategories = new HashSet<>();

    // ==================== GETTER/SETTER METHODS ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public Set<Product> getApplicableProducts() {
        return applicableProducts;
    }

    public void setApplicableProducts(Set<Product> applicableProducts) {
        this.applicableProducts = applicableProducts;
    }

    public Set<Dealer> getApplicableDealers() {
        return applicableDealers;
    }

    public void setApplicableDealers(Set<Dealer> applicableDealers) {
        this.applicableDealers = applicableDealers;
    }

    // ✅ YENİ - Category getter/setter
    public Set<Category> getApplicableCategories() {
        return applicableCategories;
    }

    public void setApplicableCategories(Set<Category> applicableCategories) {
        this.applicableCategories = applicableCategories;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public BigDecimal getMinimumOrderAmount() {
        return minimumOrderAmount;
    }

    public void setMinimumOrderAmount(BigDecimal minimumOrderAmount) {
        this.minimumOrderAmount = minimumOrderAmount;
    }

    public BigDecimal getMaximumDiscountAmount() {
        return maximumDiscountAmount;
    }

    public void setMaximumDiscountAmount(BigDecimal maximumDiscountAmount) {
        this.maximumDiscountAmount = maximumDiscountAmount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getUsageLimit() {
        return usageLimit;
    }

    public void setUsageLimit(Integer usageLimit) {
        this.usageLimit = usageLimit;
    }

    public Integer getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Integer usageCount) {
        this.usageCount = usageCount;
    }

    public Integer getUsageLimitPerCustomer() {
        return usageLimitPerCustomer;
    }

    public void setUsageLimitPerCustomer(Integer usageLimitPerCustomer) {
        this.usageLimitPerCustomer = usageLimitPerCustomer;
    }

    public String getDiscountCode() {
        return discountCode;
    }

    public void setDiscountCode(String discountCode) {
        this.discountCode = discountCode;
    }

    public Boolean getAutoApply() {
        return autoApply;
    }

    public void setAutoApply(Boolean autoApply) {
        this.autoApply = autoApply;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getStackable() {
        return stackable;
    }

    public void setStackable(Boolean stackable) {
        this.stackable = stackable;
    }

    // ==================== BUSİNESS LOGIC METHODS ====================

    public boolean isValidNow() {
        LocalDateTime now = LocalDateTime.now();
        return isActive &&
                startDate != null && startDate.isBefore(now) &&
                endDate != null && endDate.isAfter(now) &&
                (usageLimit == null || usageCount < usageLimit);
    }

    public boolean isExpired() {
        return endDate != null && endDate.isBefore(LocalDateTime.now());
    }

    public boolean isNotYetStarted() {
        return startDate != null && startDate.isAfter(LocalDateTime.now());
    }

    public boolean hasUsageLeft() {
        return usageLimit == null || usageCount < usageLimit;
    }

    public void incrementUsageCount() {
        this.usageCount = (this.usageCount == null ? 0 : this.usageCount) + 1;
    }

    public boolean canBeStackedWith(Discount other) {
        return this.stackable && other.stackable;
    }

    public boolean hasHigherPriorityThan(Discount other) {
        return this.priority > other.priority;
    }

    // ✅ YENİ - İndirim uygulama kontrolü metodları

    /**
     * İndirim genel mi? (tüm ürünlere uygulanabilir)
     */
    public boolean isGeneralDiscount() {
        return applicableProducts.isEmpty() &&
                applicableCategories.isEmpty();
    }

    /**
     * İndirim kategori bazlı mı?
     */
    public boolean isCategoryBasedDiscount() {
        return !applicableCategories.isEmpty();
    }

    /**
     * İndirim ürün bazlı mı?
     */
    public boolean isProductBasedDiscount() {
        return !applicableProducts.isEmpty();
    }

    /**
     * İndirim bayi bazlı mı?
     */
    public boolean isDealerBasedDiscount() {
        return !applicableDealers.isEmpty();
    }

    /**
     * Belirli bir ürüne uygulanabilir mi?
     */
    public boolean isApplicableToProduct(Product product) {
        // Eğer genel indirim ise (ne ürün ne kategori seçilmemiş)
        if (isGeneralDiscount()) {
            return true;
        }

        // Ürün direktly seçilmişse
        if (applicableProducts.contains(product)) {
            return true;
        }

        // Ürünün kategorisi seçilmişse
        if (product.getCategory() != null && applicableCategories.contains(product.getCategory())) {
            return true;
        }

        return false;
    }

    /**
     * Belirli bir bayiye uygulanabilir mi?
     */
    public boolean isApplicableToDealer(Dealer dealer) {
        // Eğer bayi seçimi yoksa, tüm bayiler için geçerli
        if (applicableDealers.isEmpty()) {
            return true;
        }

        // Bayi seçilmişse
        return applicableDealers.contains(dealer);
    }

    /**
     * İndirim kapsamını açıklayan metin
     */
    public String getDiscountScope() {
        if (isGeneralDiscount()) {
            return "Tüm Ürünler";
        }

        StringBuilder scope = new StringBuilder();

        if (!applicableProducts.isEmpty()) {
            scope.append("Seçili Ürünler (").append(applicableProducts.size()).append(")");
        }

        if (!applicableCategories.isEmpty()) {
            if (scope.length() > 0) scope.append(" + ");
            scope.append("Kategoriler (").append(applicableCategories.size()).append(")");
        }

        return scope.toString();
    }
}
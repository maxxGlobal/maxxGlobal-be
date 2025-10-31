package com.maxx_global.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "product_variants", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"sku"})
})
public class ProductVariant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "size", nullable = false, length = 50)
    private String size; // "4.5mm", "5.0mm", "6.0mm"

    @Column(name = "sku", nullable = false, unique = true, length = 100)
    private String sku; // "TI-001-4.5mm"

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "barcode", length = 50)
    private String barcode; // Variant'a özel barkod (opsiyonel)

    @Column(name = "minimum_order_quantity")
    private Integer minimumOrderQuantity = 1;

    @Column(name = "maximum_order_quantity")
    private Integer maximumOrderQuantity = 1000;

    // Fiyatlar artık variant'a bağlı
    @OneToMany(mappedBy = "productVariant", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductPrice> prices = new HashSet<>();

    // Default constructor
    public ProductVariant() {}

    // Constructor with required fields
    public ProductVariant(Product product, String size, String sku) {
        this.product = product;
        this.size = size;
        this.sku = sku;
        this.stockQuantity = 0;
        this.isDefault = false;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public Integer getMinimumOrderQuantity() {
        return minimumOrderQuantity;
    }

    public void setMinimumOrderQuantity(Integer minimumOrderQuantity) {
        this.minimumOrderQuantity = minimumOrderQuantity;
    }

    public Integer getMaximumOrderQuantity() {
        return maximumOrderQuantity;
    }

    public void setMaximumOrderQuantity(Integer maximumOrderQuantity) {
        this.maximumOrderQuantity = maximumOrderQuantity;
    }

    public Set<ProductPrice> getPrices() {
        return prices;
    }

    public void setPrices(Set<ProductPrice> prices) {
        this.prices = prices;
    }

    // Business methods
    public boolean isInStock() {
        return stockQuantity != null && stockQuantity > 0;
    }

    public boolean hasEnoughStock(Integer requiredQuantity) {
        return stockQuantity != null && stockQuantity >= requiredQuantity;
    }

    /**
     * SKU oluşturma helper metodu
     * Format: {productCode}-{size}
     */
    public static String generateSku(String productCode, String size) {
        if (productCode == null || size == null) {
            throw new IllegalArgumentException("Product code and size cannot be null for SKU generation");
        }

        // Size'daki özel karakterleri temizle (boşluk, nokta vb.)
        String cleanSize = size.trim().replaceAll("\\s+", "-");

        return String.format("%s-%s", productCode, cleanSize);
    }

    /**
     * Bu variant'ın görünür adını döndürür
     * Format: "ProductName - Size"
     */
    public String getDisplayName() {
        if (product != null) {
            return String.format("%s - %s", product.getName(), size);
        }
        return size;
    }

    @Override
    public String toString() {
        return "ProductVariant{" +
                "id=" + id +
                ", sku='" + sku + '\'' +
                ", size='" + size + '\'' +
                ", stockQuantity=" + stockQuantity +
                ", isDefault=" + isDefault +
                '}';
    }
}
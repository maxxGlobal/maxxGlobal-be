package com.maxx_global.entity;

import com.maxx_global.enums.StockMovementType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(name = "stock_movements")
public class StockMovement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ⚠️ DEPRECATED - Eski ilişki (backward compatibility için)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @Deprecated
    private Product product;

    // ✅ YENİ - Artık stok hareketi variant'a bağlı
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id")
    private ProductVariant productVariant;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private StockMovementType movementType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "previous_stock", nullable = false)
    private Integer previousStock;

    @Column(name = "new_stock", nullable = false)
    private Integer newStock;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    @Column(name = "total_cost")
    private BigDecimal totalCost;

    @Column(name = "reference_type")
    private String referenceType; // ORDER, MANUAL, ADJUSTMENT, EXPIRED, DAMAGED

    @Column(name = "reference_id")
    private Long referenceId; // Sipariş ID'si vs.

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "performed_by", nullable = false)
    private Long performedBy; // User ID

    @Column(name = "movement_date", nullable = false)
    private LocalDateTime movementDate;

    @Column(name = "document_number")
    private String documentNumber; // İrsaliye, fatura no vs.

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Deprecated
    public Product getProduct() { return product; }

    @Deprecated
    public void setProduct(Product product) { this.product = product; }

    public ProductVariant getProductVariant() { return productVariant; }
    public void setProductVariant(ProductVariant productVariant) { this.productVariant = productVariant; }

    public StockMovementType getMovementType() { return movementType; }
    public void setMovementType(StockMovementType movementType) { this.movementType = movementType; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getPreviousStock() { return previousStock; }
    public void setPreviousStock(Integer previousStock) { this.previousStock = previousStock; }

    public Integer getNewStock() { return newStock; }
    public void setNewStock(Integer newStock) { this.newStock = newStock; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getPerformedBy() { return performedBy; }
    public void setPerformedBy(Long performedBy) { this.performedBy = performedBy; }

    public LocalDateTime getMovementDate() { return movementDate; }
    public void setMovementDate(LocalDateTime movementDate) { this.movementDate = movementDate; }

    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }

    // Business methods

    /**
     * İlişkili ürünü döndürür (variant üzerinden veya direkt)
     * Backward compatibility için
     */
    public Product getRelatedProduct() {
        if (productVariant != null) {
            return productVariant.getProduct();
        }
        return product; // Fallback to old field
    }

    /**
     * İlişkili variant'ı döndürür
     */
    public ProductVariant getRelatedVariant() {
        return productVariant;
    }

    /**
     * Hareketin display name'i
     */
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        sb.append(movementType.getDisplayName());

        if (productVariant != null) {
            sb.append(" - ").append(productVariant.getDisplayName());
        } else if (product != null) {
            sb.append(" - ").append(product.getName());
        }

        sb.append(" (").append(quantity).append(" ").append("adet").append(")");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "StockMovement{" +
                "id=" + id +
                ", productVariant=" + (productVariant != null ? productVariant.getSku() : "null") +
                ", product=" + (product != null ? product.getName() : "null") +
                ", movementType=" + movementType +
                ", quantity=" + quantity +
                ", previousStock=" + previousStock +
                ", newStock=" + newStock +
                ", movementDate=" + movementDate +
                '}';
    }
}
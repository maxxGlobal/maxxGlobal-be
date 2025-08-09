package com.maxx_global.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false)
    private String name;

    @Column(name = "product_code", nullable = false, unique = true)
    private String code;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "material")
    private String material;

    @Column(name = "size")
    private String size;

    @Column(name = "sterile")
    private Boolean sterile;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "diameter")
    private String diameter; // Çap bilgisi

    @Column(name = "angle")
    private String angle; // Açı bilgisi (plaklar için)

    @Column(name = "implantable")
    private Boolean implantable = false; // İmplant mı?

    @Column(name = "single_use", nullable = false)
    private Boolean singleUse = true; // Tek kullanımlık mı?

    @Column(name = "ce_marking")
    private Boolean ceMarking = false; // CE işareti var mı?

    @Column(name = "fda_approved")
    private Boolean fdaApproved = false; // FDA onaylı mı?

    @Column(name = "medical_device_class")
    private String medicalDeviceClass; // Class I, II, III

    @Column(name = "regulatory_number")
    private String regulatoryNumber; // Regulatory numarası

    @Column(name = "weight_grams")
    private BigDecimal weightGrams; // Ağırlık (gram)

    @Column(name = "dimensions")
    private String dimensions; // Boyutlar

    @Column(name = "color")
    private String color;

    @Column(name = "surface_treatment")
    private String surfaceTreatment; // Yüzey işlemi

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "manufacturer_code")
    private String manufacturerCode; // Üretici kodu

    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "shelf_life_months")
    private Integer shelfLifeMonths; // Raf ömrü (ay)

    @Column(name = "unit")
    private String unit;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "lot_number", nullable = false)
    private String lotNumber;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductPrice> prices = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductImage> images = new HashSet<>();

    // Minimum sipariş miktarı
    @Column(name = "minimum_order_quantity")
    private Integer minimumOrderQuantity = 1;

    @Column(name = "maximum_order_quantity")
    private Integer maximumOrderQuantity = 1;

    // --- GETTER ve SETTER'lar ---

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public Boolean getSterile() {
        return sterile;
    }

    public void setSterile(Boolean sterile) {
        this.sterile = sterile;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Set<ProductPrice> getPrices() {
        return prices;
    }

    public void setPrices(Set<ProductPrice> prices) {
        this.prices = prices;
    }

    public Set<ProductImage> getImages() {
        return images;
    }

    public void setImages(Set<ProductImage> images) {
        this.images = images;
    }

    public Integer getMinimumOrderQuantity() {
        return minimumOrderQuantity;
    }

    public void setMinimumOrderQuantity(Integer minimumOrderQuantity) {
        this.minimumOrderQuantity = minimumOrderQuantity;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Integer getMaximumOrderQuantity() {
        return maximumOrderQuantity;
    }

    public void setMaximumOrderQuantity(Integer maximumOrderQuantity) {
        this.maximumOrderQuantity = maximumOrderQuantity;
    }

    public String getDiameter() {
        return diameter;
    }

    public void setDiameter(String diameter) {
        this.diameter = diameter;
    }

    public String getAngle() {
        return angle;
    }

    public void setAngle(String angle) {
        this.angle = angle;
    }

    public Boolean getImplantable() {
        return implantable;
    }

    public void setImplantable(Boolean implantable) {
        this.implantable = implantable;
    }

    public Boolean getSingleUse() {
        return singleUse;
    }

    public void setSingleUse(Boolean singleUse) {
        this.singleUse = singleUse;
    }

    public Boolean getCeMarking() {
        return ceMarking;
    }

    public void setCeMarking(Boolean ceMarking) {
        this.ceMarking = ceMarking;
    }

    public Boolean getFdaApproved() {
        return fdaApproved;
    }

    public void setFdaApproved(Boolean fdaApproved) {
        this.fdaApproved = fdaApproved;
    }

    public String getMedicalDeviceClass() {
        return medicalDeviceClass;
    }

    public void setMedicalDeviceClass(String medicalDeviceClass) {
        this.medicalDeviceClass = medicalDeviceClass;
    }

    public String getRegulatoryNumber() {
        return regulatoryNumber;
    }

    public void setRegulatoryNumber(String regulatoryNumber) {
        this.regulatoryNumber = regulatoryNumber;
    }

    public BigDecimal getWeightGrams() {
        return weightGrams;
    }

    public void setWeightGrams(BigDecimal weightGrams) {
        this.weightGrams = weightGrams;
    }

    public String getDimensions() {
        return dimensions;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getSurfaceTreatment() {
        return surfaceTreatment;
    }

    public void setSurfaceTreatment(String surfaceTreatment) {
        this.surfaceTreatment = surfaceTreatment;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getManufacturerCode() {
        return manufacturerCode;
    }

    public void setManufacturerCode(String manufacturerCode) {
        this.manufacturerCode = manufacturerCode;
    }

    public LocalDate getManufacturingDate() {
        return manufacturingDate;
    }

    public void setManufacturingDate(LocalDate manufacturingDate) {
        this.manufacturingDate = manufacturingDate;
    }

    public Integer getShelfLifeMonths() {
        return shelfLifeMonths;
    }

    public void setShelfLifeMonths(Integer shelfLifeMonths) {
        this.shelfLifeMonths = shelfLifeMonths;
    }
}

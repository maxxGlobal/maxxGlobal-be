package com.maxx_global.dto.productExcel;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Excel'den okunan ürün verisi için DTO
 */
public class ExcelProductData {
    private Integer rowNumber;
    private String productCode;
    private String productName;
    private String description;
    private String categoryName;
    private String material;
    private String size; // ✅ Varyant boyutu (Variant Size)
    private String sku;  // ✅ YENİ: Varyant SKU kodu
    private String diameter;
    private String angle;
    private Boolean sterile;
    private Boolean singleUse;
    private Boolean implantable;
    private Boolean ceMarking;
    private Boolean fdaApproved;
    private String medicalDeviceClass;
    private String regulatoryNumber;
    private BigDecimal weightGrams;
    private String dimensions;
    private String color;
    private String surfaceTreatment;
    private String serialNumber;
    private String manufacturerCode;
    private LocalDate manufacturingDate;
    private String unit;
    private String barcode;
    private String lotNumber;
    private Integer stockQuantity;
    private Integer minimumOrderQuantity;
    private Integer maximumOrderQuantity;

    // Default constructor
    public ExcelProductData() {}

    // Constructor with row number
    public ExcelProductData(Integer rowNumber) {
        this.rowNumber = rowNumber;
        this.singleUse = true; // Default value
        this.implantable = false; // Default value
        this.ceMarking = false; // Default value
        this.fdaApproved = false; // Default value
    }

    // Getters and Setters
    public Integer getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Integer rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
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

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
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

    public Boolean getSterile() {
        return sterile;
    }

    public void setSterile(Boolean sterile) {
        this.sterile = sterile;
    }

    public Boolean getSingleUse() {
        return singleUse;
    }

    public void setSingleUse(Boolean singleUse) {
        this.singleUse = singleUse;
    }

    public Boolean getImplantable() {
        return implantable;
    }

    public void setImplantable(Boolean implantable) {
        this.implantable = implantable;
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

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
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

    // Validation method
    public boolean isValid() {
        boolean hasProductBasics = productCode != null && !productCode.trim().isEmpty() &&
                productName != null && !productName.trim().isEmpty() &&
                categoryName != null && !categoryName.trim().isEmpty() &&
                lotNumber != null && !lotNumber.trim().isEmpty();

        boolean hasVariantSize = size != null && !size.trim().isEmpty();

        return hasProductBasics && hasVariantSize;
    }

    // Error description for invalid data
    public String getValidationError() {
        if (productCode == null || productCode.trim().isEmpty()) {
            return "Ürün kodu boş olamaz";
        }
        if (productName == null || productName.trim().isEmpty()) {
            return "Ürün adı boş olamaz";
        }
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return "Kategori adı boş olamaz";
        }
        if (lotNumber == null || lotNumber.trim().isEmpty()) {
            return "Lot numarası boş olamaz";
        }
        if (size == null || size.trim().isEmpty()) {
            return "Varyant için boyut bilgisi zorunludur";
        }

        if (minimumOrderQuantity != null && maximumOrderQuantity != null &&
                minimumOrderQuantity > maximumOrderQuantity) {
            return "Minimum sipariş miktarı maksimumdan büyük olamaz";
        }

        if (stockQuantity != null && stockQuantity < 0) {
            return "Stok miktarı negatif olamaz";
        }

        if (weightGrams != null && weightGrams.compareTo(BigDecimal.ZERO) < 0) {
            return "Ağırlık negatif olamaz";
        }

        return null;
    }

    @Override
    public String toString() {
        return "ExcelProductData{" +
                "rowNumber=" + rowNumber +
                ", productCode='" + productCode + '\'' +
                ", productName='" + productName + '\'' +
                ", categoryName='" + categoryName + '\'' +
                ", material='" + material + '\'' +
                ", lotNumber='" + lotNumber + '\'' +
                '}';
    }
}
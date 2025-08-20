package com.maxx_global.dto.productPriceExcell;

import com.maxx_global.enums.CurrencyType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Excel'den okunan fiyat verisi için DTO
 */
public class ExcelPriceData {
    private Integer rowNumber;
    private String productCode;
    private String productName;
    private CurrencyType currency;
    private BigDecimal amount;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private Boolean isActive;

    // Default constructor
    public ExcelPriceData() {}

    // Constructor with row number
    public ExcelPriceData(Integer rowNumber) {
        this.rowNumber = rowNumber;
        this.isActive = true; // Default value
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

    // Validation method
    public boolean isValid() {
        return productCode != null && !productCode.trim().isEmpty() &&
                currency != null &&
                amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    // Error description for invalid data
    public String getValidationError() {
        if (productCode == null || productCode.trim().isEmpty()) {
            return "Ürün kodu boş olamaz";
        }
        if (currency == null) {
            return "Para birimi belirtilmeli";
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "Fiyat 0'dan büyük olmalı";
        }
        return null;
    }

    @Override
    public String toString() {
        return "ExcelPriceData{" +
                "rowNumber=" + rowNumber +
                ", productCode='" + productCode + '\'' +
                ", currency=" + currency +
                ", amount=" + amount +
                ", isActive=" + isActive +
                '}';
    }
}
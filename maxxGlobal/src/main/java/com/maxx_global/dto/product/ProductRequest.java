package com.maxx_global.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Ürün oluşturma ve güncelleme için istek modeli")
public record ProductRequest(

        @Schema(description = "Ürün adı", example = "Titanyum İmplant", required = true)
        @NotBlank(message = "Product name is required")
        @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
        String name,

        @Schema(description = "Ürün kodu", example = "TI-001", required = true)
        @NotBlank(message = "Product code is required")
        @Size(min = 2, max = 50, message = "Product code must be between 2 and 50 characters")
        String code,

        @Schema(description = "Ürün açıklaması", example = "Yüksek kaliteli titanyum implant")
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @Schema(description = "Kategori ID'si", example = "5", required = true)
        @NotNull(message = "Category ID is required")
        @Min(value = 1, message = "Category ID must be greater than 0")
        Long categoryId,

        @Schema(description = "Malzeme", example = "Titanyum")
        @Size(max = 100, message = "Material must not exceed 100 characters")
        String material,

        @Schema(description = "Boyut", example = "4.5mm")
        @Size(max = 50, message = "Size must not exceed 50 characters")
        String size,

        @Schema(description = "Çap", example = "6.0mm")
        @Size(max = 50, message = "Diameter must not exceed 50 characters")
        String diameter,

        @Schema(description = "Açı", example = "30°")
        @Size(max = 50, message = "Angle must not exceed 50 characters")
        String angle,

        @Schema(description = "Steril mi?", example = "true")
        Boolean sterile,

        @Schema(description = "Tek kullanımlık mı?", example = "true")
        Boolean singleUse,

        @Schema(description = "İmplant mı?", example = "true")
        Boolean implantable,

        @Schema(description = "CE işareti var mı?", example = "true")
        Boolean ceMarking,

        @Schema(description = "FDA onaylı mı?", example = "false")
        Boolean fdaApproved,

        @Schema(description = "Tıbbi cihaz sınıfı", example = "Class II")
        @Size(max = 50, message = "Medical device class must not exceed 50 characters")
        String medicalDeviceClass,

        @Schema(description = "Düzenleyici numarası", example = "REG-2024-001")
        @Size(max = 100, message = "Regulatory number must not exceed 100 characters")
        String regulatoryNumber,

        @Schema(description = "Ağırlık (gram)", example = "15.5")
        @DecimalMin(value = "0", message = "Weight must be positive")
        @Digits(integer = 8, fraction = 2, message = "Weight format is invalid")
        BigDecimal weightGrams,

        @Schema(description = "Boyutlar", example = "10x15x20mm")
        @Size(max = 100, message = "Dimensions must not exceed 100 characters")
        String dimensions,

        @Schema(description = "Renk", example = "Gümüş")
        @Size(max = 50, message = "Color must not exceed 50 characters")
        String color,

        @Schema(description = "Yüzey işlemi", example = "Anodize")
        @Size(max = 100, message = "Surface treatment must not exceed 100 characters")
        String surfaceTreatment,

        @Schema(description = "Seri numarası", example = "SN-2024-001")
        @Size(max = 100, message = "Serial number must not exceed 100 characters")
        String serialNumber,

        @Schema(description = "Üretici kodu", example = "MFG-001")
        @Size(max = 100, message = "Manufacturer code must not exceed 100 characters")
        String manufacturerCode,

        @Schema(description = "Üretim tarihi", example = "2024-01-15")
        LocalDate manufacturingDate,

        @Schema(description = "Son kullanma tarihi", example = "2027-01-15")
        LocalDate expiryDate,

        @Schema(description = "Raf ömrü (ay)", example = "36")
        @Min(value = 1, message = "Shelf life must be at least 1 month")
        Integer shelfLifeMonths,

        @Schema(description = "Birim", example = "adet")
        @Size(max = 20, message = "Unit must not exceed 20 characters")
        String unit,

        @Schema(description = "Barkod", example = "1234567890123")
        @Size(max = 50, message = "Barcode must not exceed 50 characters")
        String barcode,

        @Schema(description = "Lot numarası", example = "LOT-2024-001", required = true)
        @NotBlank(message = "Lot number is required")
        @Size(max = 100, message = "Lot number must not exceed 100 characters")
        String lotNumber,

        @Schema(description = "Stok miktarı", example = "100")
        @Min(value = 0, message = "Stock quantity cannot be negative")
        Integer stockQuantity,

        @Schema(description = "Minimum sipariş miktarı", example = "1")
        @Min(value = 1, message = "Minimum order quantity must be at least 1")
        Integer minimumOrderQuantity,

        @Schema(description = "Maksimum sipariş miktarı", example = "1000")
        @Min(value = 1, message = "Maximum order quantity must be at least 1")
        Integer maximumOrderQuantity

) {

    // Custom validation
    public void validate() {
        if (expiryDate != null && manufacturingDate != null &&
                expiryDate.isBefore(manufacturingDate)) {
            throw new IllegalArgumentException("Expiry date cannot be before manufacturing date");
        }

        if (minimumOrderQuantity != null && maximumOrderQuantity != null &&
                minimumOrderQuantity > maximumOrderQuantity) {
            throw new IllegalArgumentException("Minimum order quantity cannot be greater than maximum order quantity");
        }

        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Expiry date cannot be in the past");
        }
    }
}
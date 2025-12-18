package com.maxx_global.dto.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.maxx_global.dto.productImage.ProductImageInfo;
import com.maxx_global.dto.productVariant.ProductVariantDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Ürün detay yanıt modeli")
public record ProductResponse(

        @Schema(description = "Ürün ID'si", example = "1")
        Long id,

        @Schema(description = "Ürün adı (dil tercihine veya yetkiye göre)", example = "Titanyum İmplant")
        String name,

        @Schema(description = "Ürün adı (İngilizce)", example = "Titanium Implant")
        String nameEn,

        @Schema(description = "Ürün kodu", example = "TI-001")
        String code,

        @Schema(description = "Ürün açıklaması (dil tercihine veya yetkiye göre)", example = "Yüksek kaliteli titanyum implant")
        String description,

        @Schema(description = "Ürün açıklaması (İngilizce)", example = "High quality titanium implant")
        String descriptionEn,

        @Schema(description = "Kategori ID'si", example = "5")
        Long categoryId,

        @Schema(description = "Kategori adı", example = "Dental İmplantlar")
        String categoryName,

        @Schema(description = "Malzeme", example = "Titanyum")
        String material,

        // ⚠️ DEPRECATED - Artık variants içinde
        @Schema(description = "Boyut (DEPRECATED)", example = "4.5mm", deprecated = true)
        @Deprecated
        String size,

        // ✅ YENİ - Varyantlar (her varyantın kendi stoğu ve fiyatları var)
        @Schema(description = "Ürün varyantları (her varyantın kendi stoğu ve fiyatları)")
        List<ProductVariantDTO> variants,

        @Schema(description = "Çap", example = "6.0mm")
        String diameter,

        @Schema(description = "Açı", example = "30°")
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
        String medicalDeviceClass,

        @Schema(description = "Düzenleyici numarası", example = "REG-2024-001")
        String regulatoryNumber,

        @Schema(description = "Ağırlık (gram)", example = "15.5")
        BigDecimal weightGrams,

        @Schema(description = "Boyutlar", example = "10x15x20mm")
        String dimensions,

        @Schema(description = "Renk", example = "Gümüş")
        String color,

        @Schema(description = "Yüzey işlemi", example = "Anodize")
        String surfaceTreatment,

        @Schema(description = "Seri numarası", example = "SN-2024-001")
        String serialNumber,

        @Schema(description = "Üretici kodu", example = "MFG-001")
        String manufacturerCode,

        @Schema(description = "Üretim tarihi", example = "2024-01-15")
        LocalDate manufacturingDate,

        @Schema(description = "Son kullanma tarihi", example = "2027-01-15")
        LocalDate expiryDate,

        @Schema(description = "Raf ömrü (ay)", example = "36")
        Integer shelfLifeMonths,

        @Schema(description = "Birim", example = "adet")
        String unit,

        @Schema(description = "Barkod", example = "1234567890123")
        String barcode,

        @Schema(description = "Lot numarası", example = "LOT-2024-001")
        String lotNumber,

        // ⚠️ DEPRECATED - Artık variants içinde
        @Schema(description = "Stok miktarı (DEPRECATED - toplam stok için variants bakın)", example = "100", deprecated = true)
        @Deprecated
        Integer stockQuantity,

        @Schema(description = "Minimum sipariş miktarı", example = "1")
        Integer minimumOrderQuantity,

        @Schema(description = "Maksimum sipariş miktarı", example = "1000")
        Integer maximumOrderQuantity,

        @Schema(description = "Ürün resimleri URL'leri")
        List<ProductImageInfo> images,

        @Schema(description = "Ana resim URL'si")
        String primaryImageUrl,

        @Schema(description = "Ürün aktif mi?", example = "true")
        Boolean isActive,

        @Schema(description = "Stokta var mı? (herhangi bir varyant stokta varsa true)", example = "true")
        Boolean isInStock,

        @Schema(description = "Süresi dolmuş mu?", example = "false")
        Boolean isExpired,

        @Schema(description = "Oluşturulma tarihi", example = "2024-01-01T10:30:00")
        LocalDateTime createdDate,

        @Schema(description = "Güncellenme tarihi", example = "2024-01-01T10:30:00")
        LocalDateTime updatedDate,

        @Schema(description = "Durum", example = "ACTIVE")
        String status,

        @Schema(description = "Kullanıcının favorisinde mi?", example = "true")
        Boolean isFavorite

        // ÖNEMLİ: prices KALDIRILDI - artık her variant'ın kendi prices listesi var
){}

package com.maxx_global.dto.product;

import com.maxx_global.dto.productImage.ProductImageInfo;
import com.maxx_global.dto.productPrice.ProductPriceSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Ürün detay bilgisi - dealer fiyatı ile birlikte")
public record ProductWithPriceResponse(

        @Schema(description = "Ürün ID'si", example = "1")
        Long id,

        @Schema(description = "Ürün adı", example = "Titanyum İmplant")
        String name,

        @Schema(description = "Ürün kodu", example = "TI-001")
        String code,

        @Schema(description = "Ürün açıklaması")
        String description,

        @Schema(description = "Kategori ID'si", example = "5")
        Long categoryId,

        @Schema(description = "Kategori adı", example = "Dental İmplantlar")
        String categoryName,

        @Schema(description = "Malzeme", example = "Titanyum")
        String material,

        @Schema(description = "Boyut", example = "4.5mm")
        String size,

        @Schema(description = "Steril mi?", example = "true")
        Boolean sterile,

        @Schema(description = "İmplant mı?", example = "true")
        Boolean implantable,

        @Schema(description = "Stok miktarı", example = "100")
        Integer stockQuantity,

        @Schema(description = "Minimum sipariş miktarı", example = "1")
        Integer minimumOrderQuantity,

        @Schema(description = "Maksimum sipariş miktarı", example = "1000")
        Integer maximumOrderQuantity,

        @Schema(description = "Son kullanma tarihi", example = "2027-01-15")
        LocalDate expiryDate,

        @Schema(description = "Birim", example = "adet")
        String unit,

        @Schema(description = "Ürün resimleri")
        List<ProductImageInfo> images,

        @Schema(description = "Ana resim URL'si")
        String primaryImageUrl,

        @Schema(description = "Stokta var mı?", example = "true")
        Boolean isInStock,

        @Schema(description = "Süresi dolmuş mu?", example = "false")
        Boolean isExpired,

        @Schema(description = "Bu dealer için fiyat bilgileri")
        List<ProductPriceSummary> dealerPrices,

        @Schema(description = "Varsayılan fiyat (DEALER price type)")
        BigDecimal defaultPrice,

        @Schema(description = "Varsayılan para birimi")
        String defaultCurrency,

        @Schema(description = "Oluşturulma tarihi")
        LocalDateTime createdDate,

        @Schema(description = "Durum", example = "ACTIVE")
        String status
) {}
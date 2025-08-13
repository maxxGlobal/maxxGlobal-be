package com.maxx_global.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Ürün arama kriterleri - gelişmiş filtreleme için")
public record ProductSearchCriteria(

        @Schema(description = "Arama terimi (ad, kod, açıklama)", example = "implant")
        String searchTerm,

        @Schema(description = "Kategori ID'leri", example = "[1, 2, 3]")
        List<Long> categoryIds,

        @Schema(description = "Malzemeler", example = "[\"Titanyum\", \"Paslanmaz Çelik\"]")
        List<String> materials,

        @Schema(description = "Steril ürünler mi?", example = "true")
        Boolean sterile,

        @Schema(description = "İmplant ürünler mi?", example = "true")
        Boolean implantable,

        @Schema(description = "CE işareti var mı?", example = "true")
        Boolean ceMarking,

        @Schema(description = "FDA onaylı mı?", example = "true")
        Boolean fdaApproved,

        @Schema(description = "Minimum ağırlık (gram)", example = "10.0")
        BigDecimal minWeight,

        @Schema(description = "Maksimum ağırlık (gram)", example = "100.0")
        BigDecimal maxWeight,

        @Schema(description = "Son kullanma tarihi - başlangıç", example = "2025-01-01")
        LocalDate expiryDateFrom,

        @Schema(description = "Son kullanma tarihi - bitiş", example = "2027-12-31")
        LocalDate expiryDateTo,

        @Schema(description = "Minimum stok miktarı", example = "10")
        Integer minStockQuantity,

        @Schema(description = "Maksimum stok miktarı", example = "1000")
        Integer maxStockQuantity,

        @Schema(description = "Sadece stokta olanlar", example = "true")
        Boolean inStockOnly,

        @Schema(description = "Sadece aktif ürünler", example = "true")
        Boolean activeOnly,

        @Schema(description = "Süresi dolmuş ürünleri dahil et", example = "false")
        Boolean includeExpired
){}
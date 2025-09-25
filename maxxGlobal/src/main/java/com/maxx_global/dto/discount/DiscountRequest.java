package com.maxx_global.dto.discount;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DiscountRequest(
        @NotBlank(message = "İndirim adı zorunludur")
        @Size(max = 100, message = "İndirim adı en fazla 100 karakter olabilir")
        String name,

        @Size(max = 500, message = "Açıklama en fazla 500 karakter olabilir")
        String description,

        @NotBlank(message = "İndirim tipi zorunludur")
        String discountType, // "PERCENTAGE" veya "FIXED_AMOUNT"

        @NotNull(message = "İndirim değeri zorunludur")
        @DecimalMin(value = "0.01", message = "İndirim değeri 0'dan büyük olmalıdır")
        @DecimalMax(value = "100.00", message = "Yüzde indirimlerde değer 100'den fazla olamaz")
        BigDecimal discountValue,

        @NotNull(message = "Başlangıç tarihi zorunludur")
        LocalDateTime startDate,

        @NotNull(message = "Bitiş tarihi zorunludur")
        LocalDateTime endDate,

        Boolean isActive,

        @DecimalMin(value = "0.00", message = "Minimum sipariş tutarı negatif olamaz")
        BigDecimal minimumOrderAmount,

        @DecimalMin(value = "0.00", message = "Maksimum indirim tutarı negatif olamaz")
        BigDecimal maximumDiscountAmount,

        @Min(value = 1, message = "Kullanım limiti 1'den küçük olamaz")
        Integer usageLimit,

        @Min(value = 1, message = "Kişi başı kullanım limiti 1'den küçük olamaz")
        Integer usageLimitPerCustomer,

        @Size(max = 50, message = "İndirim kodu en fazla 50 karakter olabilir")
        String discountCode,

        Boolean autoApply,

        @Min(value = 0, message = "Öncelik negatif olamaz")
        @Max(value = 100, message = "Öncelik 100'den büyük olamaz")
        Integer priority,

        Boolean stackable,

        // Ürün bazlı indirimler için
        List<Long> productIds,

        // Bayi bazlı indirimler için
        List<Long> dealerIds,

        // ✅ YENİ - Kategori bazlı indirimler için
        List<Long> categoryIds

) {
    // Validation metodu
    public void validate() {
        if (startDate != null && endDate != null && !startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("Başlangıç tarihi bitiş tarihinden önce olmalıdır");
        }

        if ("PERCENTAGE".equals(discountType) && discountValue != null && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Yüzdesel indirimlerde değer 100'den fazla olamaz");
        }

        if ("FIXED_AMOUNT".equals(discountType) && discountValue != null && discountValue.compareTo(BigDecimal.valueOf(10000)) > 0) {
            throw new IllegalArgumentException("Sabit tutar indirimlerde değer çok yüksek");
        }

        if (minimumOrderAmount != null && maximumDiscountAmount != null &&
                minimumOrderAmount.compareTo(maximumDiscountAmount) <= 0) {
            // Bu business rule'a göre ayarlanabilir
        }

        // ✅ YENİ - Çakışma kontrolü
        int selectionCount = 0;
        if (productIds != null && !productIds.isEmpty()) selectionCount++;
        if (categoryIds != null && !categoryIds.isEmpty()) selectionCount++;

        // Hem ürün hem kategori seçilemez (business rule)
        if (selectionCount > 1) {
            throw new IllegalArgumentException("Aynı anda hem ürün hem kategori seçilemez. Lütfen birini seçin.");
        }

        // İndirim kodu benzersizlik kontrolü (service katmanında yapılacak)
        if (discountCode != null && discountCode.trim().isEmpty()) {
            throw new IllegalArgumentException("İndirim kodu boş olamaz");
        }
    }

    // ✅ YENİ - Helper metodlar

    /**
     * Bu indirim genel mi? (tüm ürünlere uygulanır)
     */
    public boolean isGeneralDiscount() {
        return (productIds == null || productIds.isEmpty()) &&
                (categoryIds == null || categoryIds.isEmpty());
    }

    /**
     * Bu indirim kategori bazlı mı?
     */
    public boolean isCategoryBasedDiscount() {
        return categoryIds != null && !categoryIds.isEmpty();
    }

    /**
     * Bu indirim ürün bazlı mı?
     */
    public boolean isProductBasedDiscount() {
        return productIds != null && !productIds.isEmpty();
    }

    /**
     * Bu indirim bayi bazlı mı?
     */
    public boolean isDealerBasedDiscount() {
        return dealerIds != null && !dealerIds.isEmpty();
    }

    /**
     * İndirim kapsamını açıklayan metin
     */
    public String getDiscountScope() {
        if (isGeneralDiscount()) {
            return "Tüm Ürünler";
        }

        StringBuilder scope = new StringBuilder();

        if (isProductBasedDiscount()) {
            scope.append("Seçili Ürünler (").append(productIds.size()).append(")");
        }

        if (isCategoryBasedDiscount()) {
            if (scope.length() > 0) scope.append(" + ");
            scope.append("Kategoriler (").append(categoryIds.size()).append(")");
        }

        return scope.toString();
    }

    /**
     * İndirim konfigürasyon özeti
     */
    public String getConfigurationSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(discountType).append(" - ");
        summary.append(discountValue);
        if ("PERCENTAGE".equals(discountType)) {
            summary.append("%");
        } else {
            summary.append(" TL");
        }

        if (minimumOrderAmount != null) {
            summary.append(" (Min: ").append(minimumOrderAmount).append(" TL)");
        }

        if (maximumDiscountAmount != null) {
            summary.append(" (Max: ").append(maximumDiscountAmount).append(" TL)");
        }

        return summary.toString();
    }
}
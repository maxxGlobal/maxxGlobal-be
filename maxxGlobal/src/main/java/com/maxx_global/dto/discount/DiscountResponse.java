package com.maxx_global.dto.discount;

import com.maxx_global.dto.category.CategorySummary;
import com.maxx_global.dto.dealer.DealerSummary;
import com.maxx_global.dto.productVariant.ProductVariantSummary;
import com.maxx_global.enums.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DiscountResponse(
        Long id,
        String name,
        String nameEn,
        String description,
        String descriptionEn,
        DiscountType discountType,
        BigDecimal discountValue,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Boolean isActive,
        Boolean isValidNow,              // ✅ GERİ EKLENDİ - Şu anda geçerli mi?
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscountAmount,
        Integer usageLimit,
        Integer usageCount,
        Integer usageLimitPerCustomer,
        String discountCode,
        Boolean autoApply,
        Integer priority,
        Boolean stackable,

        // İlişkili veriler
        List<ProductVariantSummary> applicableVariants,
        List<DealerSummary> applicableDealers,

        // ✅ YENİ - Kategori desteği
        List<CategorySummary> applicableCategories,

        // Metadata
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String createdBy,
        String updatedBy,

        // ✅ YENİ - Computed fields
        String discountScope,           // "Genel", "Ürün Bazlı", "Kategori Bazlı" vs.
        String discountTypeDisplay,     // "Yüzdesel %15" veya "Sabit Tutar 50 TL"
        Boolean isExpired,
        Boolean isNotYetStarted,
        Boolean hasUsageLeft,
        Integer remainingUsage,
        String validityStatus,          // "Aktif", "Süresi Doldu", "Henüz Başlamadı" vs.

        // ✅ YENİ - Category specific fields
        Boolean isCategoryBased,
        Boolean isVariantBased,
        Boolean isDealerBased,
        Boolean isGeneralDiscount
) {

        /**
         * İndirim durumunu açıklayan metin
         */
        public String getStatusDescription() {
                if (Boolean.TRUE.equals(isExpired)) {
                        return "Süresi Doldu";
                } else if (Boolean.TRUE.equals(isNotYetStarted)) {
                        return "Henüz Başlamadı";
                } else if (!Boolean.TRUE.equals(isActive)) {
                        return "Pasif";
                } else if (!Boolean.TRUE.equals(hasUsageLeft)) {
                        return "Kullanım Limiti Doldu";
                } else {
                        return "Aktif";
                }
        }

        /**
         * İndirim değerini formatlanmış şekilde döndürür
         */
        public String getFormattedDiscountValue() {
                if (discountType == DiscountType.PERCENTAGE) {
                        return "%" + discountValue.stripTrailingZeros().toPlainString();
                } else {
                        return discountValue.stripTrailingZeros().toPlainString() + " TL";
                }
        }

        /**
         * Kullanım oranını yüzde olarak döndürür
         */
        public Double getUsagePercentage() {
                if (usageLimit == null || usageLimit == 0) {
                        return null;
                }
                return (usageCount.doubleValue() / usageLimit) * 100;
        }

        /**
         * İndirim kapsamını detaylandırır
         */
        public String getDetailedScope() {
                if (Boolean.TRUE.equals(isGeneralDiscount)) {
                        return "Tüm ürünlere uygulanabilir";
                }

                StringBuilder scope = new StringBuilder();

                if (Boolean.TRUE.equals(isVariantBased) && applicableVariants != null && !applicableVariants.isEmpty()) {
                        scope.append(applicableVariants.size()).append(" varyanta özel");
                }

                if (Boolean.TRUE.equals(isCategoryBased) && applicableCategories != null && !applicableCategories.isEmpty()) {
                        if (scope.length() > 0) scope.append(" ve ");
                        scope.append(applicableCategories.size()).append(" kategoriye özel");
                }

                if (Boolean.TRUE.equals(isDealerBased) && applicableDealers != null && !applicableDealers.isEmpty()) {
                        if (scope.length() > 0) scope.append(" (");
                        else scope.append("Sadece ");
                        scope.append(applicableDealers.size()).append(" bayi");
                        if (scope.toString().contains("(")) {
                                scope.append(" için geçerli)");
                        } else {
                                scope.append(" için geçerli");
                        }
                } else if (scope.length() > 0) {
                        scope.append(" (tüm bayiler için geçerli)");
                }

                return scope.toString();
        }

        /**
         * İndirim kurallarının özeti
         */
        public String getRulesSummary() {
                StringBuilder rules = new StringBuilder();

                if (minimumOrderAmount != null) {
                        rules.append("Min. sipariş: ").append(minimumOrderAmount).append(" TL");
                }

                if (maximumDiscountAmount != null) {
                        if (rules.length() > 0) rules.append(" • ");
                        rules.append("Max. indirim: ").append(maximumDiscountAmount).append(" TL");
                }

                if (usageLimit != null) {
                        if (rules.length() > 0) rules.append(" • ");
                        rules.append("Toplam kullanım: ").append(usageCount).append("/").append(usageLimit);
                }

                if (usageLimitPerCustomer != null) {
                        if (rules.length() > 0) rules.append(" • ");
                        rules.append("Kişi başı limit: ").append(usageLimitPerCustomer);
                }

                return rules.toString();
        }

        /**
         * Kategori isimlerini virgülle ayrılmış string olarak döndürür
         */
        public String getCategoryNames() {
                if (applicableCategories == null || applicableCategories.isEmpty()) {
                        return "";
                }
                return applicableCategories.stream()
                        .map(CategorySummary::name)
                        .collect(java.util.stream.Collectors.joining(", "));
        }

        /**
         * Ürün isimlerini virgülle ayrılmış string olarak döndürür
         */
        public String getProductNames() {
                if (applicableVariants == null || applicableVariants.isEmpty()) {
                        return "";
                }
                return applicableVariants.stream()
                        .map(variant -> variant.displayName() != null ? variant.displayName() : variant.productName())
                        .collect(java.util.stream.Collectors.joining(", "));
        }

        /**
         * Bayi isimlerini virgülle ayrılmış string olarak döndürür
         */
        public String getDealerNames() {
                if (applicableDealers == null || applicableDealers.isEmpty()) {
                        return "";
                }
                return applicableDealers.stream()
                        .map(DealerSummary::name)
                        .collect(java.util.stream.Collectors.joining(", "));
        }
}
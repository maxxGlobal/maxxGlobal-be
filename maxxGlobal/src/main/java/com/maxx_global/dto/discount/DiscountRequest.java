package com.maxx_global.dto.discount;

import com.maxx_global.enums.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "İndirim oluşturma ve güncelleme için istek modeli")
public record DiscountRequest(

        @Schema(description = "İndirim adı", example = "Kış Kampanyası", required = true)
        @NotBlank(message = "İndirim adı boş olamaz")
        @Size(min = 2, max = 100, message = "İndirim adı 2 ile 100 karakter arasında olmalıdır")
        String name,

        @Schema(description = "İndirim açıklaması", example = "2024 Kış dönemi özel indirim kampanyası")
        @Size(max = 500, message = "Açıklama 500 karakteri geçemez")
        String description,

        @Schema(description = "İndirim tipi", example = "PERCENTAGE", required = true)
        @NotNull(message = "İndirim tipi seçilmelidir")
        String discountType,

        @Schema(description = "İndirim değeri", example = "15.50", required = true)
        @NotNull(message = "İndirim değeri girilmelidir")
        @DecimalMin(value = "0.01", message = "İndirim değeri 0'dan büyük olmalıdır")
        @Digits(integer = 8, fraction = 2, message = "İndirim değeri formatı geçersiz")
        BigDecimal discountValue,

        @Schema(description = "Başlangıç tarihi", example = "2024-12-01T00:00:00", required = true)
        @NotNull(message = "Başlangıç tarihi seçilmelidir")
        @Future(message = "Başlangıç tarihi gelecekte olmalıdır")
        LocalDateTime startDate,

        @Schema(description = "Bitiş tarihi", example = "2024-12-31T23:59:59", required = true)
        @NotNull(message = "Bitiş tarihi seçilmelidir")
        LocalDateTime endDate,

        @Schema(description = "Uygulanacak ürün ID'leri", example = "[1, 2, 3]")
        List<Long> productIds,

        @Schema(description = "Uygulanacak bayi ID'leri", example = "[1, 2]")
        List<Long> dealerIds,

        @Schema(description = "İndirim aktif mi?", example = "true")
        Boolean isActive,

        @Schema(description = "Minimum sipariş tutarı", example = "100.00")
        @DecimalMin(value = "0", message = "Minimum tutar negatif olamaz")
        BigDecimal minimumOrderAmount,

        @Schema(description = "Maksimum indirim tutarı", example = "500.00")
        @DecimalMin(value = "0", message = "Maksimum tutar negatif olamaz")
        BigDecimal maximumDiscountAmount,

        // ✅ YENİ EKLENEN - Kullanım limiti
        @Schema(description = "Toplam kullanım limiti (null=sınırsız)", example = "100")
        @Min(value = 1, message = "Kullanım limiti 1'den küçük olamaz")
        Integer usageLimit,

        // ✅ YENİ EKLENEN - Kişi başı kullanım limiti
        @Schema(description = "Kişi başı kullanım limiti (null=sınırsız)", example = "1")
        @Min(value = 1, message = "Kişi başı kullanım limiti 1'den küçük olamaz")
        Integer usageLimitPerCustomer

) {
    // Custom validation
    public void validate() {
        if (endDate != null && startDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Bitiş tarihi başlangıç tarihinden önce olamaz");
        }

        if (discountType == DiscountType.PERCENTAGE.getDisplayName()) {
            if (discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("Yüzde indirim 100'den büyük olamaz");
            }
        }

        if (productIds == null && dealerIds == null) {
            throw new IllegalArgumentException("En az bir ürün veya bayi seçilmelidir");
        }

        if (minimumOrderAmount != null && maximumDiscountAmount != null &&
                minimumOrderAmount.compareTo(maximumDiscountAmount) > 0) {
            throw new IllegalArgumentException("Minimum tutar maksimum indirim tutarından büyük olamaz");
        }

        // ✅ YENİ VALIDATION - Usage limit kontrolü
        if (usageLimit != null && usageLimit <= 0) {
            throw new IllegalArgumentException("Kullanım limiti 0'dan büyük olmalıdır");
        }

        if (usageLimitPerCustomer != null && usageLimitPerCustomer <= 0) {
            throw new IllegalArgumentException("Kişi başı kullanım limiti 0'dan büyük olmalıdır");
        }

        // Mantıksal kontrol
        if (usageLimit != null && usageLimitPerCustomer != null &&
                usageLimitPerCustomer > usageLimit) {
            throw new IllegalArgumentException("Kişi başı kullanım limiti toplam kullanım limitinden büyük olamaz");
        }
    }
}
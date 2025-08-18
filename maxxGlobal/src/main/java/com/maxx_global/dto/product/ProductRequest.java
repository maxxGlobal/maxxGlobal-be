package com.maxx_global.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Ürün oluşturma ve güncelleme için istek modeli")
public record ProductRequest(

        @Schema(description = "Ürün adı", example = "Titanyum İmplant", required = true)
        @NotBlank(message = "Ürün adı zorunludur")
        @Size(min = 2, max = 100, message = "Ürün adı 2 ile 100 karakter arasında olmalıdır")
        String name,

        @Schema(description = "Ürün kodu", example = "TI-001", required = true)
        @NotBlank(message = "Ürün kodu zorunludur")
        @Size(min = 2, max = 50, message = "Ürün kodu 2 ile 50 karakter arasında olmalıdır")
        String code,

        @Schema(description = "Ürün açıklaması", example = "Yüksek kaliteli titanyum implant")
        @Size(max = 1000, message = "Açıklama 1000 karakteri geçemez")
        String description,

        @Schema(description = "Kategori ID'si", example = "5", required = true)
        @NotNull(message = "Kategori ID'si zorunludur")
        @Min(value = 1, message = "Kategori ID'si 0'dan büyük olmalıdır")
        Long categoryId,

        @Schema(description = "Malzeme", example = "Titanyum")
        @Size(max = 100, message = "Malzeme 100 karakteri geçemez")
        String material,

        @Schema(description = "Boyut", example = "4.5mm")
        @Size(max = 50, message = "Boyut 50 karakteri geçemez")
        String size,

        @Schema(description = "Çap", example = "6.0mm")
        @Size(max = 50, message = "Çap 50 karakteri geçemez")
        String diameter,

        @Schema(description = "Açı", example = "30°")
        @Size(max = 50, message = "Açı 50 karakteri geçemez")
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
        @Size(max = 50, message = "Tıbbi cihaz sınıfı 50 karakteri geçemez")
        String medicalDeviceClass,

        @Schema(description = "Düzenleyici numarası", example = "REG-2024-001")
        @Size(max = 100, message = "Düzenleyici numarası 100 karakteri geçemez")
        String regulatoryNumber,

        @Schema(description = "Ağırlık (gram)", example = "15.5")
        @DecimalMin(value = "0", message = "Ağırlık pozitif olmalıdır")
        @Digits(integer = 8, fraction = 2, message = "Ağırlık formatı geçersiz")
        BigDecimal weightGrams,

        @Schema(description = "Boyutlar", example = "10x15x20mm")
        @Size(max = 100, message = "Boyutlar 100 karakteri geçemez")
        String dimensions,

        @Schema(description = "Renk", example = "Gümüş")
        @Size(max = 50, message = "Renk 50 karakteri geçemez")
        String color,

        @Schema(description = "Yüzey işlemi", example = "Anodize")
        @Size(max = 100, message = "Yüzey işlemi 100 karakteri geçemez")
        String surfaceTreatment,

        @Schema(description = "Seri numarası", example = "SN-2024-001")
        @Size(max = 100, message = "Seri numarası 100 karakteri geçemez")
        String serialNumber,

        @Schema(description = "Üretici kodu", example = "MFG-001")
        @Size(max = 100, message = "Üretici kodu 100 karakteri geçemez")
        String manufacturerCode,

        @Schema(description = "Üretim tarihi", example = "2024-01-15")
        LocalDate manufacturingDate,

        @Schema(description = "Son kullanma tarihi", example = "2027-01-15")
        LocalDate expiryDate,

        @Schema(description = "Raf ömrü (ay)", example = "36")
        @Min(value = 1, message = "Raf ömrü en az 1 ay olmalıdır")
        @Max(value = 120, message = "Raf ömrü 120 ayı geçemez")
        Integer shelfLifeMonths,

        @Schema(description = "Birim", example = "adet")
        @Size(max = 20, message = "Birim 20 karakteri geçemez")
        String unit,

        @Schema(description = "Barkod", example = "1234567890123")
        @Size(max = 50, message = "Barkod 50 karakteri geçemez")
        String barcode,

        @Schema(description = "Lot numarası", example = "LOT-2024-001", required = true)
        @NotBlank(message = "Lot numarası zorunludur")
        @Size(max = 100, message = "Lot numarası 100 karakteri geçemez")
        String lotNumber,

        @Schema(description = "Stok miktarı", example = "100")
        @Min(value = 0, message = "Stok miktarı negatif olamaz")
        Integer stockQuantity,

        @Schema(description = "Minimum sipariş miktarı", example = "1")
        @Min(value = 1, message = "Minimum sipariş miktarı en az 1 olmalıdır")
        Integer minimumOrderQuantity,

        @Schema(description = "Maksimum sipariş miktarı", example = "1000")
        @Min(value = 1, message = "Maksimum sipariş miktarı en az 1 olmalıdır")
        Integer maximumOrderQuantity,

        // ===== YENİ EKLENEN RESIM ALANLARI =====

        @Schema(description = "Ürün resimleri (maksimum 10 adet)",
                type = "array",
                format = "binary")
        List<MultipartFile> images,

        @Schema(description = "Ana resim olarak işaretlenecek resmin index'i (0'dan başlar)",
                example = "0")
        @Min(value = 0, message = "Ana resim index'i 0'dan küçük olamaz")
        Integer primaryImageIndex

) {

        // Custom validation
        public void validate() {
                if (expiryDate != null && manufacturingDate != null &&
                        expiryDate.isBefore(manufacturingDate)) {
                        throw new IllegalArgumentException("Son kullanma tarihi üretim tarihinden önce olamaz");
                }

                if (minimumOrderQuantity != null && maximumOrderQuantity != null &&
                        minimumOrderQuantity > maximumOrderQuantity) {
                        throw new IllegalArgumentException("Minimum sipariş miktarı maksimum sipariş miktarından büyük olamaz");
                }

                if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
                        throw new IllegalArgumentException("Son kullanma tarihi geçmişte olamaz");
                }

                // Resim validasyonları
                validateImages();
        }

        private void validateImages() {
                if (images != null && !images.isEmpty()) {
                        // Maksimum resim sayısı kontrolü (10 adet)
                        if (images.size() > 10) {
                                throw new IllegalArgumentException("Maksimum 10 adet resim yükleyebilirsiniz");
                        }

                        // Primary image index kontrolü
                        if (primaryImageIndex != null && primaryImageIndex >= images.size()) {
                                throw new IllegalArgumentException("Ana resim index'i geçersiz. Yüklenen resim sayısından küçük olmalıdır");
                        }

                        // Her resmi kontrol et
                        for (int i = 0; i < images.size(); i++) {
                                MultipartFile image = images.get(i);

                                if (image.isEmpty()) {
                                        throw new IllegalArgumentException("Boş resim dosyası yüklenemez (Index: " + i + ")");
                                }

                                // Dosya boyutu kontrolü (5MB)
                                if (image.getSize() > 5 * 1024 * 1024) {
                                        throw new IllegalArgumentException("Resim dosyası 5MB'dan büyük olamaz (Index: " + i + ")");
                                }

                                // Dosya tipi kontrolü
                                String contentType = image.getContentType();
                                if (contentType == null || !isValidImageType(contentType)) {
                                        throw new IllegalArgumentException("Geçersiz resim formatı. Sadece JPG, JPEG, PNG, GIF, WEBP desteklenir (Index: " + i + ")");
                                }

                                // Dosya uzantısı kontrolü
                                String originalFilename = image.getOriginalFilename();
                                if (originalFilename == null || !hasValidImageExtension(originalFilename)) {
                                        throw new IllegalArgumentException("Geçersiz dosya uzantısı (Index: " + i + ")");
                                }
                        }
                }
        }

        private boolean isValidImageType(String contentType) {
                return contentType.equals("image/jpeg") ||
                        contentType.equals("image/jpg") ||
                        contentType.equals("image/png") ||
                        contentType.equals("image/gif") ||
                        contentType.equals("image/webp");
        }

        private boolean hasValidImageExtension(String filename) {
                String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
                return extension.matches("jpg|jpeg|png|gif|webp");
        }
}
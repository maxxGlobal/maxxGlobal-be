package com.maxx_global.dto.productVariant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Ürün varyant oluşturma/güncelleme request - ID'ler otomatik atanır")
public record ProductVariantRequest(
        @Schema(description = "Varyant ID'si (sadece güncelleme için - yeni kayıtta gönderilmemeli)", example = "1")
        Long id,

        @Schema(description = "Boyut", example = "M", required = true)
        @NotBlank(message = "Boyut gereklidir")
        @Size(max = 50, message = "Boyut 50 karakteri geçemez")
        String size,

        @Schema(description = "SKU kodu", example = "TI-001-M")
        String sku,

        @Schema(description = "Stok miktarı (bu varyant için)", example = "100")
        @Min(value = 0, message = "Stok negatif olamaz")
        Integer stockQuantity,

        @Schema(description = "Varsayılan varyant mı?", example = "true")
        Boolean isDefault

        // ⚠️ Fiyatlar ProductPriceExcelService üzerinden atanacak
        // Kayıt sırasında fiyat bilgisi gönderilmeyecek
) {}
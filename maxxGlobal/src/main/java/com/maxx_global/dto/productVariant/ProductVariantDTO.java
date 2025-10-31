package com.maxx_global.dto.productVariant;

import com.maxx_global.dto.productPrice.ProductPriceInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Ürün varyant bilgisi - her varyantın kendi stoğu ve fiyatı var")
public record ProductVariantDTO(
        @Schema(description = "Varyant ID'si", example = "1")
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
        Boolean isDefault,

        @Schema(description = "Bu varyantın fiyatları (dealer bazlı)")
        List<ProductPriceInfo> prices
) {}
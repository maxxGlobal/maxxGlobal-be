package com.maxx_global.dto.productVariant;

import com.maxx_global.dto.productPrice.ProductPriceInfo;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.ProductPrice;
import com.maxx_global.entity.ProductVariant;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.EntityStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProductVariantMapper {

    /**
     * ProductVariant entity -> ProductVariantDTO
     * @param variant Entity
     * @param dealerId Belirli bir dealer için fiyatları filtrelemek için (opsiyonel)
     * @return DTO
     */
    public ProductVariantDTO toDto(ProductVariant variant, boolean includePrices, Long dealerId, CurrencyType currency) {
        if (variant == null) {
            return null;
        }

        // Fiyatları filtrele (dealerId varsa sadece o dealer'ın fiyatlarını al)
        // includePrices false olsa bile productPriceId'yi göndermek için fiyatları map et
        List<ProductPriceInfo> priceInfos = mapPrices(variant, dealerId, currency, includePrices);

        return new ProductVariantDTO(
                variant.getId(),
                variant.getSize(),
                variant.getSku(),
                variant.getStockQuantity(),
                variant.getIsDefault(),
                priceInfos
        );
    }

    public ProductVariantDTO toDto(ProductVariant variant, Long dealerId, CurrencyType currency) {
        return toDto(variant, true, dealerId, currency);
    }
    /**
     * ProductVariant entity -> ProductVariantDTO (tüm fiyatlar)
     */
    public ProductVariantDTO toDto(ProductVariant variant) {
        return toDto(variant, null,null);
    }

    /**
     * ✅ ProductVariantRequest -> ProductVariant entity (YENİ - Request için)
     * NOT: Product ilişkisi service katmanında set edilmelidir
     * NOT: Fiyatlar ayrı olarak ProductPrice entity'leri olarak oluşturulacak
     */
    public ProductVariant toEntity(ProductVariantRequest request) {
        if (request == null) {
            return null;
        }

        ProductVariant variant = new ProductVariant();
        // ⚠️ ID'yi sadece update işlemlerinde set et (create'de null olmalı)
        // Service katmanında ID kontrolü yapılacak
        if (request.id() != null) {
            variant.setId(request.id());
        }

        variant.setSize(request.size());
        variant.setSku(request.sku());
        variant.setStockQuantity(request.stockQuantity() != null ? request.stockQuantity() : 0);
        variant.setIsDefault(request.isDefault() != null ? request.isDefault() : false);

        // Product ilişkisi service katmanında set edilecek
        // Prices ayrı olarak handle edilecek (createPricesFromRequest metoduyla)

        return variant;
    }

    /**
     * @deprecated ProductVariantDTO yerine ProductVariantRequest kullanın
     * ProductVariantDTO -> ProductVariant entity
     * NOT: Product ilişkisi service katmanında set edilmelidir
     */
    @Deprecated
    public ProductVariant toEntity(ProductVariantDTO dto) {
        if (dto == null) {
            return null;
        }

        ProductVariant variant = new ProductVariant();
        variant.setId(dto.id());
        variant.setSize(dto.size());
        variant.setSku(dto.sku());
        variant.setStockQuantity(dto.stockQuantity() != null ? dto.stockQuantity() : 0);
        variant.setIsDefault(dto.isDefault() != null ? dto.isDefault() : false);

        // Product ilişkisi service katmanında set edilecek
        // Prices da ayrı olarak handle edilecek

        return variant;
    }

    /**
     * ✅ Request bilgilerini mevcut entity'ye uygular (update için - YENİ)
     */
    public void updateEntity(ProductVariant existingVariant, ProductVariantRequest request) {
        if (existingVariant == null || request == null) {
            return;
        }

        existingVariant.setSize(request.size());

        // SKU güncelleme - dikkatli olmak gerekir çünkü unique
        if (request.sku() != null && !request.sku().equals(existingVariant.getSku())) {
            existingVariant.setSku(request.sku());
        }

        if (request.stockQuantity() != null) {
            existingVariant.setStockQuantity(request.stockQuantity());
        }

        if (request.isDefault() != null) {
            existingVariant.setIsDefault(request.isDefault());
        }

        // Prices ayrı handle edilecek (service katmanında - createPricesFromRequest ile)
    }

    /**
     * @deprecated ProductVariantRequest kullanın
     * DTO bilgilerini mevcut entity'ye uygular (update için)
     */
    @Deprecated
    public void updateEntity(ProductVariant existingVariant, ProductVariantDTO dto) {
        if (existingVariant == null || dto == null) {
            return;
        }

        existingVariant.setSize(dto.size());

        // SKU güncelleme - dikkatli olmak gerekir çünkü unique
        if (dto.sku() != null && !dto.sku().equals(existingVariant.getSku())) {
            existingVariant.setSku(dto.sku());
        }

        if (dto.stockQuantity() != null) {
            existingVariant.setStockQuantity(dto.stockQuantity());
        }

        if (dto.isDefault() != null) {
            existingVariant.setIsDefault(dto.isDefault());
        }

        // Prices ayrı handle edilecek (service katmanında)
    }

    /**
     * ✅ VariantPriceRequest listesinden ProductPrice entity'leri oluşturur
     * NOT: ProductVariant ilişkisi service katmanında set edilmelidir
     */
    public List<ProductPrice> createPricesFromRequest(List<VariantPriceRequest> priceRequests, ProductVariant variant) {
        if (priceRequests == null || priceRequests.isEmpty()) {
            return Collections.emptyList();
        }

        return priceRequests.stream()
                .map(priceRequest -> {
                    ProductPrice price = new ProductPrice();
                    price.setProductVariant(variant);
                    price.setCurrency(priceRequest.currency());
                    price.setAmount(priceRequest.amount());
                    price.setValidFrom(priceRequest.validFrom() != null ? priceRequest.validFrom() : LocalDateTime.now());
                    price.setValidUntil(priceRequest.validTo()); // ✅ Entity'de validUntil olarak tanımlı
                    price.setIsActive(true);
                    price.setStatus(EntityStatus.ACTIVE);

                    // Dealer set et (varsa)
                    if (priceRequest.dealerId() != null) {
                        Dealer dealer = new Dealer();
                        dealer.setId(priceRequest.dealerId());
                        price.setDealer(dealer);
                    }

                    return price;
                })
                .collect(Collectors.toList());
    }

    /**
     * Variant'ın fiyatlarını ProductPriceInfo listesine çevirir
     * includePriceAmounts false ise sadece ID gönderilir, amount null olur
     */
    private List<ProductPriceInfo> mapPrices(ProductVariant variant, Long dealerId, CurrencyType currency, boolean includePriceAmounts) {
        if (variant.getPrices() == null || variant.getPrices().isEmpty()) {
            return Collections.emptyList();
        }

        return variant.getPrices().stream()
                // Dealer filtresi (varsa)
                .filter(price -> dealerId == null ||
                        (price.getDealer() != null && price.getDealer().getId().equals(dealerId)))
                // Sadece aktif ve geçerli fiyatlar
                .filter(ProductPrice::isValidNow)
                .filter(price -> currency == null || price.getCurrency() == currency)
                .map(price -> new ProductPriceInfo(
                        price.getId(),
                        price.getCurrency(),
                        includePriceAmounts ? price.getAmount() : null  // Fiyat yetkisi yoksa amount null
                ))
                .collect(Collectors.toList());
    }

    /**
     * Variant listesini DTO listesine çevirir
     */
    public List<ProductVariantDTO> toDtoList(List<ProductVariant> variants, boolean includePrices, Long dealerId, CurrencyType currency) {
        if (variants == null || variants.isEmpty()) {
            return Collections.emptyList();
        }

        return variants.stream()
                .map(variant -> toDto(variant, includePrices, dealerId, currency))
                .collect(Collectors.toList());
    }

    public List<ProductVariantDTO> toDtoList(List<ProductVariant> variants, Long dealerId, CurrencyType currency) {
        return toDtoList(variants, true, dealerId, currency);
    }

    /**
     * Variant listesini DTO listesine çevirir (tüm fiyatlar)
     */
    public List<ProductVariantDTO> toDtoList(List<ProductVariant> variants) {
        return toDtoList(variants, true, null, null);
    }
}
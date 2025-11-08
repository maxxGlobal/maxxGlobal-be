package com.maxx_global.dto.productPrice;

import com.maxx_global.entity.ProductPrice;
import com.maxx_global.entity.Product;
import com.maxx_global.entity.ProductVariant;
import com.maxx_global.entity.Dealer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProductPriceMapper {

    // Request -> Entity (YENİ - ProductVariant bazlı)
    public ProductPrice toEntity(ProductPriceRequest request) {
        ProductPrice price = new ProductPrice();

        // ProductVariant ve Dealer set edilecek (service katmanında)
        if (request.productVariantId() != null) {
            ProductVariant variant = new ProductVariant();
            variant.setId(request.productVariantId());
            price.setProductVariant(variant);
        }
        // Backward compatibility için eski productId'yi de destekle
        else if (request.productId() != null) {
            Product product = new Product();
            product.setId(request.productId()); 
        }

        Dealer dealer = new Dealer();
        dealer.setId(request.dealerId());
        price.setDealer(dealer);

        price.setCurrency(request.currency());
        price.setAmount(request.amount());
        price.setValidFrom(request.validFrom());
        price.setValidUntil(request.validUntil());
        price.setIsActive(request.isActive() != null ? request.isActive() : true);

        return price;
    }

    // PriceGroup -> Response (GÜNCELLEME - Variant bilgileri eklendi)
    public ProductPriceResponse toResponse(PriceGroup priceGroup) {
        if (priceGroup.prices().isEmpty()) {
            throw new IllegalArgumentException("PriceGroup cannot be empty");
        }

        ProductPrice mainPrice = priceGroup.getMainPrice();

        // Para birimlerine göre fiyat listesi oluştur
        List<PriceInfo> priceInfos = priceGroup.prices().stream()
                .map(price -> new PriceInfo( price.getCurrency(), price.getAmount()))
                .collect(Collectors.toList());

        // Variant bilgilerini al (varsa)
        Long variantId = null;
        String variantSku = null;
        String variantSize = null;
        if (mainPrice.getProductVariant() != null) {
            variantId = mainPrice.getProductVariant().getId();
            variantSku = mainPrice.getProductVariant().getSku();
            variantSize = mainPrice.getProductVariant().getSize();
        }

        return new ProductPriceResponse(
                mainPrice.getId(), // Ana fiyatın ID'si grup ID'si olur
                priceGroup.productId(), // Deprecated
                variantId, // YENİ
                variantSku, // YENİ
                variantSize, // YENİ
                priceGroup.productName(),
                priceGroup.productCode(),
                priceGroup.dealerId(),
                priceGroup.dealerName(),
                priceInfos, // Gruplu fiyatlar
                mainPrice.getValidFrom(),
                mainPrice.getValidUntil(),
                mainPrice.getIsActive(),
                mainPrice.isValidNow(),
                mainPrice.getCreatedAt(),
                mainPrice.getUpdatedAt(),
                mainPrice.getStatus().name()
        );
    }

    // Entity -> Response (GÜNCELLEME - Variant desteği eklendi)
    public ProductPriceResponse toResponseSingle(ProductPrice price) {
        List<PriceInfo> priceInfos = List.of(
                new PriceInfo( price.getCurrency(), price.getAmount())
        );

        // Ürün bilgilerini al (variant üzerinden veya direkt product'tan)
        ProductVariant variant = price.getProductVariant();
        Product relatedProduct = variant.getProduct();

        Long productId = relatedProduct != null ? relatedProduct.getId() : null;
        String productName = relatedProduct != null ? relatedProduct.getName() : null;
        String productCode = relatedProduct != null ? relatedProduct.getCode() : null;

        Long variantId = variant != null ? variant.getId() : null;
        String variantSku = variant != null ? variant.getSku() : null;
        String variantSize = variant != null ? variant.getSize() : null;

        return new ProductPriceResponse(
                price.getId(),
                productId, // Deprecated
                variantId, // YENİ
                variantSku, // YENİ
                variantSize, // YENİ
                productName,
                productCode,
                price.getDealer().getId(),
                price.getDealer().getName(),
                priceInfos,
                price.getValidFrom(),
                price.getValidUntil(),
                price.getIsActive(),
                price.isValidNow(),
                price.getCreatedAt(),
                price.getUpdatedAt(),
                price.getStatus().name()
        );
    }

//    // Entity -> Summary (GÜNCELLEME - Variant desteği eklendi)
//    public ProductPriceSummary toSummary(ProductPrice price) {
//        // Ürün adını al (variant üzerinden veya direkt product'tan)
//        Product relatedProduct = price.getRelatedProduct();
//        String productName = relatedProduct != null ? relatedProduct.getName() : null;
//
//        // Eğer variant varsa, variant'ın display name'ini kullan
//        if (price.getProductVariant() != null) {
//            productName = price.getProductVariant().getDisplayName();
//        }
//
//        return new ProductPriceSummary(
//                price.getId(),
//                productName,
//                price.getDealer().getName(),
//                price.getCurrency(),
//                price.getAmount(),
//                price.isValidNow()
//        );
//    }
}
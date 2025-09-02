package com.maxx_global.dto.productPrice;

import com.maxx_global.entity.ProductPrice;
import com.maxx_global.entity.Product;
import com.maxx_global.entity.Dealer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProductPriceMapper {

    // Request -> Entity
    public ProductPrice toEntity(ProductPriceRequest request) {
        ProductPrice price = new ProductPrice();

        // Product ve Dealer set edilecek (service katmanında)
        Product product = new Product();
        product.setId(request.productId());
        price.setProduct(product);

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

    // PriceGroup -> Response (NEW METHOD)
    public ProductPriceResponse toResponse(PriceGroup priceGroup) {
        if (priceGroup.prices().isEmpty()) {
            throw new IllegalArgumentException("PriceGroup cannot be empty");
        }

        ProductPrice mainPrice = priceGroup.getMainPrice();

        // Para birimlerine göre fiyat listesi oluştur
        List<PriceInfo> priceInfos = priceGroup.prices().stream()
                .map(price -> new PriceInfo(price.getCurrency(), price.getAmount()))
                .collect(Collectors.toList());

        return new ProductPriceResponse(
                mainPrice.getId(), // Ana fiyatın ID'si grup ID'si olur
                priceGroup.productId(),
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

    // Entity -> Response (ESKİ METHOD - tek fiyat için)
    public ProductPriceResponse toResponseSingle(ProductPrice price) {
        List<PriceInfo> priceInfos = List.of(
                new PriceInfo(price.getCurrency(), price.getAmount())
        );

        return new ProductPriceResponse(
                price.getId(),
                price.getProduct().getId(),
                price.getProduct().getName(),
                price.getProduct().getCode(),
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

    // Entity -> Summary
    public ProductPriceSummary toSummary(ProductPrice price) {
        return new ProductPriceSummary(
                price.getId(),
                price.getProduct().getName(),
                price.getDealer().getName(),
                price.getCurrency(),
                price.getAmount(),
                price.isValidNow()
        );
    }
}
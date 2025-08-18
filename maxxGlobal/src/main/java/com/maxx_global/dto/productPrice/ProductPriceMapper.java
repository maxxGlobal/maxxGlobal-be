package com.maxx_global.dto.productPrice;

import com.maxx_global.dto.productPrice.ProductPriceRequest;
import com.maxx_global.dto.productPrice.ProductPriceResponse;
import com.maxx_global.dto.productPrice.ProductPriceSummary;
import com.maxx_global.entity.ProductPrice;
import com.maxx_global.entity.Product;
import com.maxx_global.entity.Dealer;
import org.springframework.stereotype.Component;

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

    // Entity -> Response
    public ProductPriceResponse toResponse(ProductPrice price) {
        return new ProductPriceResponse(
                price.getId(),
                price.getProduct().getId(),
                price.getProduct().getName(),
                price.getProduct().getCode(),
                price.getDealer().getId(),
                price.getDealer().getName(),
                price.getCurrency(),
                price.getAmount(),
                price.getValidFrom(),
                price.getValidUntil(),
                price.getIsActive(),
                price.isValidNow(), // Business method
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
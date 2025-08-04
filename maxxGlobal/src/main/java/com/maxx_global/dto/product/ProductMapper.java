package com.maxx_global.dto.product;

import com.maxx_global.dto.BaseMapper;
import com.maxx_global.dto.productImage.ProductImageResponse;
import com.maxx_global.dto.productPrice.ProductPriceResponse;
import com.maxx_global.entity.Product;
import com.maxx_global.entity.ProductImage;
import com.maxx_global.entity.ProductPrice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ProductMapper extends BaseMapper<Product, ProductRequest, ProductResponse> {

    // Product -> ProductResponse
    @Override
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "images", source = "images", qualifiedByName = "mapImageSet")
    @Mapping(target = "prices", source = "prices", qualifiedByName = "mapPriceSet")
    ProductResponse toDto(Product product);

    // ProductRequest -> Product
    @Override
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "images", ignore = true)  // Serviste set edilecek
    @Mapping(target = "prices", ignore = true)  // Serviste set edilecek
    Product toEntity(ProductRequest request);

    // Images mapping
    @Named("mapImageSet")
    default Set<ProductImageResponse> mapImageSet(Set<ProductImage> images) {
        if (images == null) return Collections.emptySet();
        return images.stream()
                .map(img -> new ProductImageResponse(
                        img.getImageUrl(),
                        img.getIsPrimary()
                ))
                .collect(Collectors.toSet());
    }

    @Named("mapPriceSet")
    default Set<ProductPriceResponse> mapPriceSet(Set<ProductPrice> prices) {
        if (prices == null) return Collections.emptySet();
        return prices.stream()
                .map(price -> new ProductPriceResponse(
                        price.getId(),
                        price.getCurrency(),
                        price.getPriceType(),
                        price.getAmount()
                ))
                .collect(Collectors.toSet());
    }


}




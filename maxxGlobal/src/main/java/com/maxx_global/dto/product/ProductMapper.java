package com.maxx_global.dto.product;

import com.maxx_global.entity.Product;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductResponse toDto(Product product);
    Product toEntity(ProductCreateRequest dto);
}

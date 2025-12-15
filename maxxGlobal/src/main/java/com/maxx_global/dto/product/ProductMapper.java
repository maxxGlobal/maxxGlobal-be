package com.maxx_global.dto.product;

import com.maxx_global.dto.BaseMapper;
import com.maxx_global.dto.productImage.ProductImageInfo;
import com.maxx_global.entity.Product;
import com.maxx_global.entity.ProductImage;
import com.maxx_global.entity.Category;
import com.maxx_global.enums.EntityStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ProductMapper extends BaseMapper<Product, ProductRequest, ProductResponse> {

    // Product -> ProductResponse
    @Override
    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "nameEn", ignore = true)
    @Mapping(target = "descriptionEn", ignore = true)
    @Mapping(target = "images", source = "images", qualifiedByName = "mapImageSet")
    @Mapping(target = "primaryImageUrl", source = "images", qualifiedByName = "findPrimaryImageUrl")
    @Mapping(target = "isActive", source = "status", qualifiedByName = "mapStatusToActive")
    @Mapping(target = "isInStock", source = ".", qualifiedByName = "mapIsInStock")
    @Mapping(target = "isExpired", source = ".", qualifiedByName = "mapIsExpired")
    @Mapping(target = "createdDate", source = "createdAt")
    @Mapping(target = "updatedDate", source = "updatedAt")
    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusToDisplayName")
    @Mapping(target = "isFavorite", ignore = true) // Serviste set edilecek
    @Mapping(target = "variants", ignore = true) // Serviste ProductVariantMapper ile set edilecek
    ProductResponse toDto(Product product);

    // ProductRequest -> Product (for CREATE operations)
    @Override
    @Mapping(target = "category", ignore = true) // Serviste set edilecek
    @Mapping(target = "images", ignore = true)   // Serviste set edilecek
    @Mapping(target = "variants", ignore = true) // Serviste ProductVariantMapper ile set edilecek
    @Mapping(target = "status", ignore = true)   // Serviste set edilecek
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Product toEntity(ProductRequest request);

    // Product -> ProductSummary
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "primaryImageUrl", source = "images", qualifiedByName = "findPrimaryImageUrl")
    @Mapping(target = "isActive", source = "status", qualifiedByName = "mapStatusToActive")
    @Mapping(target = "isInStock", source = ".", qualifiedByName = "mapIsInStock")
    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusToDisplayName")
    @Mapping(target = "isFavorite", ignore = true) // Serviste set edilecek
    ProductSummary toSummary(Product product);

    // Update existing entity (for PUT operations)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true) // Serviste manuel set edilecek
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "variants", ignore = true) // Serviste ProductVariantMapper ile set edilecek
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(@MappingTarget Product existingProduct, ProductRequest request);

    // Helper mapping methods
    @Named("mapImageSet")
    default List<ProductImageInfo> mapImageSet(Set<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        return images.stream()
                .map(img -> new ProductImageInfo(
                        img.getId(),
                        img.getImageUrl(),
                        img.getIsPrimary()
                ))
                .collect(Collectors.toList());
    }

    @Named("findPrimaryImageUrl")
    default String findPrimaryImageUrl(Set<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }

        return images.stream()
                .filter(ProductImage::getIsPrimary)
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElse(
                        // Eğer primary image yoksa, ilk resmi al
                        images.stream()
                                .map(ProductImage::getImageUrl)
                                .findFirst()
                                .orElse(null)
                );
    }

    @Named("mapStatusToActive")
    default Boolean mapStatusToActive(com.maxx_global.enums.EntityStatus status) {
        return status != null && status.name().equals("ACTIVE");
    }

    @Named("mapStatusToString")
    default String mapStatusToString(com.maxx_global.enums.EntityStatus status) {
        return status != null ? status.name() : null;
    }

    @Named("mapIsInStock")
    default Boolean mapIsInStock(Product product) {
        return product.isInStock(); // Business method
    }

    @Named("mapIsExpired")
    default Boolean mapIsExpired(Product product) {
        return product.isExpired(); // Business method
    }

    // Category mapping helper
    default Category mapCategoryId(Long categoryId) {
        if (categoryId == null) return null;
        Category category = new Category();
        category.setId(categoryId);
        return category;
    }

    @Named("mapStatusToDisplayName")
    default String mapStatusToDisplayName(EntityStatus status) {
        return status != null ? status.getDisplayName() : null;
    }
}
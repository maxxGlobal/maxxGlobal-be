package com.maxx_global.dto.stock;

import com.maxx_global.dto.BaseMapper;
import com.maxx_global.dto.appUser.UserSummary;
import com.maxx_global.dto.product.ProductSummary;
import com.maxx_global.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface StockMovementMapper extends BaseMapper<StockMovement, StockMovementRequest, StockMovementResponse> {

    @Override
    @Mapping(target = "product", source = "product", qualifiedByName = "mapProductSummary")
    @Mapping(target = "movementType", source = "movementType", qualifiedByName = "mapMovementTypeDisplayName")
    @Mapping(target = "movementTypeCode", source = "movementType", qualifiedByName = "mapMovementTypeCode")
    @Mapping(target = "performedBy", source = "performedBy", qualifiedByName = "mapPerformedByUser")
    @Mapping(target = "createdDate", source = "createdAt")
    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusToDisplayName")
    StockMovementResponse toDto(StockMovement stockMovement);

    @Override
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "performedBy", ignore = true)
    @Mapping(target = "movementDate", ignore = true)
    @Mapping(target = "previousStock", ignore = true)
    @Mapping(target = "newStock", ignore = true)
    @Mapping(target = "status", ignore = true)
    StockMovement toEntity(StockMovementRequest request);

    @Named("mapProductSummary")
    default ProductSummary mapProductSummary(Product product) {
        if (product == null) return null;
        return new ProductSummary(
                product.getId(),
                product.getName(),
                product.getCode(),
                product.getCategory() != null ? product.getCategory().getName() : null,
                null, // primaryImageUrl
                product.getStockQuantity(),
                product.getUnit(),
                product.getStatus() == com.maxx_global.enums.EntityStatus.ACTIVE,
                product.isInStock(),
                product.getStatus().getDisplayName(),
                false, // isFavorite
                null
        );
    }

    @Named("mapPerformedByUser")
    default UserSummary mapPerformedByUser(Long performedBy) {
        // Bu method'u service katmanında implement etmek daha uygun olacak
        // Şimdilik sadece ID ile basit bir summary döndürüyoruz
        if (performedBy == null) return null;
        return new UserSummary(performedBy, "Kullanıcı", "", "");
    }

    @Named("mapMovementTypeDisplayName")
    default String mapMovementTypeDisplayName(com.maxx_global.enums.StockMovementType movementType) {
        return movementType != null ? movementType.getDisplayName() : null;
    }

    @Named("mapMovementTypeCode")
    default String mapMovementTypeCode(com.maxx_global.enums.StockMovementType movementType) {
        return movementType != null ? movementType.name() : null;
    }

    @Named("mapStatusToDisplayName")
    default String mapStatusToDisplayName(com.maxx_global.enums.EntityStatus status) {
        return status != null ? status.getDisplayName() : null;
    }
}
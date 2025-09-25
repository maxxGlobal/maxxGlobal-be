package com.maxx_global.dto.discount;

import com.maxx_global.dto.category.CategorySummary;
import com.maxx_global.dto.dealer.DealerSummary;
import com.maxx_global.dto.product.ProductSummary;
import com.maxx_global.entity.Category;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Discount;
import com.maxx_global.entity.Product;
import com.maxx_global.enums.DiscountType;
import org.mapstruct.*;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface DiscountMapper {

    // ==================== TO DTO ====================

    @Mapping(target = "applicableProducts", source = "applicableProducts", qualifiedByName = "mapProductsToSummary")
    @Mapping(target = "applicableDealers", source = "applicableDealers", qualifiedByName = "mapDealersToSummary")
    @Mapping(target = "applicableCategories", source = "applicableCategories", qualifiedByName = "mapCategoriesToSummary")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "updatedBy", source = "updatedBy")

    // Computed fields
    @Mapping(target = "discountScope", source = ".", qualifiedByName = "mapDiscountScope")
    @Mapping(target = "discountTypeDisplay", source = ".", qualifiedByName = "mapDiscountTypeDisplay")
    @Mapping(target = "isExpired", source = ".", qualifiedByName = "mapIsExpired")
    @Mapping(target = "isNotYetStarted", source = ".", qualifiedByName = "mapIsNotYetStarted")
    @Mapping(target = "hasUsageLeft", source = ".", qualifiedByName = "mapHasUsageLeft")
    @Mapping(target = "remainingUsage", source = ".", qualifiedByName = "mapRemainingUsage")
    @Mapping(target = "validityStatus", source = ".", qualifiedByName = "mapValidityStatus")
    @Mapping(target = "isValidNow", source = ".", qualifiedByName = "mapIsValidNowSafely")

    // Category specific fields
    @Mapping(target = "isCategoryBased", source = ".", qualifiedByName = "mapIsCategoryBased")
    @Mapping(target = "isProductBased", source = ".", qualifiedByName = "mapIsProductBased")
    @Mapping(target = "isDealerBased", source = ".", qualifiedByName = "mapIsDealerBased")
    @Mapping(target = "isGeneralDiscount", source = ".", qualifiedByName = "mapIsGeneralDiscount")
    DiscountResponse toDto(Discount discount);

    // ==================== TO ENTITY ====================

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "applicableProducts", ignore = true)
    @Mapping(target = "applicableDealers", ignore = true)
    @Mapping(target = "applicableCategories", ignore = true)
    @Mapping(target = "usageCount", constant = "0")
    @Mapping(target = "discountType", source = "discountType", qualifiedByName = "mapStringToDiscountType")
    Discount toEntity(DiscountRequest request);

    // ==================== TO SUMMARY ====================

    @Mapping(target = "isValidNow", source = ".", qualifiedByName = "mapIsValidNow")
    DiscountSummary toSummary(Discount discount);

    // ==================== MAPPING METHODS ====================

    @Named("mapProductsToSummary")
    default List<ProductSummary> mapProductsToSummary(Set<Product> products) {
        if (products == null) return null;
        return products.stream()
                .map(p -> new ProductSummary(
                        p.getId(),
                        p.getName(),
                        p.getCode(),
                        p.getCategory() != null ? p.getCategory().getName() : null, // categoryName
                        null, // primaryImageUrl - computed in service if needed
                        p.getStockQuantity(),
                        p.getUnit(),
                        p.getStatus() == com.maxx_global.enums.EntityStatus.ACTIVE, // isActive
                        p.isInStock(), // isInStock
                        p.getStatus() != null ? p.getStatus().getDisplayName() : null, // status
                        null, // isFavorite - set in service
                        null  // prices - set in service
                ))
                .toList();
    }

    @Named("mapDealersToSummary")
    default List<DealerSummary> mapDealersToSummary(Set<Dealer> dealers) {
        if (dealers == null) return null;
        return dealers.stream()
                .map(d -> new DealerSummary(d.getId(), d.getName(),
                        d.getStatus() != null ? d.getStatus().getDisplayName() : null,
                        d.getPreferredCurrency()))
                .toList();
    }

    @Named("mapCategoriesToSummary")
    default List<CategorySummary> mapCategoriesToSummary(Set<Category> categories) {
        if (categories == null) return null;
        return categories.stream()
                .map(c -> new CategorySummary(c.getId(), c.getName(), !c.getChildren().isEmpty()))
                .toList();
    }

    @Named("mapStringToDiscountType")
    default DiscountType mapStringToDiscountType(String discountType) {
        if (discountType == null) return null;
        return DiscountType.valueOf(discountType.toUpperCase());
    }

    @Named("mapDiscountScope")
    default String mapDiscountScope(Discount discount) {
        return discount.getDiscountScope();
    }

    @Named("mapDiscountTypeDisplay")
    default String mapDiscountTypeDisplay(Discount discount) {
        if (discount.getDiscountType() == DiscountType.PERCENTAGE) {
            return "%" + discount.getDiscountValue().stripTrailingZeros().toPlainString();
        } else {
            return discount.getDiscountValue().stripTrailingZeros().toPlainString() + " TL";
        }
    }

    @Named("mapIsExpired")
    default Boolean mapIsExpired(Discount discount) {
        return discount.isExpired();
    }

    @Named("mapIsNotYetStarted")
    default Boolean mapIsNotYetStarted(Discount discount) {
        return discount.isNotYetStarted();
    }

    @Named("mapHasUsageLeft")
    default Boolean mapHasUsageLeft(Discount discount) {
        return discount.hasUsageLeft();
    }

    @Named("mapRemainingUsage")
    default Integer mapRemainingUsage(Discount discount) {
        if (discount.getUsageLimit() == null) return null;
        return Math.max(0, discount.getUsageLimit() - (discount.getUsageCount() != null ? discount.getUsageCount() : 0));
    }

    @Named("mapValidityStatus")
    default String mapValidityStatus(Discount discount) {
        if (discount.isExpired()) {
            return "Süresi Doldu";
        } else if (discount.isNotYetStarted()) {
            return "Henüz Başlamadı";
        } else if (!Boolean.TRUE.equals(discount.getIsActive())) {
            return "Pasif";
        } else if (!discount.hasUsageLeft()) {
            return "Kullanım Limiti Doldu";
        } else {
            return "Aktif";
        }
    }

    @Named("mapIsCategoryBased")
    default Boolean mapIsCategoryBased(Discount discount) {
        return discount.isCategoryBasedDiscount();
    }

    @Named("mapIsProductBased")
    default Boolean mapIsProductBased(Discount discount) {
        return discount.isProductBasedDiscount();
    }

    @Named("mapIsDealerBased")
    default Boolean mapIsDealerBased(Discount discount) {
        return discount.isDealerBasedDiscount();
    }

    @Named("mapIsGeneralDiscount")
    default Boolean mapIsGeneralDiscount(Discount discount) {
        return discount.isGeneralDiscount();
    }

    @Named("mapIsValidNow")
    default Boolean mapIsValidNow(Discount discount) {
        if (discount == null) {
            return false;
        }
        try {
            Boolean result = discount.isValidNow();
            return result != null ? result : false;
        } catch (Exception e) {
            return false;
        }
    }

    @Named("mapIsValidNowSafely")
    default Boolean mapIsValidNowSafely(Discount discount) {
        if (discount == null) {
            return false;
        }
        try {
            Boolean result = discount.isValidNow();
            return result != null ? result : false;
        } catch (Exception e) {
            return false;
        }
    }
}
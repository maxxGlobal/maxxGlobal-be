package com.maxx_global.dto.discount;

import com.maxx_global.dto.BaseMapper;
import com.maxx_global.dto.dealer.DealerSummary;
import com.maxx_global.dto.product.ProductSummary;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Discount;
import com.maxx_global.entity.Product;
import com.maxx_global.enums.EntityStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface DiscountMapper extends BaseMapper<Discount, DiscountRequest, DiscountResponse> {

    // Entity -> Response
    @Override
    @Mapping(target = "applicableProducts", source = "applicableProducts", qualifiedByName = "mapProductsToSummary")
    @Mapping(target = "applicableDealers", source = "applicableDealers", qualifiedByName = "mapDealersToSummary")
    @Mapping(target = "isValidNow", source = ".", qualifiedByName = "mapIsValidNow")
    @Mapping(target = "createdDate", source = "createdAt")
    @Mapping(target = "updatedDate", source = "updatedAt")
    @Mapping(target = "isActive", source = ".", qualifiedByName = "mapIsActive")
    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusToDisplayName")
    // ✅ YENİ EKLENEN - Usage limiti mappingleri
    @Mapping(target = "usageLimit", source = "usageLimit")
    @Mapping(target = "usageCount", source = "usageCount")
    @Mapping(target = "usageLimitPerCustomer", source = "usageLimitPerCustomer")
    @Mapping(target = "remainingUsage", source = ".", qualifiedByName = "mapRemainingUsage")
    @Mapping(target = "usageLimitReached", source = ".", qualifiedByName = "mapUsageLimitReached")
    DiscountResponse toDto(Discount discount);

    // Request -> Entity
    @Override
    @Mapping(target = "applicableProducts", ignore = true) // Serviste set edilecek
    @Mapping(target = "applicableDealers", ignore = true)  // Serviste set edilecek
    // ✅ YENİ EKLENEN - Usage limiti mappingleri
    @Mapping(target = "usageLimit", source = "usageLimit")
    @Mapping(target = "usageLimitPerCustomer", source = "usageLimitPerCustomer")
    @Mapping(target = "usageCount", constant = "0") // Yeni oluşturulan indirimde 0 olarak başla
    Discount toEntity(DiscountRequest request);

    // Entity -> Summary
    @Mapping(target = "isValidNow", source = ".", qualifiedByName = "mapIsValidNow")
    DiscountSummary toSummary(Discount discount);

    // Helper mapping methods
    @Named("mapProductsToSummary")
    default List<ProductSummary> mapProductsToSummary(Set<Product> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        return products.stream()
                .map(product -> new ProductSummary(
                        product.getId(),
                        product.getName(),
                        product.getCode(),
                        product.getCategory() != null ? product.getCategory().getName() : null,
                        null, // primaryImageUrl - bu mapper'da gerek yok
                        product.getStockQuantity(),
                        product.getUnit(),
                        product.getStatus().name().equals("ACTIVE"),
                        product.isInStock(),
                        product.getStatus().getDisplayName(),
                        null,
                        null // isFavorite - bu mapper'da gerek yok
                ))
                .collect(Collectors.toList());
    }

    @Named("mapDealersToSummary")
    default List<DealerSummary> mapDealersToSummary(Set<Dealer> dealers) {
        if (dealers == null || dealers.isEmpty()) {
            return List.of();
        }
        return dealers.stream()
                .map(dealer -> new DealerSummary(dealer.getId(), dealer.getName(),dealer.getStatus().getDisplayName(),dealer.getPreferredCurrency()))
                .collect(Collectors.toList());
    }

    @Named("mapIsValidNow")
    default Boolean mapIsValidNow(Discount discount) {
        if (discount.getStartDate() == null || discount.getEndDate() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean dateValid = discount.getStartDate().isBefore(now) && discount.getEndDate().isAfter(now);
        boolean usageValid = discount.hasUsageLeft(); // Entity'deki method kullan

        return dateValid && usageValid && discount.getIsActive();
    }

    @Named("mapStatusToString")
    default String mapStatusToString(com.maxx_global.enums.EntityStatus status) {
        return status != null ? status.name() : null;
    }

    @Named("mapIsActive")
    default Boolean mapIsActive(Discount discount) {
        return discount.getStatus() != null && discount.getStatus().name().equals("ACTIVE");
    }

    @Named("mapStatusToDisplayName")
    default String mapStatusToDisplayName(EntityStatus status) {
        return status != null ? status.getDisplayName() : null;
    }

    // ✅ YENİ EKLENEN - Kullanım limiti helper methodları
    @Named("mapRemainingUsage")
    default Integer mapRemainingUsage(Discount discount) {
        if (discount.getUsageLimit() == null) {
            return null; // Sınırsız kullanım
        }
        int usageCount = discount.getUsageCount() != null ? discount.getUsageCount() : 0;
        return Math.max(0, discount.getUsageLimit() - usageCount);
    }

    @Named("mapUsageLimitReached")
    default Boolean mapUsageLimitReached(Discount discount) {
        if (discount.getUsageLimit() == null) {
            return false; // Sınırsız kullanım
        }
        int usageCount = discount.getUsageCount() != null ? discount.getUsageCount() : 0;
        return usageCount >= discount.getUsageLimit();
    }
}
// OrderMapper.java - Discount mapping ile güncellenmiş mapping

package com.maxx_global.dto.order;

import com.maxx_global.dto.BaseMapper;
import com.maxx_global.dto.appUser.UserSummary;
import com.maxx_global.dto.discount.DiscountInfo;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Discount;
import com.maxx_global.entity.Order;
import com.maxx_global.entity.OrderItem;
import com.maxx_global.entity.ProductVariant;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.OrderStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface OrderMapper extends BaseMapper<Order, OrderRequest, OrderResponse> {

    @Override
    @Mapping(target = "orderNumber", source = "orderNumber")
    @Mapping(target = "dealerId", source = "order", qualifiedByName = "mapDealerId")
    @Mapping(target = "dealerName", source = "order", qualifiedByName = "mapDealerName")
    @Mapping(target = "createdBy", source = "user", qualifiedByName = "mapUserSummary")
    @Mapping(target = "items", source = "items", qualifiedByName = "mapOrderItems")
    @Mapping(target = "orderDate", source = "orderDate")
    @Mapping(target = "orderStatus", source = "orderStatus", qualifiedByName = "mapOrderStatusToDisplayName")
    @Mapping(target = "subtotal", expression = "java(calculateSubtotal(order.getItems()))")
    @Mapping(target = "discountAmount", source = "discountAmount")
    @Mapping(target = "totalAmount", source = "totalAmount")
    @Mapping(target = "currency", source = "currency", qualifiedByName = "mapCurrencyToString")
    @Mapping(target = "notes", source = "notes")
    @Mapping(target = "appliedDiscount", source = "appliedDiscount", qualifiedByName = "mapDiscountInfo")
    @Mapping(target = "adminNote", source = "adminNotes", qualifiedByName = "mapAdminNote")
    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusToDisplayName")
    @Mapping(target = "hasDiscount", source = "order", qualifiedByName = "mapHasDiscount")
    @Mapping(target = "savingsAmount", source = "order", qualifiedByName = "mapSavingsAmount")
    OrderResponse toDto(Order order);

    @Override
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "orderStatus", ignore = true)
    @Mapping(target = "orderDate", ignore = true)
    @Mapping(target = "orderNumber", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "discountAmount", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "appliedDiscount", ignore = true)
    @Mapping(target = "adminNotes", ignore = true)
    Order toEntity(OrderRequest request);

    // Güvenli dealer ID mapping
    @Named("mapDealerId")
    default Long mapDealerId(Order order) {
        if (order == null || order.getUser() == null || order.getUser().getDealer() == null) {
            return null;
        }
        return order.getUser().getDealer().getId();
    }

    // Güvenli dealer name mapping
    @Named("mapDealerName")
    default String mapDealerName(Order order) {
        if (order == null || order.getUser() == null || order.getUser().getDealer() == null) {
            return null;
        }
        return order.getUser().getDealer().getName();
    }

    // OrderMapper.java içindeki mapOrderItems metodunu güncelleyin:

    @Named("mapOrderItems")
    default List<OrderItemSummary> mapOrderItems(Set<OrderItem> items) {
        if (items == null) return null;
        return items.stream()
                .map(item -> {
                    // Primary image URL'sini bul
                    String primaryImageUrl = null;
                    if (item.getProduct().getImages() != null && !item.getProduct().getImages().isEmpty()) {
                        primaryImageUrl = item.getProduct().getImages().stream()
                                .filter(img -> img.getIsPrimary() != null && img.getIsPrimary())
                                .map(img -> img.getImageUrl())
                                .findFirst()
                                .orElse(
                                        // Primary image yoksa ilk resmi al
                                        item.getProduct().getImages().stream()
                                                .map(img -> img.getImageUrl())
                                                .findFirst()
                                                .orElse(null)
                                );
                    }

                    ProductVariant variant = item.getProductVariant();
                    return new OrderItemSummary(
                            item.getProduct().getId(),
                            item.getProduct().getName(),
                            variant != null ? variant.getId() : null,
                            variant != null ? variant.getSku() : null,
                            variant != null ? variant.getSize() : null,
                            item.getQuantity(),
                            item.getUnitPrice(),
                            item.getTotalPrice(),
                            item.getProductPriceId(),
                            primaryImageUrl // ✅ YENİ ALAN
                    );
                })
                .collect(Collectors.toList());
    }

    @Named("mapUserSummary")
    default UserSummary mapUserSummary(AppUser user) {
        if (user == null) return null;
        return new UserSummary(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail()
        );
    }

    // Yardımcı metod - subtotal hesaplama
    default BigDecimal calculateSubtotal(Set<OrderItem> items) {
        if (items == null) return BigDecimal.ZERO;
        return items.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Status mapping metodları
    @Named("mapStatusToDisplayName")
    default String mapStatusToDisplayName(EntityStatus status) {
        return status != null ? status.getDisplayName() : null;
    }

    @Named("mapOrderStatusToDisplayName")
    default String mapOrderStatusToDisplayName(OrderStatus orderStatus) {
        return orderStatus != null ? orderStatus.getDisplayName() : null;
    }

    @Named("mapCurrencyToString")
    default String mapCurrencyToString(com.maxx_global.enums.CurrencyType currency) {
        return currency != null ? currency.name() : "TRY";
    }

    @Named("mapAdminNote")
    default String mapAdminNote(String adminNotes) {
        if (adminNotes == null) {
            return null;
        }
        String trimmed = adminNotes.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }



    // ✅ YENİ: hasDiscount mapping metodu
    @Named("mapHasDiscount")
    default Boolean mapHasDiscount(Order order) {
        return order.getAppliedDiscount() != null &&
                order.getDiscountAmount() != null &&
                order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    // ✅ YENİ: savingsAmount mapping metodu
    @Named("mapSavingsAmount")
    default BigDecimal mapSavingsAmount(Order order) {
        if (order.getDiscountAmount() != null) {
            return order.getDiscountAmount();
        }
        return BigDecimal.ZERO;
    }

    // Mevcut mapDiscountInfo metodu
    @Named("mapDiscountInfo")
    default DiscountInfo mapDiscountInfo(Discount discount) {
        if (discount == null) return null;

        return DiscountInfo.basic(
                discount.getId(),
                discount.getName(),
                discount.getDiscountType() != null ? discount.getDiscountType().name() : null,
                discount.getDiscountValue(),
                null // calculatedAmount order'daki discountAmount'tan gelecek
        );
    }
}
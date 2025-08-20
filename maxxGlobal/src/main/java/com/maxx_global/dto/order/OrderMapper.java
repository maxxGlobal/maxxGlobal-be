package com.maxx_global.dto.order;

import com.maxx_global.dto.BaseMapper;
import com.maxx_global.dto.appUser.UserSummary;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Order;
import com.maxx_global.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface OrderMapper extends BaseMapper<Order, OrderRequest, OrderResponse> {

    @Override
    @Mapping(target = "orderNumber", source = "orderNumber")
    @Mapping(target = "dealerName", source = "user.dealer.name")
    @Mapping(target = "createdBy", source = "user", qualifiedByName = "mapUserSummary")
    @Mapping(target = "items", source = "items", qualifiedByName = "mapOrderItems")
    @Mapping(target = "orderDate", source = "orderDate")
    @Mapping(target = "status", source = "orderStatus")
    @Mapping(target = "subtotal", expression = "java(calculateSubtotal(order.getItems()))")
    @Mapping(target = "discountAmount", source = "discountAmount")
    @Mapping(target = "totalAmount", source = "totalAmount")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "notes", source = "notes")
    @Mapping(target = "adminNotes", source = "adminNotes")
    OrderResponse toDto(Order order);

    @Override
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "items", ignore = true)  // OrderItem'ları servis katmanında oluşturacağız
    @Mapping(target = "orderStatus", ignore = true)
    @Mapping(target = "orderDate", ignore = true)
    @Mapping(target = "orderNumber", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "discountAmount", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "appliedDiscount", ignore = true)
    @Mapping(target = "adminNotes", ignore = true)
    Order toEntity(OrderRequest request);

    @Named("mapOrderItems")
    default List<OrderItemSummary> mapOrderItems(Set<OrderItem> items) {
        if (items == null) return null;
        return items.stream()
                .map(item -> new OrderItemSummary(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getTotalPrice()
                ))
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
    default java.math.BigDecimal calculateSubtotal(Set<OrderItem> items) {
        if (items == null) return java.math.BigDecimal.ZERO;
        return items.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
}
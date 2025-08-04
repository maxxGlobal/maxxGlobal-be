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
    @Mapping(target = "dealerName", source = "user.dealer.name")
    @Mapping(target = "createdBy", source = "user", qualifiedByName = "mapUserSummary")
    @Mapping(target = "items", source = "items", qualifiedByName = "mapOrderItems")
    OrderResponse toDto(Order order);

    @Override
    @Mapping(target = "user.dealer.name", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "items", ignore = true)  // OrderItem'ları servis katmanında oluşturacağız
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
}
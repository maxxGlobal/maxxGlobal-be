package com.maxx_global.dto.dealer;

import com.maxx_global.dto.appUser.UserSummary;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Dealer;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.EntityStatus;
import org.mapstruct.*;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface DealerMapper {

    @Mapping(target = "phone", source = "fixedPhone")
    @Mapping(target = "mobile", source = "mobilePhone")
    @Mapping(target = "preferredCurrency", source = "preferredCurrency", qualifiedByName = "mapCurrency")
    Dealer toEntity(DealerRequest request);

    @Mapping(target = "mobilePhone", source = "mobile")
    @Mapping(target = "users", expression = "java(toUserSummaries(dealer.getUsers()))")
    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusToDisplayName")
    DealerResponse toResponse(Dealer dealer);

    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusToDisplayName")
    DealerSummary toSummary(Dealer dealer);

    List<DealerSummary> toSummaryList(List<Dealer> dealers);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "phone", source = "fixedPhone")
    @Mapping(target = "mobile", source = "mobilePhone")
    @Mapping(target = "preferredCurrency", source = "preferredCurrency", qualifiedByName = "mapCurrency")
    void updateEntityFromRequest(DealerRequest request, @MappingTarget Dealer dealer);

    default List<UserSummary> toUserSummaries(Set<AppUser> users) {
        if (users == null) return List.of();
        return users.stream()
                .map(user -> new UserSummary(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail()))
                .toList();
    }

    // Türkçe status mapping
    @Named("mapStatusToDisplayName")
    default String mapStatusToDisplayName(EntityStatus status) {
        return status != null ? status.getDisplayName() : null;
    }

    // Currency mapping - null kontrolü ile default değer
    @Named("mapCurrency")
    default CurrencyType mapCurrency(CurrencyType currency) {
        return currency != null ? currency : CurrencyType.TRY;
    }
}
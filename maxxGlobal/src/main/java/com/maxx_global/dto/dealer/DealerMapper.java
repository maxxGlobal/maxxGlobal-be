package com.maxx_global.dto.dealer;

import com.maxx_global.dto.appUser.UserSummary;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Dealer;
import com.maxx_global.enums.EntityStatus;
import org.mapstruct.*;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface DealerMapper {

    @Mapping(target = "phone", source = "fixedPhone")
    @Mapping(target = "mobile", source = "mobilePhone")
    Dealer toEntity(DealerRequest request);

    @Mapping(target = "mobilePhone", source = "mobile")
    @Mapping(target = "users", expression = "java(toUserSummaries(dealer.getUsers()))")
    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusToDisplayName") // DEĞIŞEN SATIR
    DealerResponse toResponse(Dealer dealer);

    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusToDisplayName") // DEĞIŞEN SATIR
    DealerSummary toSummary(Dealer dealer);

    List<DealerSummary> toSummaryList(List<Dealer> dealers);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "phone", source = "fixedPhone")
    @Mapping(target = "mobile", source = "mobilePhone")
    void updateEntityFromRequest(DealerRequest request, @MappingTarget Dealer dealer);

    default List<UserSummary> toUserSummaries(Set<AppUser> users) {
        if (users == null) return List.of();
        return users.stream()
                .map(user -> new UserSummary(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail()))
                .toList();
    }

    // YENİ EKLENEN - Türkçe status mapping
    @Named("mapStatusToDisplayName")
    default String mapStatusToDisplayName(EntityStatus status) {
        return status != null ? status.getDisplayName() : null;
    }
}



package com.maxx_global.dto.dealer;

import com.maxx_global.dto.BaseMapper;
import com.maxx_global.dto.appUser.UserSummary;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Dealer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface DealerMapper extends BaseMapper<Dealer, DealerRequest, DealerResponse> {

    // Entity -> Response
    @Override
    @Mapping(target = "users", source = "users", qualifiedByName = "mapUsers")
    DealerResponse toDto(Dealer dealer);

    // Request -> Entity
    @Override
    @Mapping(target = "users", ignore = true) // kullanıcılar servis katmanında set edilir
    Dealer toEntity(DealerRequest request);

    // Kullanıcı listesini UserSummary DTO’ya map et
    @Named("mapUsers")
    default List<UserSummary> mapUsers(Set<AppUser> users) {
        if (users == null) return null;
        return users.stream()
                .map(user -> new UserSummary(
                        user.getId(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getEmail()
                ))
                .collect(Collectors.toList());
    }
}


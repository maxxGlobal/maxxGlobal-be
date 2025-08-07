package com.maxx_global.dto.appUser;

import com.maxx_global.dto.BaseMapper;
import com.maxx_global.dto.dealer.DealerSummary;
import com.maxx_global.dto.permission.PermissionResponse;
import com.maxx_global.dto.role.RoleResponse;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface AppUserMapper extends BaseMapper<AppUser, AppUserRequest, AppUserResponse> {

    // --- Entity -> Response ---
    @Override
    @Mapping(target = "dealer", source = "dealer", qualifiedByName = "mapDealerSummary")
    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapRolesToNames")
    AppUserResponse toDto(AppUser appUser);

    // --- Request -> Entity ---
    @Override
    @Mapping(target = "dealer", ignore = true) // dealer servis katmanında set edilecek
    @Mapping(target = "roles", ignore = true)  // roller servis katmanında set edilecek
    AppUser toEntity(AppUserRequest request);

    // Dealer’ı özet DTO’ya dönüştür
    @Named("mapDealerSummary")
    default DealerSummary mapDealerSummary(Dealer dealer) {
        if (dealer == null) return null;
        return new DealerSummary(dealer.getId(), dealer.getName());
    }

    // Roller listesini String isimlere dönüştür
    @Named("mapRolesToNames")
    default List<RoleResponse> mapRolesToNames(Set<Role> roles) {
        return roles.stream()
                .map(role -> new RoleResponse(
                        role.getName(),
                        role.getPermissions() != null
                                ? role.getPermissions().stream()
                                .map(p -> new PermissionResponse(p.getName(), p.getDescription()))
                                .toList()
                                : List.of()
                ))
                .toList();
    }
}

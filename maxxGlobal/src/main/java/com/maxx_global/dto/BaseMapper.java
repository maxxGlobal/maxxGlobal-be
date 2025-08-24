package com.maxx_global.dto;

import com.maxx_global.enums.EntityStatus;
import org.mapstruct.Named;

import java.util.List;

public interface BaseMapper<E, Req, Res> {

    Res toDto(E entity);

    E toEntity(Req request);

    default List<Res> toDtoList(List<E> entities) {
        return entities.stream().map(this::toDto).toList();
    }

    default List<E> toEntityList(List<Req> requests) {
        return requests.stream().map(this::toEntity).toList();
    }

    // Status mapping helper methods - YENİ EKLENEN METODLAR
    @Named("mapStatusToDisplayName")
    default String mapStatusToDisplayName(EntityStatus status) {
        return status != null ? status.getDisplayName() : null;
    }

    @Named("mapStatusToCode")
    default String mapStatusToCode(EntityStatus status) {
        return status != null ? status.getCode() : null;
    }

    @Named("mapStatusToActive")
    default Boolean mapStatusToActive(EntityStatus status) {
        return status != null && status == EntityStatus.ACTIVE;
    }
}
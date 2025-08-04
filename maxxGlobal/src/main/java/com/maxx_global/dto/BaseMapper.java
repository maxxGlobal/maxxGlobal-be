package com.maxx_global.dto;

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
}


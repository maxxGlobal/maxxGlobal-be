package com.maxx_global.dto.category;

import com.maxx_global.dto.BaseMapper;
import com.maxx_global.entity.Category;
import com.maxx_global.enums.EntityStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface CategoryMapper extends BaseMapper<Category, CategoryRequest, CategoryResponse> {

    // Entity -> Response
    @Override
    @Mapping(target = "parentCategoryName", source = "parentCategory", qualifiedByName = "mapParentName")
    @Mapping(target = "parentCategoryNameEn", source = "parentCategory", qualifiedByName = "mapParentNameEn")
    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusToDisplayName")
    CategoryResponse toDto(Category category);

    // Request -> Entity
    @Override
    @Mapping(target = "parentCategory", ignore = true) // servis katmanında set edilecek
    Category toEntity(CategoryRequest request);

    // Parent Category Name mapping
    @Named("mapParentName")
    default String mapParentName(Category parent) {
        return parent != null ? parent.getName() : null;
    }

    @Named("mapParentNameEn")
    default String mapParentNameEn(Category parent) {
        return parent != null ? parent.getNameEn() : null;
    }
    // YENİ EKLENEN - Türkçe status mapping
    @Named("mapStatusToDisplayName")
    default String mapStatusToDisplayName(EntityStatus status) {
        return status != null ? status.getDisplayName() : null;
    }
}


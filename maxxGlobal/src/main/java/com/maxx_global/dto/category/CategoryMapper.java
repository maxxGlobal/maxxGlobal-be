package com.maxx_global.dto.category;

import com.maxx_global.dto.BaseMapper;
import com.maxx_global.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface CategoryMapper extends BaseMapper<Category, CategoryRequest, CategoryResponse> {

    // Entity -> Response
    @Override
    @Mapping(target = "parentCategoryName", source = "parentCategory", qualifiedByName = "mapParentName")
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
}


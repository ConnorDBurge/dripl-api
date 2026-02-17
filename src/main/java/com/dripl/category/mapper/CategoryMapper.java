package com.dripl.category.mapper;

import com.dripl.category.dto.CategoryDto;
import com.dripl.category.dto.UpdateCategoryDto;
import com.dripl.category.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CategoryMapper {

    CategoryDto toDto(Category category);

    List<CategoryDto> toDtos(List<Category> categories);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "workspaceId", ignore = true)
    @Mapping(target = "parentId", ignore = true)
    void updateEntity(UpdateCategoryDto dto, @MappingTarget Category category);
}

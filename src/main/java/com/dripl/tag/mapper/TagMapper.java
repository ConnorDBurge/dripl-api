package com.dripl.tag.mapper;

import com.dripl.tag.dto.TagDto;
import com.dripl.tag.dto.UpdateTagDto;
import com.dripl.tag.entity.Tag;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TagMapper {

    TagDto toDto(Tag tag);

    List<TagDto> toDtos(List<Tag> tags);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "workspaceId", ignore = true)
    void updateEntity(UpdateTagDto dto, @MappingTarget Tag tag);
}

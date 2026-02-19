package com.dripl.recurring.mapper;

import com.dripl.recurring.dto.RecurringItemDto;
import com.dripl.recurring.dto.UpdateRecurringItemDto;
import com.dripl.recurring.entity.RecurringItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RecurringItemMapper {

    RecurringItemDto toDto(RecurringItem recurringItem);

    List<RecurringItemDto> toDtos(List<RecurringItem> recurringItems);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "workspaceId", ignore = true)
    @Mapping(target = "accountId", ignore = true)
    @Mapping(target = "merchantId", ignore = true)
    @Mapping(target = "categoryId", ignore = true)
    @Mapping(target = "notes", ignore = true)
    @Mapping(target = "endDate", ignore = true)
    @Mapping(target = "tagIds", ignore = true)
    void updateEntity(UpdateRecurringItemDto dto, @MappingTarget RecurringItem recurringItem);
}

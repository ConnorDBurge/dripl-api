package com.dripl.category.dto;

import com.dripl.common.dto.BaseDto;
import com.dripl.common.enums.Status;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class CategoryDto extends BaseDto {

    private UUID workspaceId;
    private UUID parentId;
    private String name;
    private String description;
    private Status status;
    private boolean income;
    private boolean excludeFromBudget;
    private boolean excludeFromTotals;
    private int displayOrder;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<CategoryDto> children;
}

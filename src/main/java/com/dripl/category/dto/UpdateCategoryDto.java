package com.dripl.category.dto;

import com.dripl.common.enums.Status;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCategoryDto {

    @Size(min = 1, max = 100, message = "Category name must be between 1 and 100 characters")
    private String name;

    @Size(max = 255, message = "Category description must be at most 255 characters")
    private String description;

    private Status status;
    private Boolean income;
    private Boolean excludeFromBudget;
    private Boolean excludeFromTotals;

    private UUID parentId;
    @Getter
    private boolean parentIdSpecified;

    @Getter
    private boolean childrenSpecified;

    @JsonSetter("parentId")
    public void assignParentId(UUID parentId) {
        this.parentId = parentId;
        this.parentIdSpecified = true;
    }

    @JsonSetter("children")
    public void assignChildren(List<UUID> children) {
        this.childrenSpecified = true;
    }
}

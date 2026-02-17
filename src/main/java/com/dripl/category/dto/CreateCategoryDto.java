package com.dripl.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCategoryDto {

    @NotBlank(message = "Category name must be provided")
    @Size(min = 1, max = 100, message = "Category name must be between 1 and 100 characters")
    private String name;

    @Size(max = 255, message = "Category description must be at most 255 characters")
    private String description;

    private UUID parentId;
    private Boolean income;
    private Boolean excludeFromBudget;
    private Boolean excludeFromTotals;
}

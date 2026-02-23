package com.dripl.category.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoveCategoryDto {

    @NotNull(message = "Display order must be provided")
    @Min(value = 0, message = "Display order must be >= 0")
    private Integer displayOrder;
}

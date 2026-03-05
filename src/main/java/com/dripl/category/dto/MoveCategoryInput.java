package com.dripl.category.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoveCategoryInput {

    @Min(value = 0, message = "Display order must be >= 0")
    private Integer displayOrder;
}

package com.dripl.transaction.split.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTransactionSplitDto {

    @NotEmpty(message = "At least 2 children must be provided")
    @Size(min = 2, message = "At least 2 children must be provided")
    @Valid
    private List<UpdateSplitChildDto> children;
}

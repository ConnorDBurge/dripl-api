package com.dripl.transaction.split.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTransactionSplitInput {

    @Size(min = 2, message = "At least 2 children must be provided")
    @Valid
    private List<UpdateSplitChildInput> children;
}

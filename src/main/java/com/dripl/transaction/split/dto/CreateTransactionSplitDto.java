package com.dripl.transaction.split.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class CreateTransactionSplitDto {

    @NotNull(message = "Transaction ID must be provided")
    private UUID transactionId;

    @NotEmpty(message = "At least 2 children must be provided")
    @Size(min = 2, message = "At least 2 children must be provided")
    @Valid
    private List<SplitChildDto> children;
}

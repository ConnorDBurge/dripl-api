package com.dripl.transaction.split.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTransactionSplitInput {

    private UUID transactionId;

    @Size(min = 2, message = "At least 2 children must be provided")
    @Valid
    private List<SplitChildInput> children;
}

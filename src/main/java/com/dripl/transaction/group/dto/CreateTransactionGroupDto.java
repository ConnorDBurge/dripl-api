package com.dripl.transaction.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTransactionGroupDto {

    @NotBlank(message = "Name must be provided")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @NotEmpty(message = "At least 2 transaction IDs must be provided")
    @Size(min = 2, message = "At least 2 transaction IDs must be provided")
    private Set<UUID> transactionIds;

    private UUID categoryId;

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;

    private Set<UUID> tagIds;
}

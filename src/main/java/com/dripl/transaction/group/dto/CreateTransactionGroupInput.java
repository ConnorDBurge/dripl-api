package com.dripl.transaction.group.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTransactionGroupInput {

    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Size(min = 2, message = "At least 2 transaction IDs must be provided")
    private Set<UUID> transactionIds;

    private UUID categoryId;

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;

    private Set<UUID> tagIds;
}

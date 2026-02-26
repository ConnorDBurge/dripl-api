package com.dripl.recurring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OccurrenceTransactionDto {

    private UUID id;
    private LocalDate date;
    private BigDecimal amount;
}

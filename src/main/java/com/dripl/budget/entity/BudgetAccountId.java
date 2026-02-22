package com.dripl.budget.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BudgetAccountId implements Serializable {
    private UUID budgetId;
    private UUID accountId;
}

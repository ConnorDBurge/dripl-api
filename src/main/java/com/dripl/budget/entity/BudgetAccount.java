package com.dripl.budget.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "budget_accounts")
@IdClass(BudgetAccountId.class)
public class BudgetAccount {

    @Id
    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
}

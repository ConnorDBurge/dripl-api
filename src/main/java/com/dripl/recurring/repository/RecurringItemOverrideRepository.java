package com.dripl.recurring.repository;

import com.dripl.recurring.entity.RecurringItemOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringItemOverrideRepository extends JpaRepository<RecurringItemOverride, UUID> {

    Optional<RecurringItemOverride> findByRecurringItemIdAndOccurrenceDate(UUID recurringItemId, LocalDate occurrenceDate);


    List<RecurringItemOverride> findByWorkspaceIdAndOccurrenceDateBetween(UUID workspaceId, LocalDate start, LocalDate end);

    void deleteByRecurringItemId(UUID recurringItemId);
}

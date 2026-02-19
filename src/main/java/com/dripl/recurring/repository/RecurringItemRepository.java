package com.dripl.recurring.repository;

import com.dripl.recurring.entity.RecurringItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringItemRepository extends JpaRepository<RecurringItem, UUID> {

    List<RecurringItem> findAllByWorkspaceId(UUID workspaceId);

    Optional<RecurringItem> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}

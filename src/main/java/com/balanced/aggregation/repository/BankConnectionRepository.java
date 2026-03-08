package com.balanced.aggregation.repository;

import com.balanced.aggregation.entity.BankConnection;
import com.balanced.common.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankConnectionRepository extends JpaRepository<BankConnection, UUID> {

    List<BankConnection> findAllByWorkspaceId(UUID workspaceId);

    Optional<BankConnection> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<BankConnection> findByEnrollmentIdAndWorkspaceId(String enrollmentId, UUID workspaceId);

    Optional<BankConnection> findFirstByWorkspaceIdAndInstitutionIdAndStatus(
            UUID workspaceId, String institutionId, Status status);
}

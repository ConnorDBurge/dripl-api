package com.dripl.merchant.repository;

import com.dripl.merchant.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    List<Merchant> findAllByWorkspaceId(UUID workspaceId);

    Optional<Merchant> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Merchant> findByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);
}

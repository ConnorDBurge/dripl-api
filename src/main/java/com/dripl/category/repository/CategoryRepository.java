package com.dripl.category.repository;

import com.dripl.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID>, JpaSpecificationExecutor<Category> {

    List<Category> findAllByWorkspaceId(UUID workspaceId);

    Optional<Category> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Category> findAllByParentId(UUID parentId);

    boolean existsByParentId(UUID parentId);

    @Modifying
    @Query("UPDATE Category c SET c.parentId = null WHERE c.parentId = :parentId")
    void detachChildren(UUID parentId);
}

package com.dripl.category.repository;

import com.dripl.category.entity.Category;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class CategorySpecifications {

    private CategorySpecifications() {}

    public static Specification<Category> inWorkspace(UUID workspaceId) {
        return (root, query, cb) -> cb.equal(root.get("workspaceId"), workspaceId);
    }

    public static Specification<Category> isIncome(Boolean income) {
        return (root, query, cb) -> cb.equal(root.get("income"), income);
    }

    public static <V> Specification<Category> optionally(V value, java.util.function.Function<V, Specification<Category>> specFn) {
        return value != null ? specFn.apply(value) : Specification.where(null);
    }
}

package com.dripl.transaction.repository;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.enums.TransactionSource;
import com.dripl.transaction.enums.TransactionStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;
import java.util.UUID;

public final class TransactionSpecifications {

    private TransactionSpecifications() {}

    public static Specification<Transaction> inWorkspace(UUID workspaceId) {
        return (root, query, cb) -> cb.equal(root.get("workspaceId"), workspaceId);
    }

    public static Specification<Transaction> hasAccount(UUID accountId) {
        return (root, query, cb) -> cb.equal(root.get("accountId"), accountId);
    }

    public static Specification<Transaction> hasMerchant(UUID merchantId) {
        return (root, query, cb) -> cb.equal(root.get("merchantId"), merchantId);
    }

    public static Specification<Transaction> hasGroup(UUID groupId) {
        return (root, query, cb) -> cb.equal(root.get("groupId"), groupId);
    }

    public static Specification<Transaction> hasSplit(UUID splitId) {
        return (root, query, cb) -> cb.equal(root.get("splitId"), splitId);
    }

    public static Specification<Transaction> hasCategory(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    public static Specification<Transaction> hasRecurringItem(UUID recurringItemId) {
        return (root, query, cb) -> cb.equal(root.get("recurringItemId"), recurringItemId);
    }

    public static Specification<Transaction> hasStatus(TransactionStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Transaction> hasSource(TransactionSource source) {
        return (root, query, cb) -> cb.equal(root.get("source"), source);
    }

    public static Specification<Transaction> hasCurrency(CurrencyCode currencyCode) {
        return (root, query, cb) -> cb.equal(root.get("currencyCode"), currencyCode);
    }

    /**
     * Matches transactions that have at least one of the provided tags.
     */
    public static Specification<Transaction> hasAnyTag(Set<UUID> tagIds) {
        return (root, query, cb) -> root.join("tagIds").in(tagIds);
    }

    /**
     * Applies the spec only when the value is non-null; otherwise returns a no-op.
     */
    public static <V> Specification<Transaction> optionally(V value, java.util.function.Function<V, Specification<Transaction>> specFn) {
        return value != null ? specFn.apply(value) : Specification.where(null);
    }
}

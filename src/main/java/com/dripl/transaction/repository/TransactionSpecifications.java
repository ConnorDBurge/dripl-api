package com.dripl.transaction.repository;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.enums.TransactionSource;
import com.dripl.transaction.enums.TransactionStatus;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    public static Specification<Transaction> dateOnOrAfter(LocalDateTime startDate) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), startDate);
    }

    public static Specification<Transaction> dateOnOrBefore(LocalDateTime endDate) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), endDate);
    }

    public static Specification<Transaction> amountGreaterThanOrEqual(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), min);
    }

    public static Specification<Transaction> amountLessThanOrEqual(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), max);
    }

    /**
     * Searches across notes (on transaction), merchant name, and category name via ILIKE.
     * Uses correlated subqueries since Transaction uses raw UUID FKs, not @ManyToOne.
     */
    public static Specification<Transaction> searchText(String search) {
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";

            var notesMatch = cb.like(cb.lower(root.get("notes")), pattern);

            Subquery<String> merchantSubquery = query.subquery(String.class);
            Root<?> merchantRoot = merchantSubquery.from(com.dripl.merchant.entity.Merchant.class);
            merchantSubquery.select(merchantRoot.get("name"))
                    .where(cb.equal(merchantRoot.get("id"), root.get("merchantId")));
            var merchantMatch = cb.like(cb.lower(merchantSubquery), pattern);

            Subquery<String> categorySubquery = query.subquery(String.class);
            Root<?> categoryRoot = categorySubquery.from(com.dripl.category.entity.Category.class);
            categorySubquery.select(categoryRoot.get("name"))
                    .where(cb.equal(categoryRoot.get("id"), root.get("categoryId")));
            var categoryMatch = cb.like(cb.lower(categorySubquery), pattern);

            var amountMatch = cb.like(
                    cb.toString(root.get("amount")),
                    "%" + search + "%");

            return cb.or(notesMatch, merchantMatch, categoryMatch, amountMatch);
        };
    }

    /**
     * Builds a Sort for the given sortBy field and direction, with id ASC tiebreaker.
     * JOIN-based sorts (category, merchant, account) are handled via Specification-based ORDER BY.
     */
    public static Sort buildSort(String sortBy, Sort.Direction direction) {
        String property = switch (sortBy) {
            case "amount" -> "amount";
            case "createdAt" -> "createdAt";
            default -> "date";
        };
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    /**
     * Adds ORDER BY via a correlated subquery for sorting by a related entity's name.
     * Used for category, merchant, and account sorts where Transaction has raw UUID FKs.
     */
    public static Specification<Transaction> sortByRelatedName(String sortBy, Sort.Direction direction) {
        return (root, query, cb) -> {
            Class<?> entityClass = switch (sortBy) {
                case "category" -> com.dripl.category.entity.Category.class;
                case "merchant" -> com.dripl.merchant.entity.Merchant.class;
                case "account" -> com.dripl.account.entity.Account.class;
                default -> null;
            };
            if (entityClass == null) return null;

            String fkField = switch (sortBy) {
                case "category" -> "categoryId";
                case "merchant" -> "merchantId";
                case "account" -> "accountId";
                default -> throw new IllegalArgumentException("Unsupported sortBy: " + sortBy);
            };

            Subquery<String> sub = query.subquery(String.class);
            Root<?> relatedRoot = sub.from(entityClass);
            sub.select(relatedRoot.get("name"))
                    .where(cb.equal(relatedRoot.get("id"), root.get(fkField)));

            var nameOrder = direction == Sort.Direction.ASC ? cb.asc(sub) : cb.desc(sub);
            var idOrder = cb.asc(root.get("id"));
            query.orderBy(nameOrder, idOrder);

            return null; // no WHERE predicate, just ORDER BY
        };
    }

    /**
     * Returns true if the sortBy value requires a subquery-based ORDER BY.
     */
    public static boolean isRelatedSort(String sortBy) {
        return "category".equals(sortBy) || "merchant".equals(sortBy) || "account".equals(sortBy);
    }

    /**
     * Applies the spec only when the value is non-null; otherwise returns a no-op.
     */
    public static <V> Specification<Transaction> optionally(V value, java.util.function.Function<V, Specification<Transaction>> specFn) {
        return value != null ? specFn.apply(value) : Specification.where(null);
    }
}

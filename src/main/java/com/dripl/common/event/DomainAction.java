package com.dripl.common.event;

/**
 * Interface for domain action enums. Each enum knows its domain and action strings.
 * <p>Example: {@code TransactionAction.CREATED} → domain "transaction", action "created"</p>
 */
public interface DomainAction {
    String domain();
    String action();
}

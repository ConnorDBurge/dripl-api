package com.dripl.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic domain event publisher that auto-diffs @Audited fields.
 * Supports a value resolver map to store human-readable display names
 * instead of raw UUIDs (e.g. category name instead of categoryId).
 * <p>
 * Usage:
 * <pre>
 *   // Created — with display name resolution
 *   Map&lt;String, String&gt; names = Map.of(accountId.toString(), "Chase Checking");
 *   publisher.publish(TransactionAction.CREATED, savedTransaction, names);
 *
 *   // Updated — auto-diffs with display name resolution
 *   publisher.publish(TransactionAction.UPDATED, before, after, names);
 *
 *   // Custom action with explicit changes
 *   publisher.publish(TransactionAction.GROUPED, txnId, wsId,
 *       List.of(new FieldChange("groupId", null, groupId.toString())));
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    private static final Map<Class<?>, List<AuditedField>> FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * Publish an event with all @Audited non-null fields as changes.
     */
    public void publish(DomainAction action, WorkspaceScoped entity) {
        publish(action, entity, Map.of());
    }

    /**
     * Publish an event with all @Audited non-null fields, resolving display values.
     */
    public void publish(DomainAction action, WorkspaceScoped entity, Map<String, String> valueResolver) {
        List<FieldChange> changes = new ArrayList<>();
        for (AuditedField af : getAuditedFields(entity.getClass())) {
            Object value = readFieldValue(af.field(), entity);
            if (value != null) {
                changes.add(new FieldChange(af.displayName(), null, resolveValue(value, valueResolver)));
            }
        }
        eventPublisher.publishEvent(DomainEvents.create(
                action.domain(), action.action(), entity.getId(), entity.getWorkspaceId(), changes));
    }

    /**
     * Publish an event by diffing @Audited fields. No-op if nothing changed.
     */
    public void publish(DomainAction action, WorkspaceScoped oldEntity, WorkspaceScoped newEntity) {
        publish(action, oldEntity, newEntity, Map.of());
    }

    /**
     * Publish an event by diffing @Audited fields, resolving display values.
     * No-op if nothing changed.
     */
    public void publish(DomainAction action, WorkspaceScoped oldEntity, WorkspaceScoped newEntity,
                        Map<String, String> valueResolver) {
        List<FieldChange> changes = diff(oldEntity, newEntity, valueResolver);
        if (!changes.isEmpty()) {
            eventPublisher.publishEvent(DomainEvents.create(
                    action.domain(), action.action(), newEntity.getId(), newEntity.getWorkspaceId(), changes));
        }
    }

    /**
     * Publish a custom event with explicit field changes.
     */
    public void publish(DomainAction action, UUID entityId, UUID workspaceId, List<FieldChange> changes) {
        eventPublisher.publishEvent(DomainEvents.create(
                action.domain(), action.action(), entityId, workspaceId, changes));
    }

    List<FieldChange> diff(Object oldEntity, Object newEntity) {
        return diff(oldEntity, newEntity, Map.of());
    }

    List<FieldChange> diff(Object oldEntity, Object newEntity, Map<String, String> valueResolver) {
        List<FieldChange> changes = new ArrayList<>();
        for (AuditedField af : getAuditedFields(oldEntity.getClass())) {
            Object oldVal = readFieldValue(af.field(), oldEntity);
            Object newVal = readFieldValue(af.field(), newEntity);
            if (!Objects.equals(oldVal, newVal)) {
                changes.add(new FieldChange(
                        af.displayName(),
                        resolveValue(oldVal, valueResolver),
                        resolveValue(newVal, valueResolver)));
            }
        }
        return changes;
    }

    private static Object resolveValue(Object value, Map<String, String> valueResolver) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return value;
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> {
                        String str = item.toString();
                        return valueResolver.getOrDefault(str, str);
                    })
                    .toList();
        }
        String str = value.toString();
        return valueResolver.getOrDefault(str, str);
    }

    private List<AuditedField> getAuditedFields(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, c -> {
            List<AuditedField> fields = new ArrayList<>();
            Class<?> current = c;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    Audited annotation = field.getAnnotation(Audited.class);
                    if (annotation != null) {
                        field.setAccessible(true);
                        String display = annotation.displayName().isEmpty() ? field.getName() : annotation.displayName();
                        fields.add(new AuditedField(field, display));
                    }
                }
                current = current.getSuperclass();
            }
            return Collections.unmodifiableList(fields);
        });
    }

    private Object readFieldValue(Field field, Object obj) {
        try {
            Object value = field.get(obj);
            return switch (value) {
                case null -> null;
                case BigDecimal bd -> bd.stripTrailingZeros();
                case Collection<?> collection -> collection.stream()
                        .map(Object::toString)
                        .sorted()
                        .toList();
                default -> value.toString();
            };
        } catch (IllegalAccessException e) {
            log.warn("Cannot read field {} on {}: {}", field.getName(), obj.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private record AuditedField(Field field, String displayName) {}
}

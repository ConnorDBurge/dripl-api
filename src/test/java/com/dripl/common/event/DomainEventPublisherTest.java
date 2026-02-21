package com.dripl.common.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainEventPublisherTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DomainEventPublisher domainEventPublisher;

    enum TestAction implements DomainAction {
        CREATED, UPDATED, GROUPED;
        @Override public String domain() { return "test"; }
        @Override public String action() { return name().toLowerCase(); }
    }

    static class TestEntity implements WorkspaceScoped {
        private final UUID id;
        private final UUID workspaceId;
        @Audited(displayName = "label") String name;
        @Audited BigDecimal amount;
        @Audited(displayName = "reference") UUID refId;
        @Audited Set<UUID> tags;
        String notAudited;

        TestEntity(UUID id, UUID workspaceId, String name, BigDecimal amount, UUID refId, Set<UUID> tags, String notAudited) {
            this.id = id;
            this.workspaceId = workspaceId;
            this.name = name;
            this.amount = amount;
            this.refId = refId;
            this.tags = tags;
            this.notAudited = notAudited;
        }

        @Override public UUID getId() { return id; }
        @Override public UUID getWorkspaceId() { return workspaceId; }
    }

    private static final UUID ID = UUID.randomUUID();
    private static final UUID WS = UUID.randomUUID();

    @Test
    void diff_detectsChangedFields() {
        var old = new TestEntity(ID, WS, "Coffee", new BigDecimal("4.50"), UUID.randomUUID(), Set.of(), "ignored-old");
        var updated = new TestEntity(ID, WS, "Groceries", new BigDecimal("42.00"), UUID.randomUUID(), Set.of(), "ignored-new");

        List<FieldChange> changes = domainEventPublisher.diff(old, updated);

        assertThat(changes).hasSize(3);
        assertThat(changes).extracting(FieldChange::field).containsExactlyInAnyOrder("label", "amount", "reference");
    }

    @Test
    void diff_noChanges_returnsEmpty() {
        UUID ref = UUID.randomUUID();
        var old = new TestEntity(ID, WS, "Coffee", new BigDecimal("4.50"), ref, Set.of(), "a");
        var same = new TestEntity(ID, WS, "Coffee", new BigDecimal("4.5"), ref, Set.of(), "b");

        List<FieldChange> changes = domainEventPublisher.diff(old, same);

        assertThat(changes).isEmpty();
    }

    @Test
    void diff_bigDecimalScaleNormalized() {
        var old = new TestEntity(ID, WS, null, new BigDecimal("10.0000"), null, null, null);
        var same = new TestEntity(ID, WS, null, new BigDecimal("10"), null, null, null);

        List<FieldChange> changes = domainEventPublisher.diff(old, same);

        assertThat(changes).isEmpty();
    }

    @Test
    void diff_returnsNativeTypes() {
        var old = new TestEntity(ID, WS, "Coffee", new BigDecimal("4.50"), null, Set.of(), null);
        var updated = new TestEntity(ID, WS, "Groceries", new BigDecimal("42.00"), null, Set.of(), null);

        List<FieldChange> changes = domainEventPublisher.diff(old, updated);

        FieldChange amountChange = changes.stream().filter(c -> c.field().equals("amount")).findFirst().orElseThrow();
        assertThat(amountChange.oldValue()).isInstanceOf(BigDecimal.class);
        assertThat(amountChange.newValue()).isInstanceOf(BigDecimal.class);
        assertThat(amountChange.oldValue()).isEqualTo(new BigDecimal("4.5"));
        assertThat(amountChange.newValue()).isEqualTo(new BigDecimal("42"));
    }

    @Test
    void diff_collectionsReturnAsList() {
        UUID tag1 = UUID.randomUUID();
        UUID tag2 = UUID.randomUUID();
        var old = new TestEntity(ID, WS, null, null, null, Set.of(), null);
        var updated = new TestEntity(ID, WS, null, null, null, Set.of(tag1, tag2), null);

        List<FieldChange> changes = domainEventPublisher.diff(old, updated);

        FieldChange tagChange = changes.stream().filter(c -> c.field().equals("tags")).findFirst().orElseThrow();
        assertThat(tagChange.oldValue()).isInstanceOf(List.class);
        assertThat(tagChange.newValue()).isInstanceOf(List.class);
        assertThat((List<?>) tagChange.newValue()).hasSize(2);
    }

    @Test
    void diff_collectionsResolvedViaValueResolver() {
        UUID tag1 = UUID.randomUUID();
        UUID tag2 = UUID.randomUUID();
        var old = new TestEntity(ID, WS, null, null, null, Set.of(), null);
        var updated = new TestEntity(ID, WS, null, null, null, Set.of(tag1, tag2), null);
        Map<String, String> resolver = Map.of(tag1.toString(), "Groceries", tag2.toString(), "Monthly");

        List<FieldChange> changes = domainEventPublisher.diff(old, updated, resolver);

        FieldChange tagChange = changes.stream().filter(c -> c.field().equals("tags")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<String> resolved = (List<String>) tagChange.newValue();
        assertThat(resolved).containsExactlyInAnyOrder("Groceries", "Monthly");
    }

    @Test
    void diff_nullToNonNull() {
        var old = new TestEntity(ID, WS, null, null, null, null, null);
        var updated = new TestEntity(ID, WS, "Coffee", new BigDecimal("5"), UUID.randomUUID(), null, null);

        List<FieldChange> changes = domainEventPublisher.diff(old, updated);

        assertThat(changes).hasSize(3);
        assertThat(changes).allSatisfy(c -> assertThat(c.oldValue()).isNull());
    }

    @Test
    void diff_nonNullToNull() {
        var old = new TestEntity(ID, WS, "Coffee", new BigDecimal("5"), UUID.randomUUID(), null, null);
        var updated = new TestEntity(ID, WS, null, null, null, null, null);

        List<FieldChange> changes = domainEventPublisher.diff(old, updated);

        assertThat(changes).hasSize(3);
        assertThat(changes).extracting(FieldChange::field).containsExactlyInAnyOrder("label", "amount", "reference");
        assertThat(changes).allSatisfy(c -> assertThat(c.newValue()).isNull());
    }

    @Test
    void publish_singleEntity_includesNonNullAuditedFields() {
        var entity = new TestEntity(ID, WS, "Coffee", new BigDecimal("4.50"), null, null, "ignored");

        domainEventPublisher.publish(TestAction.CREATED, entity);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DomainEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("test.created");
        assertThat(event.entityId()).isEqualTo(ID);
        assertThat(event.workspaceId()).isEqualTo(WS);
        assertThat(event.changes()).extracting(FieldChange::field).containsExactlyInAnyOrder("label", "amount");
    }

    @Test
    void publish_diff_noChanges_doesNotPublish() {
        var entity = new TestEntity(ID, WS, "Coffee", new BigDecimal("4.50"), null, null, null);
        var same = new TestEntity(ID, WS, "Coffee", new BigDecimal("4.5"), null, null, null);

        domainEventPublisher.publish(TestAction.UPDATED, entity, same);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void publish_diff_withChanges_publishes() {
        var old = new TestEntity(ID, WS, "Coffee", new BigDecimal("4.50"), null, null, null);
        var updated = new TestEntity(ID, WS, "Groceries", new BigDecimal("4.50"), null, null, null);

        domainEventPublisher.publish(TestAction.UPDATED, old, updated);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DomainEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("test.updated");
        assertThat(event.changes()).hasSize(1);
        assertThat(event.changes().getFirst().field()).isEqualTo("label");
        assertThat(event.changes().getFirst().oldValue()).isEqualTo("Coffee");
        assertThat(event.changes().getFirst().newValue()).isEqualTo("Groceries");
    }

    @Test
    void publish_explicitChanges() {
        UUID groupId = UUID.randomUUID();

        domainEventPublisher.publish(TestAction.GROUPED, ID, WS,
                List.of(new FieldChange("groupId", null, groupId.toString())));

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("test.grouped");
        assertThat(captor.getValue().entityId()).isEqualTo(ID);
        assertThat(captor.getValue().workspaceId()).isEqualTo(WS);
    }

    @Test
    void publish_withValueResolver_resolvesUuidsToDisplayNames() {
        UUID refId = UUID.randomUUID();
        var entity = new TestEntity(ID, WS, "Coffee", new BigDecimal("4.50"), refId, null, null);
        Map<String, String> resolver = Map.of(refId.toString(), "My Reference");

        domainEventPublisher.publish(TestAction.CREATED, entity, resolver);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        FieldChange refChange = captor.getValue().changes().stream()
                .filter(c -> c.field().equals("reference")).findFirst().orElseThrow();
        assertThat(refChange.newValue()).isEqualTo("My Reference");
    }

    @Test
    void publish_diff_withValueResolver_resolvesBothOldAndNew() {
        UUID oldRef = UUID.randomUUID();
        UUID newRef = UUID.randomUUID();
        var old = new TestEntity(ID, WS, null, null, oldRef, null, null);
        var updated = new TestEntity(ID, WS, null, null, newRef, null, null);
        Map<String, String> resolver = Map.of(
                oldRef.toString(), "Old Reference",
                newRef.toString(), "New Reference");

        domainEventPublisher.publish(TestAction.UPDATED, old, updated, resolver);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        FieldChange refChange = captor.getValue().changes().getFirst();
        assertThat(refChange.field()).isEqualTo("reference");
        assertThat(refChange.oldValue()).isEqualTo("Old Reference");
        assertThat(refChange.newValue()).isEqualTo("New Reference");
    }
}

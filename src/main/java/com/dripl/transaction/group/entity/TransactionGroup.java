package com.dripl.transaction.group.entity;

import com.dripl.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "transaction_groups")
public class TransactionGroup extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "notes", length = 500)
    private String notes;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "transaction_group_tags", joinColumns = @JoinColumn(name = "transaction_group_id"))
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();
}

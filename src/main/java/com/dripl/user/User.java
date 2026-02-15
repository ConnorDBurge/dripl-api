package com.dripl.user;

import com.dripl.common.audit.BaseEntity;
import com.dripl.workspace.membership.WorkspaceMembership;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "given_name", nullable = false)
    private String givenName;

    @Column(name = "family_name", nullable = false)
    private String familyName;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "last_workspace_id")
    private UUID lastWorkspaceId;

    @OneToMany(mappedBy = "user")
    @Builder.Default
    private List<WorkspaceMembership> memberships = new ArrayList<>();
}

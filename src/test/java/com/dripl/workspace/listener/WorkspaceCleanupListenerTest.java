package com.dripl.workspace.listener;

import com.dripl.workspace.entity.Workspace;
import com.dripl.workspace.enums.WorkspaceStatus;
import com.dripl.workspace.membership.event.MembershipDeletedEvent;
import com.dripl.workspace.membership.repository.WorkspaceMembershipRepository;
import com.dripl.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceCleanupListenerTest {

    @Mock private WorkspaceMembershipRepository membershipRepository;
    @Mock private WorkspaceRepository workspaceRepository;

    @InjectMocks private WorkspaceCleanupListener listener;

    @Test
    void orphanedWorkspace_isDeleted() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId).name("Orphan").status(WorkspaceStatus.ACTIVE).build();

        when(membershipRepository.countByWorkspaceId(workspaceId)).thenReturn(0L);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        listener.handleMembershipDeleted(new MembershipDeletedEvent(workspaceId));

        verify(workspaceRepository).delete(workspace);
    }

    @Test
    void workspaceWithMembers_isNotDeleted() {
        UUID workspaceId = UUID.randomUUID();

        when(membershipRepository.countByWorkspaceId(workspaceId)).thenReturn(2L);

        listener.handleMembershipDeleted(new MembershipDeletedEvent(workspaceId));

        verify(workspaceRepository, never()).findById(any());
        verify(workspaceRepository, never()).delete(any());
    }

    @Test
    void orphanedWorkspace_alreadyGone_noError() {
        UUID workspaceId = UUID.randomUUID();

        when(membershipRepository.countByWorkspaceId(workspaceId)).thenReturn(0L);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        listener.handleMembershipDeleted(new MembershipDeletedEvent(workspaceId));

        verify(workspaceRepository, never()).delete(any());
    }
}

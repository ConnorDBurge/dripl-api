package com.dripl.workspace.listener;

import com.dripl.workspace.membership.event.MembershipDeletedEvent;
import com.dripl.workspace.membership.repository.WorkspaceMembershipRepository;
import com.dripl.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceCleanupListener {

    private final WorkspaceMembershipRepository membershipRepository;
    private final WorkspaceRepository workspaceRepository;

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleMembershipDeleted(MembershipDeletedEvent event) {
        if (event.correlationId() != null) {
            MDC.put("correlationId", event.correlationId());
        }
        try {
            var workspaceId = event.workspaceId();
            long remaining = membershipRepository.countByWorkspaceId(workspaceId);

            if (remaining == 0) {
                workspaceRepository.findById(workspaceId).ifPresent(workspace -> {
                    log.info("Deleting orphaned workspace '{}' ({})", workspace.getName(), workspaceId);
                    workspaceRepository.delete(workspace);
                });
            }
        } finally {
            MDC.remove("correlationId");
        }
    }
}

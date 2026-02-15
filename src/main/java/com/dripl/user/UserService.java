package com.dripl.user;

import com.dripl.auth.JwtUtil;
import com.dripl.common.exception.ConflictException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.workspace.CreateWorkspaceDto;
import com.dripl.workspace.Workspace;
import com.dripl.workspace.WorkspaceService;
import com.dripl.workspace.membership.MembershipService;
import com.dripl.workspace.membership.Role;
import com.dripl.workspace.membership.WorkspaceMembership;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WorkspaceService workspaceService;
    private final MembershipService membershipService;
    private final JwtUtil jwtUtil;

    @Transactional(readOnly = true)
    public User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public User updateUser(UUID userId, UpdateUserDto dto) {
        log.info("Updating user {}", userId);
        User user = getUser(userId);

        if (dto.getEmail() != null) {
            userRepository.findByEmail(dto.getEmail()).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new ConflictException("Email already in use");
                }
            });
            user.setEmail(dto.getEmail());
        }
        if (dto.getGivenName() != null) {
            user.setGivenName(dto.getGivenName());
        }
        if (dto.getFamilyName() != null) {
            user.setFamilyName(dto.getFamilyName());
        }

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        User user = getUser(userId);
        log.info("Deleting user {}", user.getEmail());

        List<WorkspaceMembership> memberships = membershipService.listAllUserMemberships(userId);
        for (WorkspaceMembership membership : memberships) {
            membershipService.deleteMembership(userId, membership.getWorkspace().getId());
        }

        userRepository.delete(user);
        log.info("Successfully deleted user {}", user.getEmail());
    }

    /**
     * Creates the user and a default workspace if they don't already exist.
     * Returns a new JWT with the user's workspace context.
     */
    @Transactional
    public BootstrapResponse bootstrapUser(String email, String givenName, String familyName) {
        log.debug("Bootstrapping user: email={}", email);

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            try {
                user = userRepository.save(User.builder()
                        .email(email)
                        .givenName(givenName != null ? givenName : email)
                        .familyName(familyName != null ? familyName : "")
                        .isActive(true)
                        .build());
                log.debug("Created user {} for email {}", user.getId(), email);
            } catch (DataIntegrityViolationException e) {
                user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new ConflictException("User just created but not found"));
            }
        }

        if (user.getLastWorkspaceId() == null) {
            String workspaceName = String.format("%s's Workspace", user.getGivenName());
            Workspace workspace = workspaceService.provisionWorkspace(user.getId(),
                    CreateWorkspaceDto.builder().name(workspaceName).build());
            user.setLastWorkspaceId(workspace.getId());
            user = userRepository.save(user);
        }

        // Gather roles from membership
        List<String> roles = membershipService.findMembership(user.getId(), user.getLastWorkspaceId())
                .map(m -> m.getRoles().stream().map(Role::name).collect(Collectors.toList()))
                .orElse(List.of("READ", "WRITE"));

        String token = jwtUtil.generateToken(user.getId(), user.getLastWorkspaceId(),
                user.getId().toString(), roles);

        return new BootstrapResponse(UserDto.fromEntity(user), token);
    }

    public record BootstrapResponse(UserDto user, String token) {}
}

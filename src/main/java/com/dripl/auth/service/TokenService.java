package com.dripl.auth.service;

import com.dripl.auth.utils.JwtUtil;
import com.dripl.user.entity.User;
import com.dripl.user.repository.UserRepository;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.workspace.membership.service.MembershipService;
import com.dripl.workspace.membership.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtUtil jwtUtil;
    private final MembershipService membershipService;
    private final UserRepository userRepository;

    public String mintToken(UUID userId, UUID workspaceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<String> roles = membershipService.findMembership(userId, workspaceId)
                .map(m -> m.getRoles().stream().map(Role::name).collect(Collectors.toList()))
                .orElse(List.of("READ"));

        return jwtUtil.generateToken(userId, workspaceId, user.getEmail(), roles);
    }
}

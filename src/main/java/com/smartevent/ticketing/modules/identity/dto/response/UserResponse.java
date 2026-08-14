package com.smartevent.ticketing.modules.identity.dto.response;

import com.smartevent.ticketing.modules.identity.entity.User;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserResponse (
        UUID id,
        String email,
        String fullName,
        String phone,
        String status,
        Set<String> roles
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getStatus().name(),
                user.getUserRoles()
                        .stream()
                        .map(userRole -> userRole.getRole().getName())
                        .collect(Collectors.toSet())
        );
    }
}

package com.smartevent.modules.identity.dto.response;

import com.smartevent.modules.identity.entity.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        String status,
        Set<String> roles,
        Instant createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getStatus().name(),
                user.getRoleNames(),
                user.getCreatedAt()
        );
    }
}

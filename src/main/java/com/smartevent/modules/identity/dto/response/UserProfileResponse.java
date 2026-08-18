package com.smartevent.modules.identity.dto.response;

import com.smartevent.modules.identity.entity.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        UUID avatarFileId,
        String status,
        Set<String> roles,
        OrganizerProfileResponse organizerProfile,
        Instant createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarFileId(),
                user.getStatus().name(),
                user.getUserRoles().stream()
                        .map(ur -> ur.getRole().getName())
                        .collect(Collectors.toSet()),
                OrganizerProfileResponse.from(user.getOrganizerProfile()),
                user.getCreatedAt()
        );
    }
}

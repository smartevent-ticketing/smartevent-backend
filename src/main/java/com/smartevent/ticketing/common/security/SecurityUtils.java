package com.smartevent.ticketing.common.security;

import com.smartevent.ticketing.infrastructure.security.UserPrincipal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Optional<Authentication> getAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return Optional.of(auth);
        }

        return Optional.empty();
    }

    public static Optional<UUID> getCurrentUserId() {
        Optional<Authentication> authOpt = getAuthentication();

        if (authOpt.isEmpty()) {
            return Optional.empty();
        }

        Authentication auth = authOpt.get();
        Object principal = auth.getPrincipal();

        if (principal instanceof UserPrincipal userPrincipal) {
            return Optional.ofNullable(userPrincipal.getId());
        }

        String userIdStr = auth.getName();
        try {
            return Optional.of(UUID.fromString(userIdStr));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> getCurrentUserEmail() {
        Optional<Authentication> authOpt = getAuthentication();

        if (authOpt.isEmpty()) {
            return Optional.empty();
        }

        Authentication auth = authOpt.get();
        Object principal = auth.getPrincipal();

        if (principal instanceof UserPrincipal userPrincipal) {
            return Optional.ofNullable(userPrincipal.getEmail());
        }

        String email = auth.getName();
        if (email != null && !email.isBlank()) {
            return Optional.of(email);
        }

        return Optional.empty();
    }

    public static boolean hasRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return false;
        }

        Optional<Authentication> authOpt = getAuthentication();

        if (authOpt.isEmpty()) {
            return false;
        }

        String expectedRole = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;

        Authentication auth = authOpt.get();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase(roleName)
                        || a.getAuthority().equalsIgnoreCase(expectedRole));
    }
}

package com.smartevent.modules.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GrantUserRoleRequest(@NotBlank String roleName) {
}

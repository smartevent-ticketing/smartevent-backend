package com.smartevent.modules.identity.service;

import com.smartevent.common.api.PageResponse;
import com.smartevent.modules.identity.dto.response.AdminUserResponse;

import java.util.UUID;

public interface AdminUserService {
    PageResponse<AdminUserResponse> listUsers(String search, int page, int size);

    AdminUserResponse grantRole(UUID targetUserId, String roleName, UUID adminUserId);
}

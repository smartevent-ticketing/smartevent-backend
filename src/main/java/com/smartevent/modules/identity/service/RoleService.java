package com.smartevent.modules.identity.service;

import com.smartevent.modules.identity.dto.response.RoleResponse;

import java.util.List;

public interface RoleService {
    List<RoleResponse> getAllRoles();
}


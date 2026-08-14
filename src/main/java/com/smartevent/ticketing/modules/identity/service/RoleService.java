package com.smartevent.ticketing.modules.identity.service;

import com.smartevent.ticketing.modules.identity.dto.response.RoleResponse;

import java.util.List;

public interface RoleService {
    List<RoleResponse> getAllRoles();
}

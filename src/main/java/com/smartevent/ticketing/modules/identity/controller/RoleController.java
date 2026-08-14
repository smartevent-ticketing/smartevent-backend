package com.smartevent.ticketing.modules.identity.controller;

import com.smartevent.ticketing.common.api.ApiResponse;
import com.smartevent.ticketing.modules.identity.dto.response.RoleResponse;
import com.smartevent.ticketing.modules.identity.service.RoleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public ApiResponse<List<RoleResponse>> getAllRoles() {
        return ApiResponse.success(roleService.getAllRoles());
    }
}

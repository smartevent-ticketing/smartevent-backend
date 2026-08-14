package com.smartevent.ticketing.modules.identity.service.impl;

import com.smartevent.ticketing.modules.identity.dto.response.RoleResponse;
import com.smartevent.ticketing.modules.identity.repository.RoleRepository;
import com.smartevent.ticketing.modules.identity.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    public RoleServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll()
                .stream()
                .map(RoleResponse::from)
                .toList();
    }
}

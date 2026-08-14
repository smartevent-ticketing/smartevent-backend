package com.smartevent.ticketing.modules.identity.service.impl;

import com.smartevent.ticketing.common.error.BusinessException;
import com.smartevent.ticketing.common.error.ErrorCode;
import com.smartevent.ticketing.modules.identity.dto.request.RegisterRequest;
import com.smartevent.ticketing.modules.identity.dto.response.UserResponse;
import com.smartevent.ticketing.modules.identity.entity.Role;
import com.smartevent.ticketing.modules.identity.entity.User;
import com.smartevent.ticketing.modules.identity.repository.RoleRepository;
import com.smartevent.ticketing.modules.identity.repository.UserRepository;
import com.smartevent.ticketing.modules.identity.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_ROLE = "CUSTOMER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }


    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        /*Kiểm tra xem email đã tồn tại chưa*/
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Email already exists"
            );
        }

        Role customerRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Customer role not found"
                ));

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                request.phone()
        );

        user.addRole(customerRole);

        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }
}

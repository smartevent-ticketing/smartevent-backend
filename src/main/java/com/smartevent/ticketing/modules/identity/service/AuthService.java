package com.smartevent.ticketing.modules.identity.service;

import com.smartevent.ticketing.modules.identity.dto.request.RegisterRequest;
import com.smartevent.ticketing.modules.identity.dto.response.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);
}
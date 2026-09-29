package com.smartevent.modules.identity.service.impl;

import com.smartevent.common.api.PageResponse;
import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.identity.dto.response.AdminUserResponse;
import com.smartevent.modules.identity.entity.Role;
import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.identity.repository.RoleRepository;
import com.smartevent.modules.identity.repository.UserRepository;
import com.smartevent.modules.identity.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {
    private static final Set<String> GRANTABLE_ROLES = Set.of("CUSTOMER", "ORGANIZER", "ADMIN");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(String search, int page, int size) {
        String query = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        if (query != null && query.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Từ khóa tìm kiếm tối đa 100 ký tự");
        }
        PageRequest pageable = PageRequest.of(
                Math.max(0, page), Math.max(1, Math.min(50, size)),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<User> result = query == null
                ? userRepository.findByDeletedAtIsNull(pageable)
                : userRepository.searchAdminUsers("%" + query + "%", pageable);
        if (result.isEmpty()) return PageResponse.from(result, List.of());

        List<UUID> ids = result.getContent().stream().map(User::getId).toList();
        Map<UUID, User> usersWithRoles = userRepository.findAllByIdsWithRoles(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<AdminUserResponse> content = result.getContent().stream()
                .map(user -> AdminUserResponse.from(usersWithRoles.getOrDefault(user.getId(), user)))
                .toList();
        return PageResponse.from(result, content);
    }

    @Override
    @Transactional
    public AdminUserResponse grantRole(UUID targetUserId, String roleName, UUID adminUserId) {
        String normalized = roleName == null ? "" : roleName.trim().toUpperCase(Locale.ROOT);
        if (!GRANTABLE_ROLES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Role không hợp lệ");
        }
        User user = userRepository.findNotDeletedByIdForRoleUpdate(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản"));
        if (user.getRoleNames().contains(normalized)) return AdminUserResponse.from(user);

        Role role = roleRepository.findByName(normalized)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Role chưa được cấu hình"));
        user.addRole(role);
        userRepository.saveAndFlush(user);
        log.info("Admin {} granted role {} to user {}", adminUserId, normalized, targetUserId);
        return AdminUserResponse.from(user);
    }
}
